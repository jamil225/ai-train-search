package com.trainsearch.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A deliberately simple flat-file logger for a small, low-effort app: appends one line (plus an
 * optional stack trace) per event to a single text file on the device, so that if something goes
 * wrong for a family member, the file itself is the entire debugging story — no server, no
 * crash-reporting SDK, nothing to configure.
 *
 * Not a general-purpose logging framework: no log levels beyond info/error, no multiple files,
 * no structured fields. If this app ever needs more than that, reach for a real library
 * (Timber + a crash reporter) instead of growing this one.
 */
object AppLogger {

    private const val FILE_NAME = "train_search.log"
    private const val MAX_BYTES = 512 * 1024L // ~512KB — plenty for a personal app, never unbounded
    private val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    @Volatile private var logFile: File? = null

    /** Call once, e.g. from MainActivity.onCreate, before anything else might log. */
    fun init(context: Context) {
        if (logFile != null) return
        logFile = File(context.applicationContext.filesDir, FILE_NAME)
        installUncaughtExceptionHandler()
    }

    fun info(tag: String, message: String) = write("INFO", tag, message, null)

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        // android.util.Log isn't available under plain JVM unit tests (it throws there rather
        // than being a no-op) — swallow that so logging never affects test or app behavior.
        runCatching { Log.e(tag, message, throwable) } // still visible in `adb logcat` on-device
        write("ERROR", tag, message, throwable)
    }

    /** Absolute path of the log file, so it can be surfaced/shared later if ever needed. */
    fun filePath(): String? = logFile?.absolutePath

    private fun write(level: String, tag: String, message: String, throwable: Throwable?) {
        val file = logFile ?: return // logging before init() is a no-op, never a crash
        runCatching {
            rotateIfTooBig(file)
            file.appendText(
                buildString {
                    append(timestamp.format(Date()))
                    append(" ")
                    append(level)
                    append(" [")
                    append(tag)
                    append("] ")
                    appendLine(message)
                    if (throwable != null) {
                        val sw = StringWriter()
                        throwable.printStackTrace(PrintWriter(sw))
                        appendLine(sw.toString().trimEnd())
                    }
                }
            )
        }
        // A logging failure (disk full, permissions) must never crash or interrupt the app —
        // there is deliberately no further error handling here.
    }

    private fun rotateIfTooBig(file: File) {
        if (file.length() > MAX_BYTES) {
            // Simplest possible rotation for a low-effort app: drop the old file and start fresh
            // rather than maintaining .1/.2 rotation files.
            file.delete()
        }
    }

    private fun installUncaughtExceptionHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            error("Uncaught", "App crashed on thread ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }
}

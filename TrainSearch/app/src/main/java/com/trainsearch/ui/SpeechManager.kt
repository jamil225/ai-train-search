package com.trainsearch.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SpeechLanguage(val code: String, val displayName: String, val shortTag: String) {
    AUTO("en-IN", "Auto (en-IN)", "AUTO"),
    HINDI("hi-IN", "Hindi (हिन्दी)", "HINDI"),
    ENGLISH("en-US", "English (en-US)", "ENG")
}

data class SpeechState(
    val isListening: Boolean = false,
    val partialText: String = "",
    val rmsDb: Float = 0f,
    val error: String? = null,
    val language: SpeechLanguage = SpeechLanguage.AUTO
)

class SpeechManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val _state = MutableStateFlow(SpeechState())
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    private var onResultCallback: ((String) -> Unit)? = null
    private var isUserStopped = false
    private var silenceRetryCount = 0
    private val maxSilenceRetries = 3

    fun setLanguage(language: SpeechLanguage) {
        _state.value = _state.value.copy(language = language)
    }

    fun startListening(onResult: (String) -> Unit) {
        onResultCallback = onResult
        isUserStopped = false
        silenceRetryCount = 0
        _state.value = _state.value.copy(isListening = true, partialText = "", error = null, rmsDb = 0f)

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = _state.value.copy(
                isListening = false,
                error = "Speech recognition is not available on this device."
            )
            return
        }

        launchRecognizer()
    }

    private fun launchRecognizer() {
        destroyRecognizer()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.value = _state.value.copy(error = null)
                }

                override fun onBeginningOfSpeech() {
                    silenceRetryCount = 0
                }

                override fun onRmsChanged(rmsdB: Float) {
                    _state.value = _state.value.copy(rmsDb = rmsdB.coerceAtLeast(0f))
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    if (isUserStopped) return

                    // Auto-retry if timeout/no-match occurs due to initial pause before speaking
                    if ((error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH) && silenceRetryCount < maxSilenceRetries) {
                        silenceRetryCount++
                        launchRecognizer()
                        return
                    }

                    val msg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Audio permission missing"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Try speaking again."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected"
                        else -> "Speech error ($error)"
                    }
                    _state.value = _state.value.copy(isListening = false, error = msg)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull().orEmpty()
                    _state.value = _state.value.copy(isListening = false, partialText = text)
                    if (text.isNotBlank()) {
                        onResultCallback?.invoke(text)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) {
                        _state.value = _state.value.copy(partialText = text)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, _state.value.language.code)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, _state.value.language.code)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 8000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _state.value = _state.value.copy(isListening = false, error = "Failed to start listening: ${e.message}")
        }
    }

    fun stopListening(submitPartialIfAvailable: Boolean = true) {
        isUserStopped = true
        val currentText = _state.value.partialText
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        destroyRecognizer()
        _state.value = _state.value.copy(isListening = false)
        if (submitPartialIfAvailable && currentText.isNotBlank()) {
            onResultCallback?.invoke(currentText)
        }
    }

    fun cancelListening() {
        isUserStopped = true
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
        destroyRecognizer()
        _state.value = _state.value.copy(isListening = false, partialText = "")
    }

    fun destroyRecognizer() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }
}

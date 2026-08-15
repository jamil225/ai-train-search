package com.trainsearch.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// A station departure board: signal yellow on near-black, with status colours
// that mean one thing throughout — green available, amber RAC, red waitlist.
val BoardYellow = Color(0xFFF5C518)
val BoardInk = Color(0xFF14170F)
val BoardSurface = Color(0xFF191D13)
val BoardText = Color(0xFFDDE2D2)
val Dim = Color(0xFF767E64)
val Rule = Color(0xFF21261A)
val AvlGreen = Color(0xFF74D69A)
val RacAmber = Color(0xFFF5C518)
val WlRed = Color(0xFFE0705C)

private val scheme = darkColorScheme(
    primary = BoardYellow,
    onPrimary = BoardInk,
    background = BoardInk,
    onBackground = BoardText,
    surface = BoardSurface,
    onSurface = BoardText,
    error = WlRed
)

/** Committed to a single dark board look — it is a departure board, not a document. */
@Composable
fun TrainSearchTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = scheme, content = content)

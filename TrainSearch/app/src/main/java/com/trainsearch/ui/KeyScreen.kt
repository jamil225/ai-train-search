package com.trainsearch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun KeyScreen(onSaved: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text("TRAIN SEARCH", color = BoardYellow, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text(
            "Paste the API key you were given. It stays on this phone, " +
                "and searches are billed to whoever issued it.",
            color = Dim, fontSize = 14.sp
        )
        OutlinedTextField(
            value = key,
            onValueChange = { key = it; error = null },
            label = { Text("API key") },
            singleLine = true,
            isError = error != null,
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace),
            visualTransformation =
                if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(onClick = { reveal = !reveal }) {
            Text(if (reveal) "Hide key" else "Show key", color = Dim)
        }
        error?.let { Text(it, color = WlRed, fontSize = 13.sp) }
        Button(
            onClick = {
                val trimmed = key.trim()
                if (!trimmed.startsWith("sk-") || trimmed.length < 20) {
                    error = "That doesn't look like an API key. It starts with sk-."
                } else onSaved(trimmed)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Continue") }
    }
}

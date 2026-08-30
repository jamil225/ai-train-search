package com.trainsearch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shown when the agent needs more information instead of failing outright. Deliberately styled
 * distinct from [ErrorBanner] (yellow, not red) so users learn "agent is asking something" vs
 * "something failed".
 */
@Composable
fun ClarificationBubble(question: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = BoardYellow.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, BoardYellow.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp, 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Agent is asking a question",
                tint = BoardYellow,
                modifier = Modifier.size(16.dp)
            )
            Text(question, color = BoardText, fontSize = 13.sp)
        }
    }
}

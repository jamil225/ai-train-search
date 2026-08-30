package com.trainsearch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trainsearch.data.MessageEntity
import com.trainsearch.data.MessageRole
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")

/** Small floating trigger for the read-only conversation history glance. */
@Composable
fun HistoryBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = BoardYellow,
        contentColor = BoardInk,
        modifier = modifier.size(40.dp)
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = "Conversation history",
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Read-only glance at the conversation: the current pattern summary (if any) plus the raw
 * messages still stored. No edit/delete affordances — this is a glance, not a chat UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
    summary: String?,
    messages: List<MessageEntity>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BoardSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "CONVERSATION HISTORY",
                color = BoardYellow,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            if (summary != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BoardInk.copy(alpha = 0.4f)
                ) {
                    Text(
                        text = summary,
                        color = BoardText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            if (messages.isEmpty() && summary == null) {
                Text("No history yet.", color = Dim, fontSize = 13.sp)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(messages) { message -> HistoryRow(message) }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(message: MessageEntity) {
    val label = if (message.role == MessageRole.USER) "YOU" else "AGENT"
    val tint = if (message.role == MessageRole.USER) BoardYellow else Dim
    val timestamp = timeFormatter.format(
        Instant.ofEpochMilli(message.createdAtEpochMs).atZone(ZoneId.systemDefault())
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$label · $timestamp", color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(message.content, color = BoardText, fontSize = 13.sp)
    }
}

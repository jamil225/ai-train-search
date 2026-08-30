package com.trainsearch.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trainsearch.data.MessageEntity
import com.trainsearch.data.ResultRow
import com.trainsearch.data.StatusKind

@Composable
fun BoardScreen(vm: BoardViewModel) {
    val state by vm.state.collectAsState()
    var input by remember { mutableStateOf("") }

    var showHistorySheet by remember { mutableStateOf(false) }
    var historySummary by remember { mutableStateOf<String?>(null) }
    var historyMessages by remember { mutableStateOf<List<MessageEntity>>(emptyList()) }
    LaunchedEffect(showHistorySheet) {
        if (showHistorySheet) {
            val snapshot = vm.loadHistoryForDisplay()
            historySummary = snapshot.summary
            historyMessages = snapshot.recentMessages
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                input = spokenText
                vm.submit(spokenText)
                input = ""
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(BoardInk)) {

        // Straight Station Board Header (Flush rectangle)
        Column(
            Modifier
                .fillMaxWidth()
                .background(BoardYellow)
                .statusBarsPadding()
                .padding(14.dp, 10.dp)
        ) {
            Text(
                state.heading.ifBlank { "AI TRAIN SEARCH" },
                color = BoardInk, fontWeight = FontWeight.Bold, fontSize = 18.sp
            )
            Text(
                state.subheading.ifBlank { "AI-powered train search" },
                color = BoardInk.copy(alpha = 0.75f), fontSize = 12.sp
            )

            // Filter & Sort Bar (Floating Filter Chips on Yellow Header)
            if (state.rows.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sort Toggles
                    FilterChipItem(
                        selected = state.selectedSortOrder == SortOrder.RANK,
                        onClick = { vm.setSortOrder(SortOrder.RANK) },
                        label = "\u26a1 Rank"
                    )
                    FilterChipItem(
                        selected = state.selectedSortOrder == SortOrder.DATE,
                        onClick = { vm.setSortOrder(SortOrder.DATE) },
                        label = "\ud83d\udcc5 Date"
                    )

                    Text("\u2502", color = BoardInk.copy(alpha = 0.4f), fontSize = 12.sp)

                    // Class Filters
                    FilterChipItem(
                        selected = state.selectedClassFilter == null,
                        onClick = { vm.setClassFilter(null) },
                        label = "All Classes"
                    )
                    state.availableClasses.forEach { cls ->
                        FilterChipItem(
                            selected = state.selectedClassFilter.equals(cls, ignoreCase = true),
                            onClick = { vm.setClassFilter(cls) },
                            label = cls
                        )
                    }

                    Text("\u2502", color = BoardInk.copy(alpha = 0.4f), fontSize = 12.sp)

                    // Availability Filters
                    FilterChipItem(
                        selected = state.selectedKindFilter == null,
                        onClick = { vm.setKindFilter(null) },
                        label = "All Status"
                    )
                    FilterChipItem(
                        selected = state.selectedKindFilter == StatusKind.AVL,
                        onClick = { vm.setKindFilter(StatusKind.AVL) },
                        label = "AVL Only"
                    )
                    FilterChipItem(
                        selected = state.selectedKindFilter == StatusKind.RAC,
                        onClick = { vm.setKindFilter(StatusKind.RAC) },
                        label = "RAC"
                    )
                    FilterChipItem(
                        selected = state.selectedKindFilter == StatusKind.WL,
                        onClick = { vm.setKindFilter(StatusKind.WL) },
                        label = "WL"
                    )

                    Text("\u2502", color = BoardInk.copy(alpha = 0.4f), fontSize = 12.sp)

                    // Date Filters
                    FilterChipItem(
                        selected = state.selectedDateFilter == null,
                        onClick = { vm.setDateFilter(null) },
                        label = "All Dates"
                    )
                    state.availableDates.forEach { date ->
                        FilterChipItem(
                            selected = state.selectedDateFilter == date,
                            onClick = { vm.setDateFilter(date) },
                            label = date
                        )
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.busy -> ProgressBody(state)
                state.error != null -> ErrorBanner(state.error!!, modifier = Modifier.align(Alignment.Center))
                state.visibleRows.isEmpty() -> Column(
                    Modifier.align(Alignment.Center).padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.rows.isNotEmpty()) {
                        Text("No options match the selected filters.", color = BoardText, fontSize = 14.sp)
                    } else {
                        Text("AI-powered train search", color = BoardYellow, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Ask for a trip in your own words.", color = BoardText, fontSize = 14.sp)
                        Text("\"Rajasthan to Pune on 1 September, sleeper\"", color = Dim, fontSize = 12.sp)
                        Text("\"Jaipur to Pune tomorrow\"", color = Dim, fontSize = 12.sp)
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.visibleRows) { row ->
                        BoardCard(row, top = row === state.visibleRows.first())
                    }
                }
            }
        }

        // Bottom Input Bar
        Column(
            Modifier
                .fillMaxWidth()
                .background(BoardInk)
                .navigationBarsPadding()
                .padding(14.dp, 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.clarificationQuestion?.let { question ->
                ClarificationBubble(question)
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Type a trip\u2026", color = Dim, fontSize = 14.sp) },
                    singleLine = true,
                    enabled = !state.busy,
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = BoardSurface,
                        unfocusedContainerColor = BoardSurface,
                        disabledContainerColor = BoardSurface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    trailingIcon = {
                        IconButton(onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your trip details in Hindi or English...")
                                // +20% over the defaults so a natural mid-sentence pause doesn't cut the recognizer off early.
                                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 7200L)
                                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4800L)
                                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3600L)
                            }
                            try {
                                speechLauncher.launch(intent)
                            } catch (_: Exception) {}
                        }) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice search",
                                tint = BoardYellow,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        vm.submit(input)
                        input = ""
                    },
                    enabled = !state.busy && input.isNotBlank(),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BoardYellow,
                        contentColor = BoardInk,
                        disabledContainerColor = BoardYellow.copy(alpha = 0.4f),
                        disabledContentColor = BoardInk.copy(alpha = 0.4f)
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Search",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Go", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }

        HistoryBadge(
            onClick = { showHistorySheet = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(14.dp, 10.dp)
        )
    }

    if (showHistorySheet) {
        HistorySheet(
            summary = historySummary,
            messages = historyMessages,
            onDismiss = { showHistorySheet = false }
        )
    }
}

@Composable
private fun FilterChipItem(selected: Boolean, onClick: () -> Unit, label: String) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) BoardInk else BoardInk.copy(alpha = 0.12f),
        contentColor = if (selected) BoardYellow else BoardInk
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(24.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF2C1C19),
        border = BorderStroke(1.dp, WlRed.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Error",
                tint = WlRed,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = message,
                color = Color(0xFFFFB4AB),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun ProgressBody(state: BoardState) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
    ) {
        Text("SEARCHING", color = BoardYellow, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(state.progressLabel, color = BoardText, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        if (state.total > 0) {
            LinearProgressIndicator(
                progress = { state.done.toFloat() / state.total },
                color = BoardYellow, trackColor = Rule,
                modifier = Modifier.fillMaxWidth()
            )
            Text("${state.done} of ${state.total} routes", color = Dim, fontSize = 11.sp)
        } else {
            LinearProgressIndicator(color = BoardYellow, trackColor = Rule, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Card Layout — Source ➔ Destination as Primary Line */
@Composable
private fun BoardCard(row: ResultRow, top: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BoardSurface),
        border = if (top) BorderStroke(1.dp, BoardYellow) else null
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Line 1: Date Pill Badge on left, Price on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BoardInk.copy(alpha = 0.35f),
                    contentColor = BoardYellow
                ) {
                    Text(
                        text = row.date,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                row.fare?.let {
                    Text("\u20b9$it", color = BoardText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            // Line 2 (Primary): Station Route (fromStnCode ➔ toStnCode) + Train Number
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${row.fromStnCode}  \u279c  ${row.toStnCode}",
                    color = BoardYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "(${row.trainNumber})",
                    color = Dim,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Line 3: Train Name
            Text(
                text = row.trainName.uppercase(),
                color = BoardText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Line 4: Departure -> Arrival · Class on left, Status Pill Badge on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${row.departureTime}  \u2192  ${row.arrivalTime}  \u00b7  ${row.travelClass}",
                    color = Dim,
                    fontSize = 12.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPillBadge(row)
                    val chance = row.confirmChance
                    if (row.kind == StatusKind.WL && chance != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "($chance%)",
                            color = confirmChanceColor(chance),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPillBadge(row: ResultRow) {
    val (bgColor, textColor) = when (row.kind) {
        StatusKind.AVL -> AvlGreen to BoardInk
        StatusKind.RAC -> RacAmber to BoardInk
        StatusKind.WL -> WlRed to Color.White
        StatusKind.OTHER -> Dim to Color.White
    }
    Surface(
        shape = CircleShape,
        color = bgColor,
        contentColor = textColor
    ) {
        Text(
            text = row.status,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

/**
 * Colors ConfirmTkt's confirmation-chance percentage independently of the WL pill's own
 * (always red) background: >=70 confident green, 40-69 middling amber, <40 unlikely red.
 */
fun confirmChanceColor(pct: Int): Color = when {
    pct >= 70 -> AvlGreen
    pct >= 40 -> RacAmber
    else -> WlRed
}

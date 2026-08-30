package com.trainsearch.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainsearch.agent.Llm
import com.trainsearch.agent.Search
import com.trainsearch.agent.SearchEvent
import com.trainsearch.data.ConfirmTkt
import com.trainsearch.data.ConversationRepository
import com.trainsearch.data.MessageRole
import com.trainsearch.data.ResultRow
import com.trainsearch.data.StatusKind
import com.trainsearch.data.formatDateDisplay
import com.trainsearch.data.formatDateRange
import com.trainsearch.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

enum class SortOrder { RANK, DATE }

data class BoardState(
    val busy: Boolean = false,
    val progressLabel: String = "",
    val done: Int = 0,
    val total: Int = 0,
    val rows: List<ResultRow> = emptyList(),
    val heading: String = "",
    val subheading: String = "",
    val error: String? = null,
    val selectedKindFilter: StatusKind? = null,
    val selectedDateFilter: String? = null,
    val selectedClassFilter: String? = null,
    val selectedSortOrder: SortOrder = SortOrder.RANK,
    val history: List<String> = emptyList(),
    /** Set when the agent needs more information; shown as a conversational prompt, not an error. */
    val clarificationQuestion: String? = null
) {
    val visibleRows: List<ResultRow>
        get() {
            val filtered = rows.filter { row ->
                (selectedKindFilter == null || row.kind == selectedKindFilter) &&
                (selectedDateFilter == null || row.date == selectedDateFilter) &&
                (selectedClassFilter == null || row.travelClass.equals(selectedClassFilter, ignoreCase = true))
            }
            return when (selectedSortOrder) {
                SortOrder.RANK -> filtered
                SortOrder.DATE -> filtered.sortedBy { it.date }
            }
        }

    val availableDates: List<String>
        get() = rows.map { it.date }.distinct()

    val availableClasses: List<String>
        get() = rows.map { it.travelClass }.distinct()
}

class BoardViewModel(
    apiKey: String,
    private val conversations: ConversationRepository
) : ViewModel() {

    private val search = Search(ConfirmTkt(), Llm(apiKey), conversations)
    private val _state = MutableStateFlow(BoardState())
    val state: StateFlow<BoardState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val (ctx, pendingQuestion) = conversations.bootstrap()
                val seededHistory = ctx.recentMessages
                    .filter { it.role == MessageRole.USER }
                    .map { it.content }
                    .asReversed() // newest first, matching the old prepend-based ordering
                    .distinct()
                    .take(10)
                _state.value = _state.value.copy(
                    history = seededHistory,
                    clarificationQuestion = pendingQuestion
                )
            } catch (e: Exception) {
                // If persisted history can't be read (e.g. a corrupt database), fail soft into a
                // blank slate rather than leaving the app stuck or crashing on every launch.
                AppLogger.error("BoardViewModel", "Failed to load persisted conversation state on startup", e)
            }
        }
    }

    /** Read-only snapshot for the history popup: current summary + every raw message still stored. */
    suspend fun loadHistoryForDisplay() = conversations.historySnapshot()

    fun setKindFilter(kind: StatusKind?) {
        _state.value = _state.value.copy(selectedKindFilter = kind)
    }

    fun setDateFilter(date: String?) {
        _state.value = _state.value.copy(selectedDateFilter = date)
    }

    fun setClassFilter(cls: String?) {
        _state.value = _state.value.copy(selectedClassFilter = cls)
    }

    fun setSortOrder(sortOrder: SortOrder) {
        _state.value = _state.value.copy(selectedSortOrder = sortOrder)
    }

    fun submit(sentence: String) {
        val trimmed = sentence.trim()
        if (trimmed.isBlank() || _state.value.busy) return

        // The user's reply might be answering a pending clarification rather than a brand new
        // search \u2014 either way `Search.run` sends full context, so no special-casing is needed here.
        val updatedHistory = (listOf(trimmed) + _state.value.history).distinct().take(10)
        _state.value = BoardState(
            busy = true,
            progressLabel = "Reading your trip",
            history = updatedHistory,
            clarificationQuestion = null
        )

        viewModelScope.launch {
            try {
                search.run(trimmed, LocalDate.now(), ZoneId.systemDefault().id).collect { event ->
                    _state.value = when (event) {
                        is SearchEvent.Progress -> _state.value.copy(
                            progressLabel = event.label, done = event.done, total = event.total
                        )
                        is SearchEvent.Results -> {
                            val datesRange = formatDateRange(event.dates)
                            BoardState(
                                busy = false,
                                rows = event.rows,
                                heading = "${event.origin.uppercase()} \u2192 ${event.destination.uppercase()}",
                                subheading = "$datesRange \u00b7 ${event.rows.size} options",
                                history = updatedHistory
                            )
                        }
                        is SearchEvent.Failed -> BoardState(busy = false, error = event.message, history = updatedHistory)
                        is SearchEvent.Clarify -> BoardState(
                            busy = false,
                            history = updatedHistory,
                            clarificationQuestion = event.question
                        )
                    }
                }
            } catch (e: Exception) {
                // Safety net for anything unexpected below Search's own error handling \u2014 e.g. a
                // database failure \u2014 so the app shows an error instead of silently hanging or crashing.
                AppLogger.error("BoardViewModel", "Unhandled failure while running search for: \"$trimmed\"", e)
                _state.value = BoardState(
                    busy = false,
                    error = "Something went wrong. Please try again.",
                    history = updatedHistory
                )
            }
        }
    }
}

package com.trainsearch.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainsearch.agent.Llm
import com.trainsearch.agent.Search
import com.trainsearch.agent.SearchEvent
import com.trainsearch.data.ConfirmTkt
import com.trainsearch.data.ResultRow
import com.trainsearch.data.StatusKind
import com.trainsearch.data.formatDateDisplay
import com.trainsearch.data.formatDateRange
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
    val history: List<String> = listOf(
        "Rajasthan to Pune today till 4 Sep, sleeper",
        "Ajmer, Jaipur, Kishangarh, Jodhpur to Pune",
        "Jaipur to Pune tomorrow",
        "Jodhpur to Pune 1 September 3A"
    )
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

class BoardViewModel(apiKey: String) : ViewModel() {

    private val search = Search(ConfirmTkt(), Llm(apiKey))
    private val _state = MutableStateFlow(BoardState())
    val state: StateFlow<BoardState> = _state.asStateFlow()

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

        val updatedHistory = (listOf(trimmed) + _state.value.history).distinct().take(10)
        _state.value = BoardState(busy = true, progressLabel = "Reading your trip", history = updatedHistory)

        viewModelScope.launch {
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
                }
            }
        }
    }
}

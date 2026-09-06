package com.trainsearch.agent

import com.trainsearch.data.ConfirmTkt
import com.trainsearch.data.ConversationRepository
import com.trainsearch.data.ConvTurn
import com.trainsearch.data.ParseOutcome
import com.trainsearch.data.ResultRow
import com.trainsearch.data.Stations
import com.trainsearch.data.Train
import com.trainsearch.data.TripState
import com.trainsearch.data.mergeTripState
import com.trainsearch.data.normalizeDate
import com.trainsearch.util.AppLogger
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

const val RESULT_LIMIT = 10
private const val MAX_CONCURRENT = 5

sealed interface SearchEvent {
    data class Progress(val label: String, val done: Int, val total: Int) : SearchEvent
    data class Results(
        val rows: List<ResultRow>,
        val origin: String,
        val destination: String,
        val dates: List<String>
    ) : SearchEvent
    data class Failed(val message: String) : SearchEvent
    /** The agent needs more information before it can search; [question] is shown conversationally, not as an error. */
    data class Clarify(val question: String) : SearchEvent
}

/** Cartesian product of origin and destination codes, origin-major. */
fun pairsFor(origins: List<String>, destinations: List<String>): List<Pair<String, String>> =
    origins.flatMap { o -> destinations.map { d -> o to d } }

/**
 * Flatten each train into one row per class, drop classes the user didn't ask for,
 * remove duplicates, rank by availability, and truncate.
 */
fun assemble(
    collected: List<Triple<Train, String, Pair<Int, Int>>>,
    classes: List<String>,
    limit: Int
): List<ResultRow> {
    val rows = collected.flatMap { (train, date, idx) ->
        flatten(train, date, originGroupIndex = idx.first, destGroupIndex = idx.second)
    }
    val filtered = if (classes.isEmpty()) rows else rows.filter { it.travelClass in classes }
    return rank(dedup(filtered)).take(limit)
}

class Search(
    private val api: ConfirmTkt,
    private val llm: Llm,
    private val conversations: ConversationRepository
) {

    /**
     * channelFlow, not flow: progress is sent from inside each concurrent search
     * as it finishes, so the bar advances during the work rather than all at once
     * after it. A plain flow cannot emit from another coroutine.
     */
    fun run(sentence: String, today: LocalDate, zone: String): Flow<SearchEvent> = channelFlow {
        // Context is read before appending this sentence, so the sentence doesn't
        // also show up duplicated inside `history`.
        val ctxBefore = conversations.currentContext()
        conversations.appendUserMessage(sentence)
        val history = ctxBefore.recentMessages.map { ConvTurn(it.role, it.content) }

        // Reduce the trip state against the user's message.
        val merged = try {
            send(SearchEvent.Progress("Reading your trip", 0, 1))
            val delta = llm.reduceTrip(sentence, today, zone, ctxBefore.tripState ?: TripState(), history)
            val merged = mergeTripState(ctxBefore.tripState ?: TripState(), delta)
            conversations.updateTripState(merged)

            // The guard: if the model re-asked with complete state, override it and proceed to search.
            if (delta.needsClarification && merged.isComplete()) {
                AppLogger.warn("Search", "Model re-asked with complete state; overriding and searching")
                merged
            } else if (delta.needsClarification) {
                // Clarification needed and state incomplete — ask the user.
                conversations.appendAssistantMessage(
                    delta.question ?: "Could you give me a bit more detail about your trip?",
                    clarificationQuestion = delta.question
                )
                send(SearchEvent.Clarify(delta.question ?: "Could you give me a bit more detail about your trip?"))
                return@channelFlow
            } else {
                merged
            }
        } catch (e: Exception) {
            AppLogger.error("Search", "reduceTrip failed for sentence: \"$sentence\"", e)
            send(SearchEvent.Failed(e.message ?: "Couldn't read that trip.")); return@channelFlow
        }

        // Convert merged state to TripQuery for the search.
        val trip = try {
            merged.toTripQuery()
        } catch (e: Exception) {
            AppLogger.error("Search", "Merged state could not be converted to trip: origin=${merged.origin}, dest=${merged.destination}", e)
            send(SearchEvent.Failed(e.message ?: "Invalid trip state."))
            return@channelFlow
        }

        val origins = try {
            Stations.resolve(trip.origin, api)
        } catch (e: Exception) {
            AppLogger.error("Search", "Station lookup failed for origin \"${trip.origin}\"", e)
            send(SearchEvent.Failed("Couldn't look up \"${trip.origin}\"."));  return@channelFlow
        }
        val destinations = try {
            Stations.resolve(trip.destination, api)
        } catch (e: Exception) {
            AppLogger.error("Search", "Station lookup failed for destination \"${trip.destination}\"", e)
            send(SearchEvent.Failed("Couldn't look up \"${trip.destination}\"."));  return@channelFlow
        }

        if (origins.isEmpty() || destinations.isEmpty()) {
            send(SearchEvent.Failed("No station found for that route. Try a station name or code."))
            return@channelFlow
        }

        val pairs = pairsFor(origins, destinations)
        val jobs = pairs.flatMap { p -> trip.dates.map { d -> p to normalizeDate(d) } }
        val total = jobs.size

        val gate = Semaphore(MAX_CONCURRENT)
        val progress = AtomicInteger(0)
        val collected = Collections.synchronizedList(
            mutableListOf<Triple<Train, String, Pair<Int, Int>>>()
        )
        val lastError = AtomicReference<String?>(null)

        send(SearchEvent.Progress("Searching ${origins.size} \u00d7 ${destinations.size} routes", 0, total))

        coroutineScope {
            jobs.map { (pair, date) ->
                async {
                    val (src, dst) = pair
                    val outcome = gate.withPermit {
                        runCatching { api.searchTrains(src, dst, date) }
                    }
                    val done = progress.incrementAndGet()
                    outcome.onSuccess { trains ->
                        val originIdx = origins.indexOf(src)
                        val destIdx = destinations.indexOf(dst)
                        trains.forEach { collected += Triple(it, date, originIdx to destIdx) }
                        send(SearchEvent.Progress("$src \u2192 $dst \u00b7 ${trains.size} trains", done, total))
                    }.onFailure { e ->
                        lastError.set(e.message)
                        send(SearchEvent.Progress("$src \u2192 $dst \u00b7 unavailable", done, total))
                    }
                }
            }.forEach { it.await() }
        }

        val gathered = collected.toList()
        if (gathered.isEmpty()) {
            send(
                SearchEvent.Failed(
                    lastError.get() ?: "No trains found on that route for those dates."
                )
            )
            return@channelFlow
        }

        val rows = assemble(gathered, trip.classes, RESULT_LIMIT)
        if (rows.isEmpty()) {
            send(SearchEvent.Failed("No ${trip.classes.joinToString("/")} availability on that route."))
            return@channelFlow
        }

        send(SearchEvent.Progress("Ranking", total, total))

        // Build a factual assistant message: the trip that was searched and result count.
        val classText = if (trip.classes.isEmpty()) "any class" else trip.classes.joinToString("/")
        val bestTrain = rows.firstOrNull()?.let { "${it.trainNumber} ${it.trainName}" } ?: "unknown"
        val assistantReply = "Searched ${trip.origin} → ${trip.destination} on ${trip.dates.first()}, $classText — ${rows.size} options, best $bestTrain"
        conversations.appendAssistantMessage(assistantReply, clarificationQuestion = null)

        send(
            SearchEvent.Results(
                rows = rows,
                origin = trip.origin,
                destination = trip.destination,
                dates = trip.dates
            )
        )
    }
}

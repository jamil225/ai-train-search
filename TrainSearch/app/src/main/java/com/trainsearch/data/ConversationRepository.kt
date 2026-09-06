package com.trainsearch.data

import com.trainsearch.util.AppLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Raw message count that triggers a trimming pass (no LLM call). */
const val COMPACTION_TRIGGER_COUNT = 50

/** Raw messages kept (most recent) after a trim. */
const val COMPACTION_KEEP_COUNT = 10

/** Max raw messages ever sent to the model as context (alongside tripState). */
const val CONTEXT_MESSAGE_LIMIT = 20

/** Idle days after which the whole conversation is cleared. */
const val EXPIRY_DAYS = 30L

private const val DAY_MS = 24L * 60 * 60 * 1000

/** Context handed to the LLM for the next call: the explicit tripState (if any) plus recent raw turns, oldest-first. */
data class ConversationContext(
    val tripState: TripState?,
    val recentMessages: List<MessageEntity>
)

/**
 * Single owner of all conversation state. `Search`/`BoardViewModel` never touch [ConversationDao]
 * directly — they append messages, ask for context, and manage state through this class.
 *
 * TripState is the authoritative slot carrier — implicit extraction from prose is gone.
 */
class ConversationRepository(
    private val dao: ConversationDao,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    // Serializes append/trim/expire so overlapping calls (e.g. a rapid double-submit)
    // can't interleave and corrupt the trim/state sequence.
    private val mutex = Mutex()

    /**
     * Call once at startup. Runs the 30-day expiry check, then returns the context to resume
     * with (tripState + last [CONTEXT_MESSAGE_LIMIT] messages) plus any outstanding clarification
     * question the user hadn't answered yet (e.g. the app was killed mid-clarification).
     */
    suspend fun bootstrap(): Pair<ConversationContext, String?> = mutex.withLock {
        expireIfStaleLocked()
        val state = dao.getState()
        buildContextLocked() to state?.pendingClarificationQuestion
    }

    suspend fun appendUserMessage(text: String) = mutex.withLock {
        expireIfStaleLocked() // covers "before starting a new search", not just app start
        dao.insertMessage(MessageEntity(role = MessageRole.USER, content = text, createdAtEpochMs = nowMs()))
        touchLastActiveLocked()
        trimIfNeededLocked()
    }

    suspend fun appendAssistantMessage(text: String, clarificationQuestion: String?) = mutex.withLock {
        dao.insertMessage(MessageEntity(role = MessageRole.ASSISTANT, content = text, createdAtEpochMs = nowMs()))
        val state = dao.getState() ?: ConversationStateEntity(lastActiveEpochMs = nowMs())
        dao.upsertState(state.copy(lastActiveEpochMs = nowMs(), pendingClarificationQuestion = clarificationQuestion))
        trimIfNeededLocked()
    }

    suspend fun clearPendingClarification() = mutex.withLock {
        val state = dao.getState() ?: return@withLock
        dao.upsertState(state.copy(pendingClarificationQuestion = null))
    }

    /** Persist the trip state. */
    suspend fun updateTripState(state: TripState) = mutex.withLock {
        val entity = dao.getState() ?: ConversationStateEntity(lastActiveEpochMs = nowMs())
        dao.upsertState(entity.copy(tripState = state, lastActiveEpochMs = nowMs()))
    }

    /** Clear the trip state (used on "new trip" action). */
    suspend fun resetTripState() = mutex.withLock {
        val state = dao.getState() ?: return@withLock
        dao.upsertState(state.copy(tripState = null))
    }

    /** Context to hand the LLM for the next call: current tripState + last [CONTEXT_MESSAGE_LIMIT] raw messages. */
    suspend fun currentContext(): ConversationContext = mutex.withLock { buildContextLocked() }

    /** Read-only snapshot for the history popup: current tripState + every raw message still stored. */
    suspend fun historySnapshot(): ConversationContext = mutex.withLock {
        ConversationContext(dao.getState()?.let { state ->
            // Parse the JSON tripState back to object for display
            state.tripState
        }, dao.allMessages())
    }

    /** Debug-only hook (never called from production code paths) to test the 30-day expiry without waiting. */
    suspend fun debugBackdateLastActive(daysAgo: Long) = mutex.withLock {
        val state = dao.getState() ?: ConversationStateEntity(lastActiveEpochMs = nowMs())
        dao.upsertState(state.copy(lastActiveEpochMs = nowMs() - daysAgo * DAY_MS))
    }

    private suspend fun buildContextLocked(): ConversationContext {
        val all = dao.allMessages()
        val recent = all.takeLast(CONTEXT_MESSAGE_LIMIT)
        val state = dao.getState()
        return ConversationContext(state?.tripState, recent)
    }

    private suspend fun touchLastActiveLocked() {
        val state = dao.getState() ?: ConversationStateEntity(lastActiveEpochMs = nowMs())
        dao.upsertState(state.copy(lastActiveEpochMs = nowMs()))
    }

    private suspend fun trimIfNeededLocked() {
        val count = dao.messageCount()
        if (count < COMPACTION_TRIGGER_COUNT) return

        // Simple trim: drop oldest, keep newest COMPACTION_KEEP_COUNT.
        // No LLM call, no summarization — tripState carries the slots now.
        dao.trimToNewest(COMPACTION_KEEP_COUNT)
    }

    private suspend fun expireIfStaleLocked() {
        val state = dao.getState() ?: return
        val idleMs = nowMs() - state.lastActiveEpochMs
        if (idleMs <= EXPIRY_DAYS * DAY_MS) return

        // Conversation has been idle > 30 days. Clear it entirely.
        dao.clearMessages()
        dao.upsertState(state.copy(tripState = null, lastActiveEpochMs = nowMs(), pendingClarificationQuestion = null))
    }
}

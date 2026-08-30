package com.trainsearch.data

import com.trainsearch.util.AppLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Raw message count that triggers a compaction pass. */
const val COMPACTION_TRIGGER_COUNT = 40

/** Raw messages kept (most recent) after a compaction pass. */
const val COMPACTION_KEEP_COUNT = 15

/** Max raw messages ever sent to the model as context, alongside the summary. */
const val CONTEXT_MESSAGE_LIMIT = 15

/** Idle days after which the whole conversation is summarized and cleared. */
const val EXPIRY_DAYS = 30L

private const val DAY_MS = 24L * 60 * 60 * 1000

/** Context handed to the LLM for the next call: the rolling summary (if any) plus recent raw turns, oldest-first. */
data class ConversationContext(
    val summary: String?,
    val recentMessages: List<MessageEntity>
)

/**
 * Single owner of all conversation state. `Search`/`BoardViewModel` never touch [ConversationDao]
 * directly — they append messages, ask for context, and let this class handle compaction and expiry.
 *
 * [summarizer] is injected as a plain function (existing summary, older messages) -> new summary,
 * rather than a direct `Llm`/`Summarizer` reference, so this class stays unit-testable with a fake
 * summarizer and a fake DAO, without needing Room or network in tests.
 */
class ConversationRepository(
    private val dao: ConversationDao,
    private val summarizer: suspend (existingSummary: String?, olderMessages: List<MessageEntity>) -> String,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    // Serializes append/compact/expire so overlapping calls (e.g. a rapid double-submit)
    // can't interleave and corrupt the trim/summarize sequence.
    private val mutex = Mutex()

    /**
     * Call once at startup. Runs the 30-day expiry check, then returns the context to resume
     * with (summary + last [CONTEXT_MESSAGE_LIMIT] messages) plus any outstanding clarification
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
        compactIfNeededLocked()
    }

    suspend fun appendAssistantMessage(text: String, clarificationQuestion: String?) = mutex.withLock {
        dao.insertMessage(MessageEntity(role = MessageRole.ASSISTANT, content = text, createdAtEpochMs = nowMs()))
        val state = dao.getState() ?: ConversationStateEntity(lastActiveEpochMs = nowMs())
        dao.upsertState(state.copy(lastActiveEpochMs = nowMs(), pendingClarificationQuestion = clarificationQuestion))
        compactIfNeededLocked()
    }

    suspend fun clearPendingClarification() = mutex.withLock {
        val state = dao.getState() ?: return@withLock
        dao.upsertState(state.copy(pendingClarificationQuestion = null))
    }

    /** Context to hand the LLM for the next call: current summary + last [CONTEXT_MESSAGE_LIMIT] raw messages. */
    suspend fun currentContext(): ConversationContext = mutex.withLock { buildContextLocked() }

    /** Read-only snapshot for the history popup: current summary + every raw message still stored. */
    suspend fun historySnapshot(): ConversationContext = mutex.withLock {
        ConversationContext(dao.getState()?.summary, dao.allMessages())
    }

    /** Debug-only hook (never called from production code paths) to test the 30-day expiry without waiting. */
    suspend fun debugBackdateLastActive(daysAgo: Long) = mutex.withLock {
        val state = dao.getState() ?: ConversationStateEntity(lastActiveEpochMs = nowMs())
        dao.upsertState(state.copy(lastActiveEpochMs = nowMs() - daysAgo * DAY_MS))
    }

    private suspend fun buildContextLocked(): ConversationContext {
        val all = dao.allMessages()
        val recent = all.takeLast(CONTEXT_MESSAGE_LIMIT)
        return ConversationContext(dao.getState()?.summary, recent)
    }

    private suspend fun touchLastActiveLocked() {
        val state = dao.getState() ?: ConversationStateEntity(lastActiveEpochMs = nowMs())
        dao.upsertState(state.copy(lastActiveEpochMs = nowMs()))
    }

    private suspend fun compactIfNeededLocked() {
        val count = dao.messageCount()
        if (count < COMPACTION_TRIGGER_COUNT) return

        val all = dao.allMessages()
        val older = all.dropLast(COMPACTION_KEEP_COUNT)
        if (older.isEmpty()) return

        val state = dao.getState()
        val newSummary = runCatching { summarizer(state?.summary, older) }.getOrElse {
            // Best-effort: if the summarizer call fails (e.g. network), skip compaction this
            // round rather than losing messages — it will be retried on the next append.
            AppLogger.error("ConversationRepository", "Compaction summarizer call failed, will retry next append", it)
            return
        }
        dao.trimToNewest(COMPACTION_KEEP_COUNT)
        dao.upsertState((state ?: ConversationStateEntity(lastActiveEpochMs = nowMs())).copy(summary = newSummary))
    }

    private suspend fun expireIfStaleLocked() {
        val state = dao.getState() ?: return
        val idleMs = nowMs() - state.lastActiveEpochMs
        if (idleMs <= EXPIRY_DAYS * DAY_MS) return

        val remaining = dao.allMessages()
        if (remaining.isNotEmpty()) {
            val finalSummary = runCatching { summarizer(state.summary, remaining) }.getOrElse {
                AppLogger.error("ConversationRepository", "Expiry summarizer call failed, keeping prior summary", it)
                state.summary
            }
            dao.upsertState(
                state.copy(summary = finalSummary, lastActiveEpochMs = nowMs(), pendingClarificationQuestion = null)
            )
        } else {
            dao.upsertState(state.copy(lastActiveEpochMs = nowMs(), pendingClarificationQuestion = null))
        }
        dao.clearMessages()
    }
}

package com.trainsearch.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory fake, no Room, so this test suite stays a plain fast JVM test like the rest of the project. */
private class FakeConversationDao : ConversationDao {
    private val messages = mutableListOf<MessageEntity>()
    private var nextId = 1L
    private var state: ConversationStateEntity? = null

    override suspend fun insertMessage(message: MessageEntity): Long {
        val withId = message.copy(id = nextId++)
        messages += withId
        return withId.id
    }

    override suspend fun allMessages(): List<MessageEntity> = messages.sortedBy { it.id }

    override suspend fun messageCount(): Int = messages.size

    override suspend fun trimToNewest(keep: Int) {
        val toKeep = messages.sortedByDescending { it.id }.take(keep).map { it.id }.toSet()
        messages.retainAll { it.id in toKeep }
    }

    override suspend fun clearMessages() {
        messages.clear()
    }

    override suspend fun getState(id: Int): ConversationStateEntity? = state

    override suspend fun upsertState(state: ConversationStateEntity) {
        this.state = state
    }
}

class ConversationRepositoryTest {

    private fun turn(text: String) = MessageEntity(role = MessageRole.USER, content = text, createdAtEpochMs = 0)

    @Test fun `compaction does not fire below the trigger count`() = runTest {
        val dao = FakeConversationDao()
        var summarizerCalls = 0
        val repo = ConversationRepository(dao, summarizer = { _, _ -> summarizerCalls++; "summary" })

        repeat(COMPACTION_TRIGGER_COUNT - 1) { repo.appendUserMessage("query $it") }

        assertEquals(COMPACTION_TRIGGER_COUNT - 1, dao.messageCount())
        assertEquals(0, summarizerCalls)
    }

    @Test fun `compaction fires exactly at the trigger count and keeps only the newest window`() = runTest {
        val dao = FakeConversationDao()
        var summarizerCalls = 0
        val repo = ConversationRepository(dao, summarizer = { _, _ -> summarizerCalls++; "profile" })

        repeat(COMPACTION_TRIGGER_COUNT) { repo.appendUserMessage("query $it") }

        assertEquals(1, summarizerCalls)
        assertEquals(COMPACTION_KEEP_COUNT, dao.messageCount())
        // The newest COMPACTION_KEEP_COUNT messages survive, oldest-first.
        val remaining = dao.allMessages().map { it.content }
        val expectedSurvivors = (COMPACTION_TRIGGER_COUNT - COMPACTION_KEEP_COUNT until COMPACTION_TRIGGER_COUNT)
            .map { "query $it" }
        assertEquals(expectedSurvivors, remaining)
    }

    @Test fun `compaction summarizer receives the existing summary and only the older messages`() = runTest {
        val dao = FakeConversationDao()
        var seenExisting: String? = "unset"
        var seenOlderCount = -1
        val repo = ConversationRepository(dao, summarizer = { existing, older ->
            seenExisting = existing
            seenOlderCount = older.size
            "new summary"
        })

        repeat(COMPACTION_TRIGGER_COUNT) { repo.appendUserMessage("q$it") }

        assertNull(seenExisting) // no prior summary on the first compaction pass
        assertEquals(COMPACTION_TRIGGER_COUNT - COMPACTION_KEEP_COUNT, seenOlderCount)
        assertEquals("new summary", repo.currentContext().summary)
    }

    @Test fun `expiry past 30 days summarizes once and clears all raw messages`() = runTest {
        val dao = FakeConversationDao()
        var clock = 0L
        var summarizerCalls = 0
        val repo = ConversationRepository(dao, summarizer = { _, _ -> summarizerCalls++; "final profile" }, nowMs = { clock })

        repo.appendUserMessage("hello")
        clock += (EXPIRY_DAYS + 1) * 24 * 60 * 60 * 1000L

        val (ctx, _) = repo.bootstrap()

        assertEquals(1, summarizerCalls)
        assertEquals("final profile", ctx.summary)
        assertTrue(ctx.recentMessages.isEmpty())
        assertEquals(0, dao.messageCount())
    }

    @Test fun `expiry does not fire at exactly the boundary`() = runTest {
        val dao = FakeConversationDao()
        var clock = 0L
        var summarizerCalls = 0
        val repo = ConversationRepository(dao, summarizer = { _, _ -> summarizerCalls++; "profile" }, nowMs = { clock })

        repo.appendUserMessage("hello")
        clock += EXPIRY_DAYS * 24 * 60 * 60 * 1000L

        val (ctx, _) = repo.bootstrap()

        assertEquals(0, summarizerCalls)
        assertEquals(1, ctx.recentMessages.size)
    }

    @Test fun `expiry with no remaining messages does not call the summarizer`() = runTest {
        val dao = FakeConversationDao()
        var clock = 0L
        var summarizerCalls = 0
        val repo = ConversationRepository(dao, summarizer = { _, _ -> summarizerCalls++; "profile" }, nowMs = { clock })

        // Touch last-active without ever adding a message (state exists, no messages).
        repo.appendUserMessage("hi")
        dao.clearMessages()
        clock += (EXPIRY_DAYS + 1) * 24 * 60 * 60 * 1000L

        repo.bootstrap()

        assertEquals(0, summarizerCalls)
        assertFalse(dao.allMessages().isNotEmpty())
    }
}

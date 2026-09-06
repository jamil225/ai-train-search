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
        val repo = ConversationRepository(dao)

        repeat(COMPACTION_TRIGGER_COUNT - 1) { repo.appendUserMessage("query $it") }

        assertEquals(COMPACTION_TRIGGER_COUNT - 1, dao.messageCount())
    }

    @Test fun `compaction fires exactly at the trigger count and keeps only the newest window`() = runTest {
        val dao = FakeConversationDao()
        val repo = ConversationRepository(dao)

        repeat(COMPACTION_TRIGGER_COUNT) { repo.appendUserMessage("query $it") }

        assertEquals(COMPACTION_KEEP_COUNT, dao.messageCount())
        // The newest COMPACTION_KEEP_COUNT messages survive, oldest-first.
        val remaining = dao.allMessages().map { it.content }
        val expectedSurvivors = (COMPACTION_TRIGGER_COUNT - COMPACTION_KEEP_COUNT until COMPACTION_TRIGGER_COUNT)
            .map { "query $it" }
        assertEquals(expectedSurvivors, remaining)
    }

    @Test fun `trip state can be persisted and retrieved`() = runTest {
        val dao = FakeConversationDao()
        val repo = ConversationRepository(dao)

        val state = TripState(origin = "Jaipur", destination = "Mumbai", dates = listOf("2026-09-03"))
        repo.updateTripState(state)

        val ctx = repo.currentContext()
        assertEquals("Jaipur", ctx.tripState?.origin)
        assertEquals("Mumbai", ctx.tripState?.destination)
        assertEquals(listOf("2026-09-03"), ctx.tripState?.dates)
    }

    @Test fun `trip state can be reset`() = runTest {
        val dao = FakeConversationDao()
        val repo = ConversationRepository(dao)

        val state = TripState(origin = "Jaipur", destination = "Mumbai", dates = listOf("2026-09-03"))
        repo.updateTripState(state)
        repo.resetTripState()

        val ctx = repo.currentContext()
        assertNull(ctx.tripState)
    }

    @Test fun `expiry past 30 days clears all raw messages and resets trip state`() = runTest {
        val dao = FakeConversationDao()
        var clock = 0L
        val repo = ConversationRepository(dao, nowMs = { clock })

        repo.appendUserMessage("hello")
        val state = TripState(origin = "Jaipur", destination = "Mumbai")
        repo.updateTripState(state)

        clock += (EXPIRY_DAYS + 1) * 24 * 60 * 60 * 1000L

        val (ctx, _) = repo.bootstrap()

        assertNull(ctx.tripState)
        assertTrue(ctx.recentMessages.isEmpty())
        assertEquals(0, dao.messageCount())
    }

    @Test fun `expiry does not fire at exactly the boundary`() = runTest {
        val dao = FakeConversationDao()
        var clock = 0L
        val repo = ConversationRepository(dao, nowMs = { clock })

        repo.appendUserMessage("hello")
        val state = TripState(origin = "Jaipur", destination = "Mumbai")
        repo.updateTripState(state)

        clock += EXPIRY_DAYS * 24 * 60 * 60 * 1000L

        val (ctx, _) = repo.bootstrap()

        assertEquals("Jaipur", ctx.tripState?.origin)
        assertEquals(1, ctx.recentMessages.size)
    }

    @Test fun `expiry with no remaining messages clears state cleanly`() = runTest {
        val dao = FakeConversationDao()
        var clock = 0L
        val repo = ConversationRepository(dao, nowMs = { clock })

        // Touch last-active and add state without messages.
        repo.appendUserMessage("hi")
        val state = TripState(origin = "Jaipur", destination = "Mumbai")
        repo.updateTripState(state)
        dao.clearMessages()

        clock += (EXPIRY_DAYS + 1) * 24 * 60 * 60 * 1000L

        val (ctx, _) = repo.bootstrap()

        assertNull(ctx.tripState)
        assertFalse(dao.allMessages().isNotEmpty())
    }
}

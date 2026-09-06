package com.trainsearch.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

enum class MessageRole { USER, ASSISTANT }

/** One turn in the ongoing conversation. Append-only; rows are trimmed by compaction/expiry, never edited. */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: MessageRole,
    val content: String,
    val createdAtEpochMs: Long
)

/**
 * Singleton row (always id = SINGLETON_ID) holding the explicit trip state and
 * bookkeeping timestamps. This is a single-user, single-conversation app, so there is
 * no conversation-id concept to model beyond this one row.
 */
@Entity(tableName = "conversation_state")
data class ConversationStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val tripState: String? = null,  // JSON-encoded TripState; null means no state yet
    val lastActiveEpochMs: Long,
    val pendingClarificationQuestion: String? = null
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

class Converters {
    @TypeConverter
    fun roleToString(role: MessageRole): String = role.name

    @TypeConverter
    fun stringToRole(value: String): MessageRole = MessageRole.valueOf(value)

    @TypeConverter
    fun tripStateToString(s: TripState?): String? = s?.let { Json.encodeToString(it) }

    @TypeConverter
    fun stringToTripState(v: String?): TripState? =
        v?.let { runCatching { Json.decodeFromString<TripState>(it) }.getOrNull() }
}

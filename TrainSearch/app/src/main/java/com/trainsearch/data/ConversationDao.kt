package com.trainsearch.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ConversationDao {

    @Insert
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("SELECT * FROM messages ORDER BY id ASC")
    suspend fun allMessages(): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun messageCount(): Int

    /** Keep only the newest [keep] rows (by insertion order); delete everything else. */
    @Query(
        """
        DELETE FROM messages WHERE id NOT IN (
            SELECT id FROM messages ORDER BY id DESC LIMIT :keep
        )
        """
    )
    suspend fun trimToNewest(keep: Int)

    @Query("DELETE FROM messages")
    suspend fun clearMessages()

    @Query("SELECT * FROM conversation_state WHERE id = :id")
    suspend fun getState(id: Int = ConversationStateEntity.SINGLETON_ID): ConversationStateEntity?

    @Upsert
    suspend fun upsertState(state: ConversationStateEntity)
}

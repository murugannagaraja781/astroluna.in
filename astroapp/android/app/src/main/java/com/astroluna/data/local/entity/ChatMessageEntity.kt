package com.astroluna.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey
    val messageId: String,
    val sessionId: String,
    val text: String,
    val senderId: String,
    val timestamp: Long,
    val status: String, // "sent", "delivered", "read"
    val isSentByMe: Boolean
)

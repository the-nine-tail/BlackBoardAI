package com.example.blackboardai.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.blackboardai.domain.entity.ChatMessage

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long
)

fun ChatMessageEntity.toDomain(): ChatMessage {
    return ChatMessage(
        id = id,
        content = content,
        isFromUser = isFromUser,
        timestamp = timestamp
    )
}

fun ChatMessage.toEntity(): ChatMessageEntity {
    return ChatMessageEntity(
        id = id,
        content = content,
        isFromUser = isFromUser,
        timestamp = timestamp
    )
} 
package com.zara.assistant.ui

data class ChatMessage(
    val text: String,
    val role: MessageRole
)

enum class MessageRole { USER, ZARA }

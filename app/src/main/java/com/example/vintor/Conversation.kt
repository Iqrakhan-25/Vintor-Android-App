package com.example.vintor.models
data class Conversation(
    val conversationId: String = "",
    val userId: String = "",
    val userName: String = "",
    val profileImageBase64: String? = null,
    val lastMessage: String = "",
    val timestamp: Long = 0L,
    val lastActive: Long = 0L,
    val isOnline: Boolean = false,
    val unreadCount: Int = 0,
    val isBlocked: Boolean = false,
    val participants: List<String> = emptyList()
)
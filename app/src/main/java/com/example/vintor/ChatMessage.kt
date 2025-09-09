data class ChatMessage(
    var id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val deletedFor: List<String> = emptyList(),
    var status: String = MessageStatus.SENT.name,
    val unreadCount: Int = 0
)
enum class MessageStatus { SENT, DELIVERED, READ }
data class NotificationModel(
    var id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val postId: String = "",
    val type: String = "",
    val commentText: String = "",
    val senderName: String = "",
    val senderImage: String = "",
    val timestamp: Long = 0L,
    var isRead: Boolean = false
)

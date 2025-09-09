import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
object NotificationUtils {
    fun sendNotification(
        receiverId: String,
        postId: String,
        type: String,
        commentText: String = ""
    ) {
        val db = FirebaseFirestore.getInstance()
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val senderId = currentUser.uid
        db.collection("Users").document(senderId).get().addOnSuccessListener { senderDoc ->
            val senderName = senderDoc.getString("fullName") ?: "Unknown User"
            val senderImage = senderDoc.getString("profileImageBase64") ?: ""
            fun createAndSendNotification() {
                val notificationId = db.collection("notifications").document().id
                val notification = NotificationModel(
                    id = notificationId,
                    senderId = senderId,
                    receiverId = receiverId,
                    senderName = senderName,
                    senderImage = senderImage,
                    postId = postId,
                    type = type,
                    commentText = commentText,
                    isRead = false,
                    timestamp = System.currentTimeMillis()
                )
                db.collection("notifications").document(notificationId)
                    .set(notification)
                    .addOnSuccessListener { Log.d("Notification", "Sent") }
                    .addOnFailureListener { Log.e("Notification", "Failed: ${it.message}") }
            }
            when (type) {
                "like" -> {
                    db.collection("notifications")
                        .whereEqualTo("senderId", senderId)
                        .whereEqualTo("receiverId", receiverId)
                        .whereEqualTo("postId", postId)
                        .whereEqualTo("type", "like")
                        .get()
                        .addOnSuccessListener { docs ->
                            if (docs.isEmpty) {
                                createAndSendNotification()
                            } else {
                                Log.d("Notification", "Duplicate like notification avoided.")
                            }
                        }
                }
                "comment" -> {
                    db.collection("notifications")
                        .whereEqualTo("senderId", senderId)
                        .whereEqualTo("receiverId", receiverId)
                        .whereEqualTo("postId", postId)
                        .whereEqualTo("type", "comment")
                        .whereEqualTo("commentText", commentText.trim())
                        .get()
                        .addOnSuccessListener { docs ->
                            if (docs.isEmpty) {
                                createAndSendNotification()
                            } else {
                                Log.d("Notification", "Duplicate comment notification avoided.")
                            }
                        }
                }
                "follow" -> {
                    db.collection("notifications")
                        .whereEqualTo("senderId", senderId)
                        .whereEqualTo("receiverId", receiverId)
                        .whereEqualTo("type", "follow")
                        .get()
                        .addOnSuccessListener { docs ->
                            if (docs.isEmpty) {
                                createAndSendNotification()
                            } else {
                                Log.d("Notification", "Duplicate follow notification avoided.")
                            }
                        }
                }
                else -> {
                    Log.e("Notification", "Unknown notification type: $type")
                }
            }
        }.addOnFailureListener {
            Log.e("Notification", "User fetch failed: ${it.message}")
        }
    }
}

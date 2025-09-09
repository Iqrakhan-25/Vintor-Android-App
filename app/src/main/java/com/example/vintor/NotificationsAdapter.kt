package com.example.vintor
import NotificationModel
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class NotificationAdapter(
    private val context: Context,
    private val notifications: MutableList<NotificationModel>,
    private val onNotificationClick: (NotificationModel) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {
    inner class NotificationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val senderImage: ImageView = view.findViewById(R.id.imageSender)
        val senderName: TextView = view.findViewById(R.id.textSenderName)
        val notificationText: TextView = view.findViewById(R.id.textNotification)
        val timeText: TextView = view.findViewById(R.id.textTime)
        val menuNotification: ImageView = view.findViewById(R.id.menuNotification)
        val rootLayout: View = view.findViewById(R.id.rootLayout)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }
    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]
        holder.senderName.text = notification.senderName
        holder.notificationText.text = when (notification.type) {
            "like" -> "❤️ Liked your post"
            "comment" -> "commented: \"${notification.commentText}\""
            "follow" -> "started following you"
            else -> "sent you a notification"
        }
        holder.timeText.text = getRelativeTime(notification.timestamp)
        if (!notification.senderImage.isNullOrBlank()) {
            try {
                val decoded = Base64.decode(notification.senderImage, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                holder.senderImage.setImageBitmap(bitmap)
            } catch (e: Exception) {
                holder.senderImage.setImageResource(R.drawable.ic_default_avatar)
            }
        } else {
            holder.senderImage.setImageResource(R.drawable.ic_default_avatar)
        }
        // Background color based on isRead
        val bgColor = if (!notification.isRead)
            Color.parseColor("#E4A3BE") // pink
        else
            Color.parseColor("#AABDB8B8") // gray
        holder.rootLayout.setBackgroundColor(bgColor)
        // Menu click
        holder.menuNotification.setOnClickListener { view ->
            val popup = PopupMenu(context, view)
            popup.inflate(R.menu.menu_notification)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_mark_read -> {
                        markAsRead(notification, position)
                        true
                    }
                    R.id.action_delete -> {
                        deleteNotification(notification, position)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
        holder.itemView.setOnClickListener {
            if (!notification.isRead) {
                notification.isRead = true
                notifyItemChanged(position)

                FirebaseFirestore.getInstance()
                    .collection("notifications")
                    .document(notification.id)
                    .update("isRead", true)
            }
            onNotificationClick(notification)
        }
    }
    override fun getItemCount(): Int = notifications.size
    private fun markAsRead(notification: NotificationModel, position: Int) {
        val docId = notification.id
        FirebaseFirestore.getInstance()
            .collection("notifications")
            .document(docId)
            .update("isRead", true)
            .addOnSuccessListener {
                notification.isRead = true
                notifyItemChanged(position)
                Toast.makeText(context, "Marked as read", Toast.LENGTH_SHORT).show()
            }
    }
    private fun deleteNotification(notification: NotificationModel, position: Int) {
        val docId = notification.id
        FirebaseFirestore.getInstance()
            .collection("notifications")
            .document(docId)
            .delete()
            .addOnSuccessListener {
                notifications.removeAt(position)
                notifyItemRemoved(position)
                notifyItemRangeChanged(position, notifications.size)
                Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
            }
    }
    private fun getRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            seconds < 60 -> "just now"
            minutes < 60 -> "$minutes min ago"
            hours < 24 -> "$hours h ago"
            else -> "$days d ago"
        }
    }
}

package com.example.vintor

import ChatMessage
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(
    private val currentUserId: String,
    private var messages: MutableList<ChatMessage>,
    private val onMessageLongClick: ((ChatMessage) -> Unit)? = null,
    private val onMessageClick: ((ChatMessage) -> Unit)? = null,
    private val onSelectionChange: ((Set<String>) -> Unit)? = null,
    private val getMessageStatusIcon: ((ChatMessage) -> Int)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val VIEW_TYPE_SENT = 1
    private val VIEW_TYPE_RECEIVED = 2
    private val selectedMessages = mutableSetOf<String>()
    inner class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textMessage: TextView = itemView.findViewById(R.id.textMessage)
        val textTimestamp: TextView = itemView.findViewById(R.id.textTimestamp)
        val imageStatus: ImageView = itemView.findViewById(R.id.ivUserImage)
    }
    inner class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textMessage: TextView = itemView.findViewById(R.id.textMessage)
        val textTimestamp: TextView = itemView.findViewById(R.id.textTimestamp)
    }
    override fun getItemViewType(position: Int): Int {
        return if (messages[position].senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_sent, parent, false)
            SentMessageViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_received, parent, false)
            ReceivedMessageViewHolder(view)
        }
    }
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages.getOrNull(position) ?: return
        val isSelected = selectedMessages.contains(message.id)
        val bgColor = if (isSelected) {
            ContextCompat.getColor(holder.itemView.context, R.color.baby_Pink)
        } else {
            Color.TRANSPARENT
        }
        holder.itemView.setBackgroundColor(bgColor)
        holder.itemView.setOnLongClickListener {
            onMessageLongClick?.invoke(message)
            true
        }
        holder.itemView.setOnClickListener {
            if (selectedMessages.isNotEmpty()) {
                toggleMessageSelection(message.id)
            } else {
                onMessageClick?.invoke(message)
            }
        }
        when (holder) {
            is SentMessageViewHolder -> bindSentMessage(holder, message)
            is ReceivedMessageViewHolder -> bindReceivedMessage(holder, message)
        }
    }
    private fun bindSentMessage(holder: SentMessageViewHolder, message: ChatMessage) {
        holder.textMessage.text = message.message
        holder.textTimestamp.text = formatTimestamp(message.timestamp)
        val iconRes = getMessageStatusIcon?.invoke(message) ?: when (MessageStatus.valueOf(message.status)) {
            MessageStatus.SENT -> R.drawable.ic_single_gray_tick
            MessageStatus.DELIVERED -> R.drawable.ic_double_gray_tick
            MessageStatus.READ -> R.drawable.ic_double_blue_tick
        }
        holder.imageStatus.setImageResource(iconRes)
    }
    private fun bindReceivedMessage(holder: ReceivedMessageViewHolder, message: ChatMessage) {
        holder.textMessage.text = message.message
        holder.textTimestamp.text = formatTimestamp(message.timestamp)
    }
    fun toggleMessageSelection(messageId: String) {
        if (selectedMessages.contains(messageId)) {
            selectedMessages.remove(messageId)
        } else {
            selectedMessages.add(messageId)
        }
        notifySelectionChanged()
    }
    fun setSelectedMessages(selected: Set<String>) {
        selectedMessages.clear()
        selectedMessages.addAll(selected)
        notifySelectionChanged()
    }
    private fun notifySelectionChanged() {
        notifyDataSetChanged()
        onSelectionChange?.invoke(selectedMessages)
    }
    override fun getItemCount(): Int = messages.size
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}

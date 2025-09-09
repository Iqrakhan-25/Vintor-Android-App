package com.example.vintor.ui

import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.vintor.R
import com.example.vintor.databinding.ItemConversationBinding
import com.example.vintor.models.Conversation
import java.text.SimpleDateFormat
import java.util.*
class ConversationAdapter(
    private var conversations: List<Conversation>,
    private val onItemClick: (Conversation) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder>() {
    private val selectedConversations = mutableSetOf<String>()
    inner class ConversationViewHolder(val binding: ItemConversationBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(conversation: Conversation) {
            binding.textViewUserName.text = conversation.userName
            binding.textViewLastMessage.text = conversation.lastMessage
            if (conversation.unreadCount > 0) {
                binding.textViewUnreadCount.visibility = View.VISIBLE
                binding.textViewUnreadCount.text = conversation.unreadCount.toString()
            } else {
                binding.textViewUnreadCount.visibility = View.GONE
            }
            if (conversation.isOnline) {
                binding.textViewLastSeenOrOnline.text = "Online"
                binding.textViewLastSeenOrOnline.setTextColor(Color.parseColor("#4CAF50")) // Green
            } else {
                binding.textViewLastSeenOrOnline.text = formatLastSeen(conversation.lastActive)
                binding.textViewLastSeenOrOnline.setTextColor(Color.GRAY)
            }
            if (!conversation.profileImageBase64.isNullOrEmpty()){
            try {
                    val decodedString = Base64.decode(conversation.profileImageBase64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                    binding.imageViewProfile.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    binding.imageViewProfile.setImageResource(R.drawable.ic_default_avatar)
                }
            } else {
                binding.imageViewProfile.setImageResource(R.drawable.ic_default_avatar)
            }
            if (selectedConversations.contains(conversation.conversationId)) {
                binding.root.setBackgroundColor(Color.parseColor("#FFDDDDDD"))
            } else {
                binding.root.setBackgroundColor(Color.TRANSPARENT)
            }
            binding.root.setOnClickListener {
                if (selectedConversations.isNotEmpty()) {
                    toggleSelection(conversation.conversationId)
                } else {
                    onItemClick(conversation)
                }
            }
            binding.root.setOnLongClickListener {
                toggleSelection(conversation.conversationId)
                true
            } } }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val binding = ItemConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ConversationViewHolder(binding)
    }
    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        holder.bind(conversations[position])
    }
    override fun getItemCount(): Int = conversations.size
    fun setList(newList: List<Conversation>) {
        conversations = newList
        selectedConversations.clear()
        notifyDataSetChanged()
        onSelectionChanged(selectedConversations.size)
    }
    fun toggleSelection(conversationId: String) {
        if (selectedConversations.contains(conversationId)) {
            selectedConversations.remove(conversationId)
        } else {
            selectedConversations.add(conversationId)
        }
        notifyDataSetChanged()
        onSelectionChanged(selectedConversations.size)
    }
    fun clearSelections() {
        selectedConversations.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }
    fun getSelectedConversations(): List<String> = selectedConversations.toList()
    fun getSelectedCount(): Int = selectedConversations.size
    private fun formatLastSeen(lastSeenMillis: Long): String {
        if (lastSeenMillis <= 0L) return " "
        val now = Calendar.getInstance()
        val lastSeen = Calendar.getInstance().apply { timeInMillis = lastSeenMillis }
        val sameDay = now.get(Calendar.YEAR) == lastSeen.get(Calendar.YEAR) &&
         now.get(Calendar.DAY_OF_YEAR) == lastSeen.get(Calendar.DAY_OF_YEAR)
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val wasYesterday = yesterday.get(Calendar.YEAR) == lastSeen.get(Calendar.YEAR) &&
        yesterday.get(Calendar.DAY_OF_YEAR) == lastSeen.get(Calendar.DAY_OF_YEAR)
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return when {
            sameDay -> "Today ${timeFormat.format(Date(lastSeenMillis))}"
            wasYesterday -> "Yesterday ${timeFormat.format(Date(lastSeenMillis))}"
            else -> {
                val fullFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                fullFormat.format(Date(lastSeenMillis))
            } } } }

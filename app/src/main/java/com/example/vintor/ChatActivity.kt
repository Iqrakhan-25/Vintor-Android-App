package com.example.vintor

import ChatMessage
import android.content.ContentValues.TAG
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Editable
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.vintor.databinding.ActivityChatBinding
import com.example.vintor.fragments.ChatFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import java.text.SimpleDateFormat
import java.util.*

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: ChatAdapter
    private var messagesListener: ListenerRegistration? = null
    private var userListener: ListenerRegistration? = null
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var currentUserId: String
    private lateinit var chatPartnerId: String
    private lateinit var conversationId: String
    private val messages = mutableListOf<ChatMessage>()
    private val selectedMessageIds = mutableSetOf<String>()
    private var hasBlockedPartner = false
    private var isBlockedByPartner = false
    private var isPartnerChatActivityOpen = false
    private var isActivityRunning = false
    private var partnerLastSeen: Long = 0L
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initializeUserData()
        setupViews()
        setupRecyclerView()
        setupListeners()
    }
    override fun onResume() {
        super.onResume()
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userDocRef = FirebaseFirestore.getInstance().collection("Users").document(currentUserId)
        userDocRef.update("chatActivityOpen", true, "lastActive", System.currentTimeMillis())
        isActivityRunning = true
        updateUserPresence(true)
        setupFirestoreListeners()
        markMessagesAsRead()
    }
    private fun updateUserPresence(isOnline: Boolean) {
        firestore.collection("Users").document(currentUserId)
            .update(
                "lastActive", System.currentTimeMillis(),
                "chatActivityOpen", isOnline
            )
    }
    override fun onPause() {
        super.onPause()
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userDocRef = FirebaseFirestore.getInstance().collection("Users").document(currentUserId)
        userDocRef.update("chatActivityOpen", false, "lastActive", System.currentTimeMillis())
        isActivityRunning = false
        updateUserPresence(false)
        cleanupListeners()
    }
    override fun onDestroy() {
        super.onDestroy()
        cleanupListeners()
    }
    private fun initializeUserData() {
        currentUserId = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        chatPartnerId = intent.getStringExtra("RECEIVER_ID") ?: run {
            Toast.makeText(this, "No chat partner specified", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        conversationId = intent.getStringExtra("CONVERSATION_ID") ?:
        if (currentUserId < chatPartnerId) "$currentUserId-$chatPartnerId" else "$chatPartnerId-$currentUserId"
        binding.chatUserName.text = intent.getStringExtra("RECEIVER_NAME") ?: "Unknown"
        loadProfileImage(intent.getStringExtra("PROFILE_IMAGE"))
    }
    private fun setupViews() {
        binding.btnSend.isEnabled = false
        binding.tvUserBlocked.visibility = View.GONE
        binding.btnDeleteSelected.visibility = View.GONE
    }
    private fun setupRecyclerView() {
        adapter = ChatAdapter(
            currentUserId = currentUserId,
            messages = messages,
            onMessageLongClick = { message -> toggleMessageSelection(message.id) },
            onMessageClick = { message ->
                if (selectedMessageIds.isNotEmpty()) toggleMessageSelection(message.id)
            }
        )
        binding.recyclerViewChat.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply { stackFromEnd = true }
            adapter = this@ChatActivity.adapter
            setHasFixedSize(true)
            itemAnimator = null
        }
    }
    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnMenu.setOnClickListener { showChatMenu(it) }
        binding.btnDeleteSelected.setOnClickListener { deleteSelectedMessages() }

        binding.etMessage.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                binding.btnSend.isEnabled = !s.isNullOrBlank() && !hasBlockedPartner && !isBlockedByPartner
            }
        })
        binding.btnSend.setOnClickListener {
            val msg = binding.etMessage.text.toString().trim()
            if (msg.isNotEmpty()) sendMessage(msg)
        }
    }
    private fun setupFirestoreListeners() {
        // Listener for partner user data
        userListener = firestore.collection("Users").document(chatPartnerId)
            .addSnapshotListener { snapshot, error ->
                if (!isActivityRunning) return@addSnapshotListener
                if (error != null) {
                    Log.e("ChatActivity", "Partner info error", error)
                    return@addSnapshotListener
                }
                val user = snapshot?.toObject(User::class.java) ?: return@addSnapshotListener
                checkIfCurrentUserBlockedPartner()
                isBlockedByPartner = user.blockedUsers?.contains(currentUserId) == true
                updateBlockedUI()
                binding.chatUserName.text = if (!isBlockedByPartner) user.fullName else "Unknown"
                if (!isBlockedByPartner) {
                    loadProfileImage(user.profileImageBase64)
                } else {
                    binding.chatUserImage.setImageResource(R.drawable.ic_default_avatar)
                }
                val prevChatOpen = isPartnerChatActivityOpen
                isPartnerChatActivityOpen = user.chatActivityOpen ?: false
                partnerLastSeen = user.lastActive ?: 0L
                updateLastSeenUI()
                if (isPartnerChatActivityOpen != prevChatOpen) {
                    updateMessageStatuses()
                }
            }
        // Listener for messages
        messagesListener = firestore.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (!isActivityRunning) return@addSnapshotListener
                if (error != null) {
                    Log.e(TAG, "Messages error", error)
                    return@addSnapshotListener
                }
                val newMessages = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        // Safe deletedFor check
                        val deletedFor = when (val field = doc.get("deletedFor")) {
                            is List<*> -> field.filterIsInstance<String>()
                            else -> emptyList()
                        }
                        if (deletedFor.contains(currentUserId)) {
                            return@mapNotNull null
                        }
                        doc.toObject(ChatMessage::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing message ${doc.id}", e)
                        null
                    }
                } ?: emptyList()
                if (newMessages != messages) {
                    messages.clear()
                    messages.addAll(newMessages)
                    adapter.notifyDataSetChanged()
                    scrollToBottom()
                    markMessagesAsRead()
                }
            }
    }
    private fun sendMessage(text: String) {
        if (text.isBlank() || hasBlockedPartner || isBlockedByPartner) return
        binding.btnSend.isEnabled = false
        val messageId = UUID.randomUUID().toString()
        val message = ChatMessage(
            id = messageId,
            senderId = currentUserId,
            receiverId = chatPartnerId,
            message = text,
            timestamp = System.currentTimeMillis(),
            status = getInitialMessageStatus()
        )
        // Add message locally to UI immediately
        messages.add(message)
        adapter.notifyItemInserted(messages.size - 1)
        scrollToBottom()
        //  Determine conversation ID safely
        val convId = ChatFragment.getConversationId(currentUserId, chatPartnerId)
        // Update conversation via ChatFragment (handles local + Firestore)
        ChatFragment.instance?.sendMessageToUser(chatPartnerId, text)
        // Send the message to Firestore messages collection
        firestore.collection("conversations")
            .document(convId)
            .collection("messages")
            .document(messageId)
            .set(message)
            .addOnCompleteListener { task ->
                binding.btnSend.isEnabled = true
                if (task.isSuccessful) {
                    binding.etMessage.text?.clear()
                } else {
                    handleSendFailure(message, task.exception)
                }
            }
            .addOnFailureListener { e ->
                binding.btnSend.isEnabled = true
                handleSendFailure(message, e)
            }
    }
    private fun getInitialMessageStatus(): String {
        return when {
            isPartnerChatActivityOpen -> MessageStatus.READ.name
            isPartnerRecentlyActive() -> MessageStatus.DELIVERED.name
            else -> MessageStatus.SENT.name
        }
    }
    private fun handleSendFailure(message: ChatMessage, exception: Exception?) {
        messages.remove(message)
        adapter.notifyDataSetChanged()
        Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
        Log.e(TAG, "Message send failed", exception)
    }
    private fun isPartnerRecentlyActive(): Boolean {
        val twoMinutesAgo = System.currentTimeMillis() - 120_000
        return partnerLastSeen >= twoMinutesAgo
    }
    private fun updateMessageStatuses() {
        val messagesToUpdate = messages.filter { msg ->
            msg.senderId == currentUserId &&
                    (msg.status == MessageStatus.SENT.name || msg.status == MessageStatus.DELIVERED.name)
        }
        if (messagesToUpdate.isEmpty()) return
        val newStatus = if (isPartnerChatActivityOpen) MessageStatus.READ.name else MessageStatus.DELIVERED.name
        val batch = firestore.batch()
        messagesToUpdate.forEach { msg ->
            if (msg.status != newStatus) {
                batch.update(
                    firestore.collection("conversations")
                        .document(conversationId)
                        .collection("messages")
                        .document(msg.id),
                    "status", newStatus
                )
            }
        }
        batch.commit()
    }
    private fun markMessagesAsRead() {
        val unreadMessages = messages.filter {
            it.receiverId == currentUserId && it.status != MessageStatus.READ.name
        }
        if (unreadMessages.isEmpty()) return
        val batch = firestore.batch()
        val convRef = firestore.collection("conversations").document(conversationId)
        unreadMessages.forEach { msg ->
            batch.update(
                convRef.collection("messages").document(msg.id),
                "status", MessageStatus.READ.name
            )
        }
        batch.update(convRef, "unreadCounts.$currentUserId", 0)
        batch.commit()
            .addOnSuccessListener {
                unreadMessages.forEach { msg ->
                    val idx = messages.indexOfFirst { it.id == msg.id }
                    if (idx != -1) messages[idx] = messages[idx].copy(status = MessageStatus.READ.name)
                }
                adapter.notifyDataSetChanged()
            }
    }
    private fun updateLastSeenUI() {
        runOnUiThread {
            if (hasBlockedPartner || isBlockedByPartner) {
                binding.chatUserStatus.text = " "
                return@runOnUiThread
            }
            binding.chatUserStatus.text = when {
                isPartnerChatActivityOpen -> "Online"
                partnerLastSeen > 0 -> "Last seen ${formatLastSeenTime(partnerLastSeen)}"
                else -> "Offline"
            }
        }
    }
    private fun formatLastSeenTime(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
    private fun toggleMessageSelection(messageId: String) {
        if (selectedMessageIds.contains(messageId)) {
            selectedMessageIds.remove(messageId)
        } else {
            selectedMessageIds.add(messageId)
        }
        adapter.setSelectedMessages(selectedMessageIds)
        binding.btnDeleteSelected.visibility =
            if (selectedMessageIds.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun deleteSelectedMessages() {
        if (selectedMessageIds.isEmpty()) return
        val batch = firestore.batch()
        selectedMessageIds.forEach { id ->
            batch.update(
                firestore.collection("conversations")
                    .document(conversationId)
                    .collection("messages")
                    .document(id),
                "deletedFor", FieldValue.arrayUnion(currentUserId)
            )
        }
        batch.commit()
            .addOnSuccessListener {
                // Remove deleted messages locally
                messages.removeAll { selectedMessageIds.contains(it.id) }
                adapter.notifyDataSetChanged()
                updateConversationLastMessageAfterDeletion()
                selectedMessageIds.clear()
                binding.btnDeleteSelected.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "Failed to delete messages", e)
            }
    }
    private fun updateConversationLastMessageAfterDeletion() {
        val visibleMessages = messages.filter { !(it.deletedFor?.contains(currentUserId) ?: false) }
        if (visibleMessages.isEmpty()) {
            firestore.collection("conversations").document(conversationId)
                .update("lastMessage", "", "timestamp", System.currentTimeMillis())
        } else {
            val latestMessage = visibleMessages.maxByOrNull { it.timestamp }
            firestore.collection("conversations").document(conversationId)
                .update(
                    "lastMessage", latestMessage?.message ?: "",
                    "timestamp", latestMessage?.timestamp ?: System.currentTimeMillis()
                )
        }
    }
    private fun clearChat() {
        if (messages.isEmpty()) {
            Toast.makeText(this, "No messages to clear", Toast.LENGTH_SHORT).show()
            return
        }

        val messagesCollectionRef = firestore.collection("conversations")
            .document(conversationId)
            .collection("messages")
        messagesCollectionRef.get()
            .addOnSuccessListener { querySnapshot ->
                val batch = firestore.batch()
                for (doc in querySnapshot.documents) {
                    batch.update(
                        doc.reference,
                        "deletedFor",
                        FieldValue.arrayUnion(currentUserId)
                    )
                }
                val convRef = firestore.collection("conversations").document(conversationId)
                batch.update(convRef, "lastMessage", "")

                batch.commit()
                    .addOnSuccessListener {
                        messages.clear()
                        adapter.notifyDataSetChanged()
                    }
                    .addOnFailureListener { e ->
                        Log.e("ChatActivity", "clearChat batch commit failed", e)
                        Toast.makeText(this, "Failed to clear chat: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "clearChat failed to get messages", e)
                Toast.makeText(this, "Failed to load messages: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun toggleBlockUser() {
        firestore.collection("Users").document(currentUserId)
            .update("blockedUsers", if (hasBlockedPartner) {
                FieldValue.arrayRemove(chatPartnerId)
            } else {
                FieldValue.arrayUnion(chatPartnerId)
            })
            .addOnSuccessListener {
                hasBlockedPartner = !hasBlockedPartner
                updateBlockedUI()
            }
    }
    private fun showChatMenu(view: View) {
        PopupMenu(this, view).apply {
            menuInflater.inflate(R.menu.chat_menu, menu)
            menu.findItem(R.id.action_block_user).title =
                if (hasBlockedPartner) "Unblock User" else "Block User"
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_clear_chat -> { clearChat(); true }
                    R.id.action_block_user -> { toggleBlockUser(); true }
                    else -> false
                }
            }
            show()
        }
    }
    private fun loadProfileImage(base64: String?) {
        try {
            if (base64.isNullOrEmpty()) throw Exception("Empty image")
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            binding.chatUserImage.setImageBitmap(bitmap)
        } catch (e: Exception) {
            binding.chatUserImage.setImageResource(R.drawable.ic_default_avatar)
        }
    }
    private fun scrollToBottom() {
        binding.recyclerViewChat.post {
            if (messages.isNotEmpty()) {
                binding.recyclerViewChat.smoothScrollToPosition(messages.size - 1)
            }
        }
    }
    private fun checkIfCurrentUserBlockedPartner() {
        firestore.collection("Users").document(currentUserId)
            .get()
            .addOnSuccessListener { doc ->
                hasBlockedPartner = (doc.get("blockedUsers") as? List<*>)?.contains(chatPartnerId) == true
                updateBlockedUI()
            }
    }
    private fun updateBlockedUI() {
        runOnUiThread {
            if (hasBlockedPartner || isBlockedByPartner) {
                binding.tvUserBlocked.visibility = View.VISIBLE
                binding.layoutInput.visibility = View.GONE
                binding.tvUserBlocked.text = when {
                    hasBlockedPartner -> "You have blocked this user"
                    isBlockedByPartner -> "You are blocked by this user"
                    else -> ""
                }
                binding.btnSend.isEnabled = false
                binding.etMessage.isEnabled = false
            } else {
                binding.tvUserBlocked.visibility = View.GONE
                binding.layoutInput.visibility = View.VISIBLE
                binding.btnSend.isEnabled = !binding.etMessage.text.isNullOrBlank()
                binding.etMessage.isEnabled = true
            }
        }
    }
    private fun cleanupListeners() {
        messagesListener?.remove()
        userListener?.remove()
    }
}
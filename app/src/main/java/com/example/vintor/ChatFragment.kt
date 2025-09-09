package com.example.vintor.fragments
import ChatMessage
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.vintor.AiChatActivity
import com.example.vintor.ChatActivity
import com.example.vintor.R
import com.example.vintor.User
import com.example.vintor.databinding.FragmentChatBinding
import com.example.vintor.models.Conversation
import com.example.vintor.ui.ConversationAdapter
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private val firestore = FirebaseFirestore.getInstance()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private val conversations = mutableListOf<Conversation>()
    private lateinit var adapter: ConversationAdapter
    private var conversationListener: ListenerRegistration? = null
    private val messagesListeners = mutableMapOf<String, ListenerRegistration>()
    private val AI_CONVERSATION_ID = "ai_$currentUserId"
    companion object {
        var instance: ChatFragment? = null
        fun getConversationId(currentUserId: String, partnerId: String): String {
            return if (partnerId == "AI") "ai_$currentUserId"
            else listOf(currentUserId, partnerId).sorted().joinToString("-")
        }
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        instance = this
        setupRecyclerView()
        setupSwipeRefresh()
        setupFabButton()
        setupDeleteButton()
        loadOrCreateAiConversation()
        startListeningConversations()
        return binding.root
    }
    private fun setupRecyclerView() {
        adapter = ConversationAdapter(conversations,
            onItemClick = { conversation ->
                if (adapter.getSelectedCount() == 0) {
                    openChatActivity(conversation)
                }
            },
            onSelectionChanged = { selectedCount ->
                binding.topBar.visibility = if (selectedCount > 0) View.VISIBLE else View.GONE
            })
        binding.recyclerViewChat.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ChatFragment.adapter
        }
    }
    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            conversations.clear()
            adapter.setList(conversations)
            removeAllMessagesListeners()
            startListeningConversations()
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }
    private fun setupFabButton() {
        binding.btnAiChat.setOnClickListener {
            startActivity(Intent(requireContext(), AiChatActivity::class.java))
        }
    }
    private fun setupDeleteButton() {
        binding.btnDeleteSelected.setOnClickListener {
            if (adapter.getSelectedCount() > 0) showDeleteConfirmationDialog()
        }
    }
    private fun showDeleteConfirmationDialog() {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Delete Conversations")
            .setMessage("Once deleted, this conversation and all its messages will be permanently lost.")
            .setPositiveButton("Delete") { _, _ -> deleteSelectedConversations() }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
        val width = (requireContext().resources.displayMetrics.widthPixels * 0.8).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_dialog)

    }

    private fun deleteSelectedConversations() {
        val selectedConvIds = adapter.getSelectedConversations()
        if (selectedConvIds.isEmpty()) return

        val batch = firestore.batch()
        val selectedConversations = conversations.filter { selectedConvIds.contains(it.conversationId) }
        val deleteMessagesTasks = mutableListOf<com.google.android.gms.tasks.Task<Void>>()

        for (conv in selectedConversations) {
            val convDocRef = firestore.collection("conversations").document(conv.conversationId)
            val messagesRef = convDocRef.collection("messages")

            val deleteMessagesTask = messagesRef.get().continueWithTask { task ->
                if (task.isSuccessful) {
                    val batchMessages = firestore.batch()
                    for (msgDoc in task.result!!) {
                        batchMessages.delete(msgDoc.reference)
                    }
                    batchMessages.commit()
                } else {
                    throw (task.exception ?: Exception("Failed to get messages for deletion"))
                }
            }
            deleteMessagesTasks.add(deleteMessagesTask)
            if (conv.userId == "AI") {
            } else {
                batch.update(convDocRef, "hiddenFor.$currentUserId", true)
            }
        }
        Tasks.whenAll(deleteMessagesTasks)
            .addOnSuccessListener {
                // Delete AI conversations docs now
                selectedConversations.filter { it.userId == "AI" }
                    .forEach {
                        val convDocRef = firestore.collection("conversations").document(it.conversationId)
                        batch.delete(convDocRef)
                    }
                batch.commit()
                    .addOnSuccessListener {
                        conversations.removeAll { selectedConvIds.contains(it.conversationId) }
                        adapter.setList(conversations)
                        adapter.clearSelections()
                        binding.topBar.visibility = View.GONE
                        if (selectedConversations.any { it.userId == "AI" }) {
                            createAiConversation()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("ChatFragment", "Failed to commit delete batch", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("ChatFragment", "Failed to delete messages", e)
            } }
    fun sendMessageToUser(receiverId: String, messageText: String) {
        val convId = getConversationId(currentUserId, receiverId)
        // Add conversation locally if it doesn’t exist
        if (conversations.none { it.conversationId == convId }) {
            val newConv = Conversation(
                conversationId = convId,
                userId = receiverId,
                userName = if (receiverId == "AI") "AI Assistant" else "Loading...",
                lastMessage = messageText,
                timestamp = System.currentTimeMillis(),
                unreadCount = 0,
                participants = listOf(currentUserId, receiverId)
            )
            conversations.add(0, newConv)
            adapter.setList(conversations)
        }
        // Update Firestore conversation doc
        updateConversationForMessage(receiverId, messageText)
            .addOnFailureListener {
                Log.e("ChatFragment", "Failed to update conversation on message send", it)
            }
    }
    fun updateConversationForMessage(receiverId: String, messageText: String): Task<Void> {
        val convId = getConversationId(currentUserId, receiverId)
        val conversationData = hashMapOf(
            "participants" to listOf(currentUserId, receiverId),
            "lastMessage" to messageText,
            "timestamp" to System.currentTimeMillis(),
            "hiddenFor" to mapOf(currentUserId to false, receiverId to false),
            "unreadCounts.$receiverId" to FieldValue.increment(1)
        )
        return firestore.collection("conversations")
            .document(convId)
            .set(conversationData, SetOptions.merge())
    }
    private fun openChatActivity(conversation: Conversation) {
        val intent = if (conversation.userId == "AI") {
            Intent(requireContext(), AiChatActivity::class.java)
        } else {
            Intent(requireContext(), ChatActivity::class.java).apply {
                putExtra("RECEIVER_ID", conversation.userId)
                putExtra("RECEIVER_NAME", conversation.userName)
                putExtra("CONVERSATION_ID", conversation.conversationId)
                putExtra("PROFILE_IMAGE", conversation.profileImageBase64)
            }
        }
        startActivity(intent)
        markMessagesAsRead(conversation.conversationId)
    }
    private fun loadOrCreateAiConversation() {
        firestore.collection("conversations").document(AI_CONVERSATION_ID)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    createAiConversation()
                } else if (doc.getBoolean("hiddenFor.$currentUserId") == true) {
                    firestore.collection("conversations").document(AI_CONVERSATION_ID)
                        .update("hiddenFor.$currentUserId", false)
                }
            }
            .addOnFailureListener {
                createAiConversation()
            }
    }
    private fun createAiConversation() {
        firestore.collection("conversations").document(AI_CONVERSATION_ID)
            .set(
                mapOf(
                    "participants" to listOf(currentUserId, "AI"),
                    "lastMessage" to " start chatting with AI",
                    "timestamp" to System.currentTimeMillis(),
                    "unreadCounts" to mapOf(currentUserId to 0L),
                    "hiddenFor" to mapOf<String, Boolean>()
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener {
                Log.d("ChatFragment", "AI conversation ready")
            }
            .addOnFailureListener { e ->
                Log.e("ChatFragment", "Failed to create AI conversation", e)
            }
    }
 fun startListeningConversations() {
        conversationListener?.remove()
        conversationListener = firestore.collection("conversations")
            .whereArrayContains("participants", currentUserId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChatFragment", "Failed to listen conversations", error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                Log.d("ChatFragment", "Received conversations: ${snapshot.documents.size}")
                val visibleDocs = snapshot.documents.filter {
                    val hiddenFor = it.get("hiddenFor") as? Map<*, *>
                    hiddenFor?.get(currentUserId) != true
                }
                Log.d("ChatFragment", "Visible conversations after filter: ${visibleDocs.size}")
                var updated = false
                visibleDocs.forEach { doc ->
                    Log.d(
                        "ChatFragment",
                        "Conversation doc id: ${doc.id} lastMessage: ${doc.getString("lastMessage")}"
                    )
                    val convId = doc.id
                    val changeType =
                        snapshot.documentChanges.find { it.document.id == convId }?.type
                    when (changeType) {
                        DocumentChange.Type.ADDED -> {
                            if (!conversations.any { it.conversationId == convId }) {
                                addConversation(doc)
                                updated = true
                            }
                        }
                        DocumentChange.Type.MODIFIED -> {
                            updateConversation(doc)
                            updated = true
                        }
                        DocumentChange.Type.REMOVED -> {
                            removeConversation(convId)
                            updated = true
                        }
                        else -> {
                            if (!conversations.any { it.conversationId == convId }) {
                                addConversation(doc)
                                updated = true
                            } } } }
                if (updated) {
                    conversations.sortByDescending { it.timestamp }
                    adapter.setList(conversations)
                }
            } }
    private fun addConversation(doc: DocumentSnapshot) {
        val convId = doc.id
        val participants = doc.get("participants") as? List<String> ?: return
        val otherUserId = participants.firstOrNull { it != currentUserId } ?: return
        val unreadCounts = doc.get("unreadCounts") as? Map<String, Long> ?: mapOf()
        val unreadCount = unreadCounts[currentUserId] ?: 0L
        val conversation = Conversation(
            conversationId = convId,
            userId = otherUserId,
            userName = if (otherUserId == "AI") "AI Assistant" else "Loading...",
            lastMessage = doc.getString("lastMessage") ?: "start chatting with AI",
            timestamp = doc.getLong("timestamp") ?: 0L,
            unreadCount = unreadCount.toInt(),
            participants = participants,
            profileImageBase64 = if (otherUserId == "AI") "" else null,
            lastActive = if (otherUserId == "AI") System.currentTimeMillis() else 0L,
            isOnline = otherUserId == "AI"
        )
        conversations.add(conversation)
        addMessagesListener(convId)
        if (otherUserId != "AI") loadUserDetails(otherUserId, convId)
    }
    private fun updateConversation(doc: DocumentSnapshot) {
        val convId = doc.id
        val index = conversations.indexOfFirst { it.conversationId == convId }
        if (index == -1) return
        // Safe handling of unreadCounts
        val unreadCounts = doc.get("unreadCounts") as? Map<*, *> ?: mapOf<Any, Any>()
        val unreadCount = (unreadCounts[currentUserId] as? Number)?.toInt() ?: 0
        val participants = doc.get("participants") as? List<String> ?: listOf(currentUserId)
        val otherUserId = participants.firstOrNull { it != currentUserId } ?: return
        // Update conversation safely
        var updatedConv = conversations[index].copy(
            lastMessage = doc.getString("lastMessage") ?: conversations[index].lastMessage,
            timestamp = doc.getLong("timestamp") ?: conversations[index].timestamp,
            unreadCount = unreadCount
        )
        // If AI conversation, mark online and update lastActive
        if (otherUserId == "AI") {
            updatedConv = updatedConv.copy(
                lastActive = System.currentTimeMillis(),
                isOnline = true
            )
        }
        conversations[index] = updatedConv
        // Load user details for non-AI
        if (otherUserId != "AI") loadUserDetails(otherUserId, convId)
    }
    private fun removeConversation(convId: String) {
        val index = conversations.indexOfFirst { it.conversationId == convId }
        if (index == -1) return
        conversations.removeAt(index)
        adapter.setList(conversations)
        messagesListeners[convId]?.remove()
        messagesListeners.remove(convId)
        if (convId == AI_CONVERSATION_ID) {
            createAiConversation()
        }
    }
    private fun addMessagesListener(convId: String) {
        if (messagesListeners.containsKey(convId)) return
        val listener = firestore.collection("conversations")
            .document(convId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChatFragment", "Message listener error for $convId", error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                // Safely map messages
                val visibleMessages = mutableListOf<ChatMessage>()
                var unreadCount = 0
                for (doc in snapshot.documents) {
                    try {
                        val msg = doc.toObject(ChatMessage::class.java)?.copy(id = doc.id) ?: continue
                        val deletedFor = (doc.get("deletedFor") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                        if (!deletedFor.contains(currentUserId)) {
                            visibleMessages.add(msg)
                            // Count unread messages
                            val readBy = doc.get("readBy") as? List<*>
                            if (msg.senderId != currentUserId && (readBy == null || !readBy.contains(currentUserId))) {
                                unreadCount++
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ChatFragment", "Failed to parse message ${doc.id}", e)
                    }
                }
                // Update Firestore unread count
                firestore.collection("conversations").document(convId)
                    .update("unreadCounts.$currentUserId", unreadCount)
                    .addOnFailureListener { Log.e("ChatFragment", "Failed to update unread count", it) }
                // Update last message info from visible messages only
                val latestMsg = visibleMessages.maxByOrNull { it.timestamp }
                val index = conversations.indexOfFirst { it.conversationId == convId }
                if (index != -1) {
                    val updatedConv = conversations[index].copy(
                        lastMessage = latestMsg?.message ?: "",
                        timestamp = latestMsg?.timestamp ?: 0L,
                        unreadCount = unreadCount
                    )
                    conversations[index] = updatedConv
                    conversations.sortByDescending { it.timestamp }
                    adapter.setList(conversations)
                }
            }

        messagesListeners[convId] = listener
    }
    private fun removeAllMessagesListeners() {
        messagesListeners.values.forEach { it.remove() }
        messagesListeners.clear()
    }
    private fun loadUserDetails(userId: String, convId: String) {
        firestore.collection("Users").document(userId)
            .addSnapshotListener { userDoc, error ->
                if (error != null) {
                    Log.e("ChatFragment", "Failed to listen user $userId", error)
                    return@addSnapshotListener
                }
                val user = userDoc?.toObject(User::class.java) ?: return@addSnapshotListener
                firestore.collection("Users").document(currentUserId).get()
                    .addOnSuccessListener { currentUserDoc ->
                        val isBlockedByMe = (currentUserDoc.get("blockedUsers") as? List<*>)?.contains(userId) == true
                        val isBlockedByOther = user.blockedUsers?.contains(currentUserId) == true
                        val index = conversations.indexOfFirst { it.conversationId == convId }
                        if (index == -1) return@addOnSuccessListener
                        val updatedConv = when {
                            isBlockedByMe -> conversations[index].copy(
                                userName = user.fullName,
                                profileImageBase64 = user.profileImageBase64,
                                isBlocked = true,
                                lastActive = user.lastActive ?: 0L,
                                isOnline = user.chatActivityOpen ?: false
                            )
                            isBlockedByOther -> conversations[index].copy(
                                userName = "Unknown",
                                profileImageBase64 = "",
                                isBlocked = true,
                                lastMessage = "[Blocked]",
                                lastActive = 0L,
                                isOnline = false
                            )
                            else -> conversations[index].copy(
                                userName = user.fullName,
                                profileImageBase64 = user.profileImageBase64,
                                isBlocked = false,
                                lastActive = user.lastActive ?: 0L,
                                isOnline = user.chatActivityOpen ?: false
                            )
                        }
                        conversations[index] = updatedConv
                        adapter.notifyItemChanged(index)
                    } }}
    fun markMessagesAsRead(convId: String) {
        val index = conversations.indexOfFirst { it.conversationId == convId }
        if (index != -1) {
            conversations[index] = conversations[index].copy(unreadCount = 0)
            adapter.notifyItemChanged(index)
        }
        val messagesRef = firestore.collection("conversations")
            .document(convId)
            .collection("messages")
        messagesRef.get().addOnSuccessListener { snapshot ->
            val batch = firestore.batch()
            for (doc in snapshot.documents) {
                val readBy = (doc.get("readBy") as? MutableList<String>) ?: mutableListOf()
                if (!readBy.contains(currentUserId)) {
                    readBy.add(currentUserId)
                    batch.update(doc.reference, "readBy", readBy)
                }
            }
            batch.commit().addOnSuccessListener {
                // Reset conversation unread count AFTER marking messages read
                firestore.collection("conversations").document(convId)
                    .update("unreadCounts.$currentUserId", 0)
            }.addOnFailureListener {
                Log.e("ChatFragment", "Failed to mark messages as read", it)
            }
        }.addOnFailureListener {
            Log.e("ChatFragment", "Failed to fetch messages for read update", it)
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        instance = null
        conversationListener?.remove()
        removeAllMessagesListeners()
        _binding = null
    }
}
package com.example.vintor

import ChatMessage
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.vintor.databinding.ActivityAiChatBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.*

class AiChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiChatBinding
    private val auth = FirebaseAuth.getInstance()
    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()
    private val firestore = FirebaseFirestore.getInstance()
    private var conversationId = ""
    private var messagesListener: ListenerRegistration? = null
    private val selectedMessageIds = mutableSetOf<String>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "AiChatActivity"
        private const val OPENROUTER_API_KEY = "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"
        private const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val currentUserId = auth.currentUser?.uid ?: run {
            finish()
            return
        }

        conversationId = "ai_$currentUserId"
        setupRecyclerView()
        setupClickListeners()
        listenToMessages()
    }

    private fun setupRecyclerView() {
        val currentUserId = auth.currentUser?.uid ?: "user"
        adapter = ChatAdapter(
            currentUserId = currentUserId,
            messages = messages,
            onMessageLongClick = { message ->
                toggleMessageSelection(message.id)
                updateTopBar()
            },
            onMessageClick = { message ->
                if (selectedMessageIds.isNotEmpty()) {
                    toggleMessageSelection(message.id)
                    updateTopBar()
                }
            },
            onSelectionChange = { selected ->
                selectedMessageIds.clear()
                selectedMessageIds.addAll(selected)
                updateTopBar()
            },
            getMessageStatusIcon = { message ->
                when (MessageStatus.valueOf(message.status)) {
                    MessageStatus.SENT -> R.drawable.ic_single_gray_tick
                    MessageStatus.DELIVERED -> R.drawable.ic_double_gray_tick
                    MessageStatus.READ -> R.drawable.ic_double_blue_tick
                }
            }
        )

        binding.recyclerViewChat.apply {
            layoutManager = LinearLayoutManager(this@AiChatActivity).apply {
                stackFromEnd = true
            }
            adapter = this@AiChatActivity.adapter
            itemAnimator = null
            setHasFixedSize(true)
        }
    }

    private fun setupClickListeners() {
        binding.send.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                binding.etMessage.setText("")
            }
        }

        binding.btnBack.setOnClickListener {
            if (selectedMessageIds.isNotEmpty()) {
                clearSelections()
            } else {
                finish()
            }
        }

        binding.btnDeleteSelected.setOnClickListener {
            deleteSelectedMessages()
        }
    }

    private fun updateTopBar() {
        if (selectedMessageIds.isNotEmpty()) {
            binding.normalTopBar.visibility = View.GONE
            binding.topBar.visibility = View.VISIBLE
            binding.tvSelectedCount.text = "${selectedMessageIds.size} selected"
        } else {
            binding.normalTopBar.visibility = View.VISIBLE
            binding.topBar.visibility = View.GONE
        }
    }

    private fun clearSelections() {
        selectedMessageIds.clear()
        adapter.setSelectedMessages(selectedMessageIds)
        updateTopBar()
    }

    private fun listenToMessages() {
        messagesListener = firestore.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Listen failed", error)
                    return@addSnapshotListener
                }

                snapshot?.let { querySnapshot ->
                    val newMessages = querySnapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(ChatMessage::class.java)?.copy(id = doc.id)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing message", e)
                            null
                        }
                    }

                    Log.d(TAG, "Fetched ${newMessages.size} messages")
                    messages.clear()
                    messages.addAll(newMessages)
                    adapter.notifyDataSetChanged()
                    scrollToBottom()
                }
            }
    }

    private fun sendMessage(userText: String) {
        val currentUserId = auth.currentUser?.uid ?: return

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderId = currentUserId,
            receiverId = "AI",
            message = userText,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.READ.name
        )

        messages.add(userMessage)
        adapter.notifyItemInserted(messages.size - 1)
        scrollToBottom()

        saveMessageToFirestore(conversationId, userMessage) {
            updateConversationMetadata(conversationId, userText, userMessage.timestamp)
            sendMessageToAI(userText)
        }
    }

    private fun saveMessageToFirestore(conversationId: String, message: ChatMessage, onSuccess: (() -> Unit)? = null) {
        firestore.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .document(message.id)
            .set(message)
            .addOnSuccessListener { onSuccess?.invoke() }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save message", e)
                Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
                messages.remove(message)
                adapter.notifyDataSetChanged()
            }
    }

    private fun updateConversationMetadata(conversationId: String, lastMessage: String, timestamp: Long) {
        val currentUserId = auth.currentUser?.uid ?: return

        val conversationData = mapOf(
            "participants" to listOf(currentUserId, "AI"),
            "lastMessage" to lastMessage,
            "timestamp" to timestamp,
            "conversationName" to "AI Assistant"
        )

        firestore.collection("conversations")
            .document(conversationId)
            .set(conversationData, SetOptions.merge())
    }

    private fun sendMessageToAI(userMessage: String) {
        val apiKey = OPENROUTER_API_KEY.trim()
        val modelName = "openai/gpt-4o-mini"

        val messagesArray = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", "You are a helpful assistant."))
            put(JSONObject().put("role", "user").put("content", userMessage))
        }

        val requestBodyJson = JSONObject().apply {
            put("model", modelName)
            put("messages", messagesArray)
        }.toString()

        val requestBody = requestBodyJson.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(OPENROUTER_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("HTTP-Referer", "https://your-domain.com/") // Must be valid
            .addHeader("X-Title", "Vintor AI Chat")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        Log.d(TAG, "Sending to AI: $requestBodyJson")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { addMessageToChat("Error: ${e.message}", "AI") }
            }

            override fun onResponse(call: Call, response: Response) {
                val rawResponse = response.body?.string().orEmpty()
                Log.d(TAG, "Raw AI Response: $rawResponse")

                try {
                    val json = JSONObject(rawResponse)

                    // If API returned an error
                    if (json.has("error")) {
                        val errorMsg = json.getJSONObject("error").optString("message", "Unknown error")
                        runOnUiThread { addMessageToChat("API Error: $errorMsg", "AI") }
                        return
                    }

                    val choices = json.optJSONArray("choices")
                    var aiReply = ""

                    if (choices != null && choices.length() > 0) {
                        val choice = choices.getJSONObject(0)
                        val messageObj = choice.optJSONObject("message")
                        aiReply = messageObj?.optString("content", "")?.trim() ?: ""
                    }

                    runOnUiThread {
                        if (aiReply.isNotEmpty()) {
                            addMessageToChat(aiReply, "AI")
                        } else {
                            addMessageToChat("No valid AI response found.", "AI")
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { addMessageToChat("Error parsing AI response: ${e.message}", "AI") }
                }
            }
        })
    }
    private fun addMessageToChat(messageText: String, senderId: String) {
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderId = senderId,
            receiverId = auth.currentUser?.uid ?: "",
            message = messageText,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.READ.name
        )

        messages.add(message)
        adapter.notifyItemInserted(messages.size - 1)
        scrollToBottom()

        saveMessageToFirestore(conversationId, message) {
            updateConversationMetadata(conversationId, messageText, message.timestamp)
        }
    }

    private fun toggleMessageSelection(messageId: String) {
        if (selectedMessageIds.contains(messageId)) {
            selectedMessageIds.remove(messageId)
        } else {
            selectedMessageIds.add(messageId)
        }
        adapter.setSelectedMessages(selectedMessageIds)
    }

    private fun deleteSelectedMessages() {
        if (selectedMessageIds.isEmpty()) return

        val batch = firestore.batch()
        selectedMessageIds.forEach { id ->
            val ref = firestore.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .document(id)
            batch.delete(ref)
        }

        batch.commit()
            .addOnSuccessListener {
                messages.removeAll { msg -> selectedMessageIds.contains(msg.id) }
                adapter.notifyDataSetChanged()
                clearSelections()
                scrollToBottom()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete messages", Toast.LENGTH_SHORT).show()
            }
    }

    private fun scrollToBottom() {
        if (messages.isNotEmpty()) {
            binding.recyclerViewChat.post {
                binding.recyclerViewChat.smoothScrollToPosition(messages.size - 1)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        messagesListener?.remove()
    }
}

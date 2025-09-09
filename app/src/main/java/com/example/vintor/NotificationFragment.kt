package com.example.vintor

import NotificationModel
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class NotificationFragment : Fragment() {

    private lateinit var recyclerNotifications: RecyclerView
    private lateinit var markAllReadText: TextView
    private lateinit var notificationAdapter: NotificationAdapter
    private val notifications = mutableListOf<NotificationModel>()
    private val db = FirebaseFirestore.getInstance()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notification, container, false)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerNotifications = view.findViewById(R.id.recyclerNotifications)
        markAllReadText = view.findViewById(R.id.textMarkAllRead)
        notificationAdapter = NotificationAdapter(
            requireContext(),
            notifications
        ) { notification -> handleNotificationClick(notification) }
        recyclerNotifications.layoutManager = LinearLayoutManager(requireContext())
        recyclerNotifications.adapter = notificationAdapter
        markAllReadText.setOnClickListener {
            markAllAsRead()
        }
        fetchNotifications()
    }
    private fun fetchNotifications() {
        db.collection("notifications")
            .whereEqualTo("receiverId", currentUserId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                notifications.clear()
                snapshots?.documents?.forEach { doc ->
                    val notification = doc.toObject(NotificationModel::class.java)
                    if (notification != null) {
                        notification.id = doc.id
                        if (doc.contains("isRead")) {
                            notification.isRead = doc.getBoolean("isRead") ?: false
                        }
                        notifications.add(notification)
                    }
                }
                notificationAdapter.notifyDataSetChanged()
            }
    }
    private fun markAllAsRead() {
        val batch = db.batch()
        notifications.filter { !it.isRead }.forEach { notification ->
            val ref = db.collection("notifications").document(notification.id)
            batch.update(ref, "isRead", true)
            notification.isRead = true
        }
        batch.commit().addOnSuccessListener {
            notificationAdapter.notifyDataSetChanged()
        }
    }
    private fun openUserProfile(userId: String) {
        if (userId == currentUserId) {
            (requireActivity() as? DashBoard)?.openProfileFragmentFromHome()

        } else {
            val intent = Intent(requireContext(), UserProfileActivity::class.java)
            intent.putExtra("USER_ID", userId)
            startActivity(intent)
        }
    }
    private fun handleNotificationClick(notification: NotificationModel) {
        db.collection("notifications").document(notification.id)
            .update("isRead", true)
        notification.isRead = true
        notificationAdapter.notifyDataSetChanged()
        when (notification.type) {
            "like", "comment" -> {
                if (notification.receiverId == currentUserId) {
                    val intent = Intent(requireContext(), PostListActivity::class.java)
                    intent.putExtra("highlightPostId", notification.postId)
                    startActivity(intent)
                }
            }
        "follow" -> {
                openUserProfile(notification.senderId)
            }

            else -> {
            }
        }
    }
}
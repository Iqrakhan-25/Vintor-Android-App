package com.example.vintor

import Post
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.vintor.databinding.ActivityUserProfileBinding
import com.example.vintor.ui.home.PostsAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*

class UserProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUserProfileBinding
    private val firestore = FirebaseFirestore.getInstance()
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val posts = mutableListOf<Post>()
    private lateinit var adapter: PostsAdapter
    private val currentUserId = firebaseAuth.currentUser?.uid ?: ""
    private var userId: String = ""
    private var highlightPostId: String? = null
    private var isFollowing = false
    private var followersCount = 0
    private var followingCount = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        userId = intent.getStringExtra("USER_ID") ?: return
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadUserPosts()
        }
        setupRecyclerView()
        setupListeners()
        loadUserData()
    }
    private fun setupRecyclerView() {
        adapter = PostsAdapter(
            context = this,
            postList = posts,
            highlightPostId = highlightPostId,
            onUserClick = { userId -> openUserProfile(userId) },
            currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        )
        binding.recyclerViewUserPosts.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewUserPosts.adapter = adapter
    }
    private fun setupListeners() {
        binding.topAppBar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        binding.btnFollow.setOnClickListener {
            toggleFollow()
        }
        binding.btnMessage.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("RECEIVER_ID", userId)
            intent.putExtra("RECEIVER_NAME", binding.userName.text.toString())
            startActivity(intent)
        }
    }
    private fun openUserProfile(uid: String) {
        if (uid == currentUserId) return
        val intent = Intent(this, UserProfileActivity::class.java)
        intent.putExtra("USER_ID", uid)
        startActivity(intent)
        finish()
    }
    private fun loadUserData() {
        firestore.collection("Users").document(userId)
            .addSnapshotListener { doc, error ->
                if (error != null || doc == null || !doc.exists()) {
                    Toast.makeText(this, "Failed to load user data", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                binding.userName.text = doc.getString("fullName") ?: ""
                binding.userBio.text = doc.getString("bio") ?: ""
                binding.userCity.text = doc.getString("city") ?: ""
                binding.userEmail.text = doc.getString("email") ?: ""
                followersCount = (doc.getLong("followersCount") ?: 0).toInt().coerceAtLeast(0)
                followingCount = (doc.getLong("followingCount") ?: 0).toInt().coerceAtLeast(0)
                binding.userFollowers.text = "$followersCount\nFollowers"
                binding.userFollowing.text = "$followingCount\nFollowing"
                val followersList = doc.get("followers") as? List<String> ?: emptyList()
                isFollowing = currentUserId.isNotEmpty() && followersList.contains(currentUserId)
                updateFollowButton()
                val base64 = doc.getString("profileImageBase64") ?: ""
                if (base64.isNotEmpty()) {
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    binding.userProfileImage.setImageBitmap(bmp)
                }
                loadUserPosts()
            }
    }
    private fun loadUserPosts() {
        if (userId.isEmpty()) return
        binding.recyclerViewUserPosts.visibility = View.GONE
        binding.emptyTextView.visibility = View.GONE
        binding.swipeRefreshLayout.isRefreshing = true
        firestore.collection("Posts")
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                binding.swipeRefreshLayout.isRefreshing = false

                if (error != null) {
                    return@addSnapshotListener
                }
                posts.clear()
                var totalLikes = 0
                snapshots?.documents?.forEach { doc ->
                    val post = doc.toObject(Post::class.java)?.copy(id = doc.id)
                    if (post != null) {
                        posts.add(post)
                        totalLikes += post.likedBy.size
                    }
                }
                adapter.notifyDataSetChanged()
                binding.postsCount.text = "${posts.size}\nPosts"
                binding.likesCount.text = "$totalLikes\nLikes"
                if (posts.isEmpty()) {
                    binding.emptyTextView.visibility = View.VISIBLE
                }

                binding.recyclerViewUserPosts.visibility = View.VISIBLE
            }
    }
    private fun toggleFollow() {
        if (currentUserId.isEmpty() || userId.isEmpty()) return
        val userDoc = firestore.collection("Users").document(userId)
        val myDoc = firestore.collection("Users").document(currentUserId)
        val batch = firestore.batch()
        if (isFollowing) {
            // Unfollow logic
            batch.update(userDoc, mapOf(
                "followers" to FieldValue.arrayRemove(currentUserId),
                "followersCount" to FieldValue.increment(-1)
            ))
            batch.update(myDoc, mapOf(
                "following" to FieldValue.arrayRemove(userId),
                "followingCount" to FieldValue.increment(-1)
            ))
            isFollowing = false
        } else {
            // Follow logic
            batch.update(userDoc, mapOf(
                "followers" to FieldValue.arrayUnion(currentUserId),
                "followersCount" to FieldValue.increment(1)
            ))
            batch.update(myDoc, mapOf(
                "following" to FieldValue.arrayUnion(userId),
                "followingCount" to FieldValue.increment(1)
            ))
            isFollowing = true
            NotificationUtils.sendNotification(
                receiverId = userId,
                postId = "",
                type = "follow"
            )
        }
        batch.commit().addOnSuccessListener {
         // Live count update will auto refresh due to addSnapshotListener
            updateFollowButton()
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to update follow status", Toast.LENGTH_SHORT).show()
        }
    }
    private fun updateFollowButton() {
        binding.btnFollow.text = if (isFollowing) "Unfollow" else "Follow"
    }
}

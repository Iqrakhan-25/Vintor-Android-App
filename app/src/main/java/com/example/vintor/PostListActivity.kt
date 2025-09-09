package com.example.vintor
import Post
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.vintor.databinding.ActivityPostListBinding
import com.example.vintor.ui.home.PostsAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class PostListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPostListBinding
    private val posts = mutableListOf<Post>()
    private lateinit var postsAdapter: PostsAdapter
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var highlightPostId: String? = null
    private val currentUserId = firebaseAuth.currentUser?.uid ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPostListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        highlightPostId = intent.getStringExtra("highlightPostId")
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "My Posts"
        }
        binding.topAppBar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        // RecyclerView setup
        binding.recyclerPosts.layoutManager = LinearLayoutManager(this)
        postsAdapter = PostsAdapter(
            context = this,
            postList = posts,
            highlightPostId = highlightPostId,
            onUserClick = { /* No user click here */ },
            currentUserId = currentUserId
        )
        binding.recyclerPosts.adapter = postsAdapter

        loadUserPosts()
    }

    private fun loadUserPosts() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        binding.swipeRefreshLayout.isRefreshing = true
        firestore.collection("Posts")
            .whereEqualTo("userId", uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                binding.swipeRefreshLayout.isRefreshing = false
                if (error != null) {
                    error.printStackTrace()
                    return@addSnapshotListener
                }
                posts.clear()
                var highlightedPost: Post? = null
                snapshots?.forEach { doc ->
                    val post = doc.toObject(Post::class.java)?.copy(id = doc.id)
                    if (post != null) {
                        if (post.id == highlightPostId) {
                            highlightedPost = post // Save highlighted post
                        } else {
                            posts.add(post)
                        }
                    }
                }
                // 🔹 Add highlighted post at top if found
                highlightedPost?.let {
                    posts.add(0, it)
                }

                postsAdapter.notifyDataSetChanged()
            }
    }
}
package com.example.vintor

import Post

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vintor.adapters.CommentsAdapter
import com.example.vintor.databinding.FragmentHomeBinding
import com.example.vintor.models.Comment
import com.example.vintor.ui.home.PostsAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class HomeFragment : Fragment() {
    private val highlightPostId: String? = null
    private lateinit var binding: FragmentHomeBinding
    private lateinit var postsAdapter: PostsAdapter
    private lateinit var userSuggestionAdapter: UserSuggestionAdapter
    private lateinit var tvNoUserFound: TextView
    private val postsList = mutableListOf<Post>()
    private val suggestionsList = mutableListOf<User>()
    private val db = FirebaseFirestore.getInstance()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        parentFragmentManager.setFragmentResultListener("REFRESH_HOME", this) { _, _ ->
            loadPosts()
        }
        tvNoUserFound = TextView(requireContext()).apply {
            text = "No user found"
            visibility = View.GONE
            textSize = 16f
            setTextColor(resources.getColor(android.R.color.darker_gray))
            setPadding(16, 16, 16, 16)
        }
        (binding.homeRecyclerViewUsers.parent as ViewGroup).addView(tvNoUserFound)
        setupPostRecycler()
        setupUserSuggestionRecycler()
        setupSearchBar()
        setupSwipeRefresh()
        loadPosts()
        return binding.root
    }
    private fun setupPostRecycler() {
        postsAdapter = PostsAdapter(
            context = requireContext(),
            postList = postsList,
            highlightPostId = highlightPostId, // 👈 یہاں pass کرو
            onUserClick = { userId -> openUserProfile(userId) },
            currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        )
        binding.homeRecyclerViewPosts.layoutManager = LinearLayoutManager(requireContext())
        binding.homeRecyclerViewPosts.adapter = postsAdapter
    }
    private fun setupUserSuggestionRecycler() {
        userSuggestionAdapter = UserSuggestionAdapter(suggestionsList) { user ->
            openUserProfile(user.id)
        }
        binding.homeRecyclerViewUsers.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.homeRecyclerViewUsers.adapter = userSuggestionAdapter
        binding.homeRecyclerViewUsers.visibility = View.GONE
    }
    private fun setupSwipeRefresh() {
        binding.homeSwipeRefresh.setOnRefreshListener {
            binding.homeEtSearch.text?.clear()
            hideSuggestions()
            loadPosts()
        }
    }
    private fun setupSearchBar() {
        val searchView = binding.homeEtSearch
        searchView.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                if (query.isNotEmpty()) {
                    searchUsers(query)
                } else {
                    hideSuggestions()
                }
            }
        })
    }
    private fun searchUsers(query: String) {
        db.collection("Users")
            .orderBy("fullName")
            .startAt(query)
            .endAt(query + "\uf8ff")
            .get()
            .addOnSuccessListener { snapshot ->
                suggestionsList.clear()
                for (doc in snapshot.documents) {
                    val user = doc.toObject(User::class.java)
                    if (user != null) {
                        user.id = doc.id
                        if (user.id != currentUserId) {
                            suggestionsList.add(user)
                        }
                    }
                }
                userSuggestionAdapter.notifyDataSetChanged()
                if (suggestionsList.isNotEmpty()) {
                    binding.homeRecyclerViewUsers.visibility = View.VISIBLE
                    tvNoUserFound.visibility = View.GONE
                } else {
                    binding.homeRecyclerViewUsers.visibility = View.GONE
                    tvNoUserFound.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener {
                hideSuggestions()
                Toast.makeText(requireContext(), "Error searching users", Toast.LENGTH_SHORT).show()
            }
    }
    private fun hideSuggestions() {
        suggestionsList.clear()
        userSuggestionAdapter.notifyDataSetChanged()
        binding.homeRecyclerViewUsers.visibility = View.GONE
        tvNoUserFound.visibility = View.GONE
    }
    private fun openUserProfile(userId: String) {
        if (userId == currentUserId) {
            (requireActivity() as? DashBoard)?. openProfileFragmentFromHome()
        } else {
            val intent = Intent(requireContext(), UserProfileActivity::class.java)
            intent.putExtra("USER_ID", userId)
            startActivity(intent)
        }
    }
    private var userListeners = mutableMapOf<String, ListenerRegistration>()
    private fun loadPosts() {
        binding.homeShimmerLayout.visibility = View.VISIBLE
        binding.homeShimmerLayout.startShimmer()
        binding.homeRecyclerViewPosts.visibility = View.GONE
        binding.emptyTextView.isVisible = false
        userListeners.values.forEach { it.remove() }
        userListeners.clear()
        db.collection("Posts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    finishLoadingPosts()
                    return@addSnapshotListener
                }
                if (snapshot == null || snapshot.isEmpty) {
                    postsList.clear()
                    finishLoadingPosts()
                    binding.emptyTextView.isVisible = true
                    return@addSnapshotListener
                }
                val tempList = mutableListOf<Post>()
                var loadedCount = 0
                val totalDocs = snapshot.size()
                for (doc in snapshot.documents) {
                    val post = doc.toObject(Post::class.java)
                    if (post != null && post.userId.isNotEmpty()) {
                        val listener = db.collection("Users").document(post.userId)
                            .addSnapshotListener { userDoc, _ ->
                                val user = userDoc?.toObject(User::class.java)
                                post.userName = user?.fullName ?: ""
                                post.profileImage = user?.profileImageBase64 ?: ""
                                post.isOnline = user?.isOnline ?: false
                                val index = postsList.indexOfFirst { it.id == post.id }
                                if (index != -1) {
                                    postsList[index] = post
                                    binding.homeRecyclerViewPosts.adapter?.notifyItemChanged(index)
                                }
                            }
                        userListeners[post.userId] = listener
                        tempList.add(post)
                    } else if (post != null) {
                        tempList.add(post)
                    }
                    loadedCount++
                    if (loadedCount == totalDocs) {
                        tempList.sortByDescending { it.timestamp }
                        postsList.clear()
                        postsList.addAll(tempList)
                        finishLoadingPosts()
                    }
                }
            }
    }
    private fun finishLoadingPosts() {
        postsAdapter.notifyDataSetChanged()
        binding.homeShimmerLayout.stopShimmer()
        binding.homeShimmerLayout.visibility = View.GONE
        binding.homeRecyclerViewPosts.visibility = View.VISIBLE
        binding.homeSwipeRefresh.isRefreshing = false
        binding.emptyTextView.isVisible = postsList.isEmpty()
    }
}
package com.example.vintor.ui.home
import android.view.ViewGroup
import NotificationModel
import Post
import android.util.Base64
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.vintor.R
import com.example.vintor.adapters.CommentsAdapter
import com.example.vintor.models.Comment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.*

class PostsAdapter(
    private val context: Context,
    private val postList: MutableList<Post>,
    private val highlightPostId: String?,
    private val onUserClick: (String) -> Unit,
    private val currentUserId: String

) : RecyclerView.Adapter<PostsAdapter.PostViewHolder>() {

    private val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private val firestore = FirebaseFirestore.getInstance()
    inner class PostViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userImage: ImageView = view.findViewById(R.id.postUserImage)
        val userNameTV: TextView = view.findViewById(R.id.postUserName)
        val contentTV: TextView = view.findViewById(R.id.postContent)
        val timestampTV: TextView = view.findViewById(R.id.postTimestamp)
        val imageIV: ImageView = view.findViewById(R.id.postImage)
        val likeIcon: ImageView = view.findViewById(R.id.likeIcon)
        val likeCountTV: TextView = view.findViewById(R.id.likeCount)
        val commentIcon: ImageView = view.findViewById(R.id.commentIcon)
        val menuView: View = view.findViewById(R.id.postMenu)
        val onlineStatusDot: View = itemView.findViewById(R.id.onlineStatusDot)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view)
    }
    override fun getItemCount(): Int = postList.size
    private val db = FirebaseFirestore.getInstance()
    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = postList[position]
        if (highlightPostId != null && post.id == highlightPostId) {
            val highlightColor = Color.parseColor("#FF5722") // Dark red
            val normalColor = Color.parseColor("#FFEB3B")    // Yellow
            holder.itemView.setBackgroundColor(highlightColor)
            Handler(Looper.getMainLooper()).postDelayed({
                holder.itemView.setBackgroundColor(normalColor)
            }, 10_000L) // 10 sec
        } else {
            holder.itemView.setBackgroundColor(Color.parseColor("#FFEB3B"))
        }
        holder.onlineStatusDot.visibility = if (post.isOnline) View.VISIBLE else View.GONE
        holder.contentTV.text = post.content ?: ""
        holder.timestampTV.text = getRelativeTime(post.timestamp)
        if (!post.imageUrl.isNullOrEmpty()) {
            holder.imageIV.visibility = View.VISIBLE
            Glide.with(context)
                .load(post.imageUrl)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(holder.imageIV)
        } else {
            holder.imageIV.visibility = View.GONE

        }
        firestore.collection("Users").document(post.userId).get()
            .addOnSuccessListener { snapshot ->
                val fullName = snapshot.getString("fullName") ?: "User"
                val profileImageBase64 = snapshot.getString("profileImageBase64") ?: ""

                holder.userNameTV.text = fullName
                if (profileImageBase64.isBlank() || profileImageBase64 == "null") {
                    holder.userImage.setImageResource(R.drawable.ic_default_avatar)
                } else { // Convert Base64 to Bitmap
                    try {
                        val imageBytes = Base64.decode(profileImageBase64, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        holder.userImage.setImageBitmap(bitmap)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        holder.userImage.setImageResource(R.drawable.ic_default_avatar)
                    }
                }
            }
            .addOnFailureListener {
                holder.userNameTV.text = "User"
                holder.userImage.setImageResource(R.drawable.ic_default_avatar)
            }

        holder.userImage.setOnClickListener { onUserClick(post.userId) }
        holder.userNameTV.setOnClickListener { onUserClick(post.userId) }

        val isLiked = post.likedBy.contains(uid)
        holder.likeIcon.setImageResource(if (isLiked) R.drawable.ic_liked else R.drawable.ic_like)
        holder.likeCountTV.text = post.likedBy.size.toString()
        holder.likeIcon.setOnClickListener {
            if (!isLiked && post.userId != uid) {
                NotificationUtils.sendNotification(
                    receiverId = post.userId,
                    postId = post.id,
                    type = "like"
                )
            }
            toggleLike(post, position) }
        holder.commentIcon.setOnClickListener {
            openCommentsBottomSheet(post)
        }
        // Show 3-dot menu only if the post belongs to the current user
        if (post.userId == currentUserId) {
            holder.menuView.visibility = View.VISIBLE
            holder.menuView.setOnClickListener {
                val popup = PopupMenu(holder.itemView.context, holder.menuView)
                popup.inflate(R.menu.post_options_menu)
                // You can also hide edit/delete here if needed
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_edit -> {
                            showEditDialog(post, position)
                            true
                        }
                        R.id.action_delete -> {
                            confirmDelete(post, position)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
        } else {
            holder.menuView.visibility = View.GONE
        }
    }
    private fun getRelativeTime(timestamp: Timestamp?): String {
        timestamp ?: return "Unknown"
        val postDate = timestamp.toDate()
        val now = Date()
        val diffInMillis = now.time - postDate.time
        val seconds = diffInMillis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        return when {
            seconds < 60 -> "Just now"
            minutes < 60 -> "$minutes min ago"
            hours < 24 -> "$hours h ago"
            days < 2 -> "$days d ago"
            else -> {
                val format = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                format.format(postDate)
            }
        }
    }
    private fun toggleLike(post: Post, position: Int) {
        val updatedLikedBy = post.likedBy.toMutableList()
        val isLiked = updatedLikedBy.contains(uid)
        if (isLiked) {
            updatedLikedBy.remove(uid)
        } else {
            updatedLikedBy.add(uid)
        }
        postList[position] = post.copy(likedBy = updatedLikedBy)
        notifyItemChanged(position)
        firestore.collection("Posts")
            .document(post.id)
            .update("likedBy", updatedLikedBy)
    }
    private fun openCommentsBottomSheet(post: Post) {
        val view = LayoutInflater.from(context).inflate(R.layout.layout_bottom_sheet_comments, null)
        val dialog = BottomSheetDialog(context)
        dialog.setContentView(view)
        val commentInput = view.findViewById<EditText>(R.id.etCommentInput)
        val sendBtn = view.findViewById<ImageButton>(R.id.btnSendComment)
        val commentsRV = view.findViewById<RecyclerView>(R.id.rvCommentsList)
        val commentList = mutableListOf<Comment>()
        val commentAdapter = CommentsAdapter(
            commentList = commentList,
            currentUserId = uid,
            postId = post.id
        ) { comment ->
            showDeleteCommentDialog(post.id, comment)
        }
        commentsRV.layoutManager = LinearLayoutManager(context)
        commentsRV.adapter = commentAdapter
        firestore.collection("Posts").document(post.id)
            .collection("comments").orderBy("timestamp")
            .addSnapshotListener { snapshots, _ ->
                commentList.clear()
                snapshots?.forEach { doc ->
                    val commentId = doc.id
                    val userId = doc.getString("userId") ?: ""
                    val commentText = doc.getString("text") ?: ""
                    val commentTime = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                        .format(doc.getTimestamp("timestamp")?.toDate() ?: Date())
                    val commentUserName = doc.getString("userName") ?: "User"
                    //  Now fetch the user's profile image using userId
                    firestore.collection("Users").document(userId).get()
                        .addOnSuccessListener { userDoc ->
                            val userImageBase64 = userDoc.getString("profileImageBase64") ?: ""
                            val comment = Comment(
                                commentId = commentId,
                                userId = userId,
                                text = commentText,
                                timestamp = commentTime,
                                userName = commentUserName,
                                userImage = userImageBase64
                            )
                            commentList.add(comment)
                            commentAdapter.notifyDataSetChanged()
                        }
                        .addOnFailureListener {
                            // If user not found, use default avatar
                            val comment = Comment(
                                commentId = commentId,
                                userId = userId,
                                text = commentText,
                                timestamp = commentTime,
                                userName = commentUserName,
                                userImage = ""
                            )
                            commentList.add(comment)
                            commentAdapter.notifyDataSetChanged()
                        }
                }
            }
        commentInput.addTextChangedListener {
            sendBtn.visibility = if (it.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        sendBtn.setOnClickListener {
            val commentText = commentInput.text.toString().trim()
            if (commentText.isNotEmpty()) {
                firestore.collection("Users").document(uid).get()
                    .addOnSuccessListener { userSnap ->
                        val fullName = userSnap.getString("fullName") ?: "User"
                        val profileImage = userSnap.getString("profileImageBase64") ?: ""
                        val commentData = mapOf(
                            "text" to commentText,
                            "timestamp" to Timestamp.now(),
                            "userId" to uid,
                            "userName" to fullName,
                            "userImage" to profileImage
                        )
                        firestore.collection("Posts").document(post.id)
                            .collection("comments").add(commentData)
                            .addOnSuccessListener {
                                //  Send notification ONLY once, with actual comment text
                                if (post.userId != uid) {
                                    NotificationUtils.sendNotification(
                                        receiverId = post.userId,
                                        postId = post.id,
                                        type = "comment",
                                        commentText = commentText
                                    )
                                }
                            }
                        commentInput.text.clear()
                        sendBtn.visibility = View.GONE
                    }}}

        dialog.show()
    }
    private fun confirmDelete(post: Post, position: Int) {
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Delete Post")
            .setMessage("Are you sure you want to delete this post?")
            .setPositiveButton("Yes") { _, _ -> deletePost(post, position) }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
        val width = (context.resources.displayMetrics.widthPixels * 0.8).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_dialog)
    }

    // Delete Comment dialog
    private fun showDeleteCommentDialog(postId: String, comment: Comment) {
        val dialog = AlertDialog.Builder(context)
            .setTitle("Delete Comment")
            .setMessage("Are you sure you want to delete this comment?")
            .setPositiveButton("Delete") { _, _ ->
                FirebaseFirestore.getInstance()
                    .collection("Posts")
                    .document(postId)
                    .collection("comments")
                    .document(comment.commentId)
                    .delete()
                    .addOnSuccessListener { }
                    .addOnFailureListener {
                        Toast.makeText(context, "Failed to delete", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
        val width = (context.resources.displayMetrics.widthPixels * 0.8).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_dialog)
    }

    // Edit Post dialog
    private fun showEditDialog(post: Post, position: Int) {
        val input = EditText(context).apply {
            setText(post.content)
            setSelection(post.content.length) // cursor at end
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Edit Post")
            .setView(input)
            .setPositiveButton("Update") { _, _ ->
                val newText = input.text.toString().trim()
                if (newText.isNotEmpty()) updatePost(post, position, newText)
                else Toast.makeText(context, "Content cannot be empty", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
        val width = (context.resources.displayMetrics.widthPixels * 0.85).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_dialog)
    }
    private fun updatePost(post: Post, position: Int, newText: String) {
        postList[position] = post.copy(content = newText, timestamp = Timestamp.now())
        notifyItemChanged(position)
        firestore.collection("Posts").document(post.id)
            .update(mapOf("content" to newText, "timestamp" to Timestamp.now()))
    }
    private fun deletePost(post: Post, position: Int) {
        firestore.collection("Posts").document(post.id).delete()
            .addOnSuccessListener {
                postList.removeAt(position)
                notifyItemRemoved(position)
                Toast.makeText(context, "Post deleted", Toast.LENGTH_SHORT).show()
            }
    }
}

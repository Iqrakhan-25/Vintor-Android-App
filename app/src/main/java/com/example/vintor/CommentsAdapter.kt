package com.example.vintor.adapters

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.vintor.R
import com.example.vintor.models.Comment
class CommentsAdapter(
    private val commentList: MutableList<Comment>,
    private val currentUserId: String,
    private val postId: String,
    private val onDeleteComment: (Comment) -> Unit
) : RecyclerView.Adapter<CommentsAdapter.CommentViewHolder>() {
    inner class CommentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userImage: ImageView = view.findViewById(R.id.CommentUserImage)
        val userName: TextView = view.findViewById(R.id.CommentUser)
        val commentText: TextView = view.findViewById(R.id.commentText)
        val timestamp: TextView = view.findViewById(R.id.commentTime)
        val deleteIcon: ImageView = view.findViewById(R.id.deleteCommentIcon)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }
    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = commentList[position]
        holder.userName.text = comment.userName
        holder.commentText.text = comment.text
        holder.timestamp.text = comment.timestamp
        // Show delete icon only if current user is the comment owner
        if (comment.userId == currentUserId) {
            holder.deleteIcon.visibility = View.VISIBLE
            holder.deleteIcon.setOnClickListener {
                onDeleteComment(comment)
            }
        } else {
            holder.deleteIcon.visibility = View.GONE
        }
        if (comment.userImage.isNotEmpty() && comment.userImage != "null") {
            try {
                val imageBytes = Base64.decode(comment.userImage, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                holder.userImage.setImageBitmap(bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
                holder.userImage.setImageResource(R.drawable.ic_default_avatar)
            }
        } else {
            holder.userImage.setImageResource(R.drawable.ic_default_avatar)
        }
    }
    override fun getItemCount() = commentList.size
    fun addComment(comment: Comment) {
        commentList.add(comment)
        notifyItemInserted(commentList.size - 1)
    }
    fun removeComment(comment: Comment) {
        val index = commentList.indexOfFirst { it.commentId == comment.commentId }
        if (index != -1) {
            (commentList as MutableList).removeAt(index)
            notifyItemRemoved(index)
        } } }

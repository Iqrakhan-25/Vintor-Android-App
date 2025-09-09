package com.example.vintor
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class UserSuggestionAdapter(
    private val users: MutableList<User>,
    private val onUserClick: (User) -> Unit
) : RecyclerView.Adapter<UserSuggestionAdapter.UserViewHolder>() {
    inner class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val profileImage: ImageView = view.findViewById(R.id.userProfileImg)
        val userName: TextView = view.findViewById(R.id.userName)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_suggestion, parent, false)
        return UserViewHolder(view)
    }
    override fun getItemCount(): Int = users.size
    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        holder.userName.text = user.fullName
        if (user.profileImageBase64.isNotEmpty()) {
            try {
                val bytes = Base64.decode(user.profileImageBase64, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                holder.profileImage.setImageBitmap(bmp)
            } catch (_: Exception) {
                holder.profileImage.setImageResource(R.drawable.ic_default_avatar)
            }
        } else {
            holder.profileImage.setImageResource(R.drawable.ic_default_avatar)
        }
        holder.itemView.setOnClickListener { onUserClick(user) }
    }
}

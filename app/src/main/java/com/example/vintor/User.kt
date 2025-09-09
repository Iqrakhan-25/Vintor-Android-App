package com.example.vintor
import android.text.TextWatcher
import android.text.Editable
data class User(
    var id: String = "",
    val fullName: String = "",
    val profileImageBase64: String = "",
    val lastActive: Long = 0L,
    val chatActivityOpen: Boolean? = false,
    val blockedUsers: List<String> = emptyList(),
    val isOnline: Boolean = false,
    val chatActive: Boolean = false,
    val hideLastSeen: Boolean = false
)
abstract class SimpleTextWatcher : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: Editable?) {}
}

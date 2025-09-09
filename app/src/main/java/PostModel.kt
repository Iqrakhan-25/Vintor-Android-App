import com.google.firebase.Timestamp
data class Post(
    var id: String = "",
    val userId: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    var userName: String = "",
    var content: String = "",
    var profileImage: String = "",
    var imageUrl: String = "",
    var isOnline: Boolean = false,
    var likedBy: List<String> = emptyList(),
)


package com.example.vintor
import Post
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.*
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.vintor.databinding.FragmentProfileBinding
import com.example.vintor.ui.home.PostsAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ProfileFragment : Fragment() {
    private var highlightPostId: String? = null
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val firestore = FirebaseFirestore.getInstance()
    private val firebaseAuth = FirebaseAuth.getInstance()
    private var profileImageBase64: String = ""
    private var selectedImageUri: Uri? = null
    private var activePopup: PopupWindow? = null
    private val posts: MutableList<Post> = mutableListOf()
    private val postsAdapter by lazy {
        PostsAdapter(
            requireContext(),
            posts,
            highlightPostId,
            onUserClick = { userId ->
                Toast.makeText(requireContext(), "User ID: $userId", Toast.LENGTH_SHORT).show()
            },
            currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        )
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        highlightPostId = arguments?.getString("highlightPostId")
    }
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val uri = result.data!!.data
                uri?.let {
                    selectedImageUri = it
                    val bitmap = MediaStore.Images.Media.getBitmap(requireActivity().contentResolver, it)
                    binding.profileDp.setImageBitmap(bitmap)
                    profileImageBase64 = bitmapToBase64(bitmap)
                    val uid = firebaseAuth.currentUser?.uid
                    if (!uid.isNullOrEmpty()) {
                        firestore.collection("Users").document(uid)
                            .update("profileImageBase64", profileImageBase64)
                            .addOnSuccessListener {
                                Toast.makeText(requireContext(), "Profile picture updated!", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(requireContext(), "Failed to update profile picture", Toast.LENGTH_SHORT).show()
                            } } } } }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        binding.root.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        setupListeners()
        binding.swipeRefreshLayout.isRefreshing = true
        loadUserProfile()
        loadUserPostStats()
        binding.swipeRefreshLayout.setOnRefreshListener {
            binding.swipeRefreshLayout.isRefreshing = true
            loadUserProfile()
            loadUserPostStats()
        }

        return binding.root
    }
    private fun setupListeners() {
        binding.profileDp.setOnClickListener { showImageOptions() }
        // "Your Posts" Button click → Open UserPostsActivity
        binding.btnYourPosts.setOnClickListener {
            val intent = Intent(requireContext(), PostListActivity::class.java)
            intent.putExtra("userId", firebaseAuth.currentUser?.uid)
            startActivity(intent)
        }
        // Save Profile
        binding.saveProfileBtn.setOnClickListener { validateAndSave() }
        binding.profileDob.setOnClickListener {
            val calendar = Calendar.getInstance()
            val datePickerDialog = DatePickerDialog(requireContext(), { _, year, month, day ->
                val selectedCalendar = Calendar.getInstance().apply {
                    set(year, month, day)
                }
                val now = Calendar.getInstance()
                val age = now.get(Calendar.YEAR) - selectedCalendar.get(Calendar.YEAR)
                val futureDob = selectedCalendar.after(now)
                val dob = String.format("%02d/%02d/%04d", day, month + 1, year)
                binding.profileDob.setText(dob)
                when {
                    futureDob -> showFloatingError(binding.profileDob, "Future DOB not allowed")
                    age < 18 || age > 90 -> showFloatingError(binding.profileDob, "Age must be between 18 and 90")
                }

            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
            datePickerDialog.show()
        }
        binding.saveProfileBtn.setOnClickListener { validateAndSave() }
    }
    private fun loadUserProfile() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        firestore.collection("Users").document(uid)
            .addSnapshotListener { document, _ ->
                if (document != null && document.exists()) {
                    binding.profileName.setText(document.getString("fullName") ?: "")
                    binding.profileEmail.setText(document.getString("email") ?: "")
                    binding.profilePhone.setText(document.getString("phone") ?: "")
                    binding.profileBio.setText(document.getString("bio") ?: "")
                    binding.profileCity.setText(document.getString("city") ?: "")
                    binding.profileDob.setText(document.getString("dob") ?: "")
                    val followers = (document.get("followers") as? List<*>)?.size ?: 0
                    val following = (document.get("following") as? List<*>)?.size ?: 0
                    binding.followersCount.text = "$followers\nFollowers"
                    binding.followingCount.text = "$following\nFollowing"
                    binding.postsCount.text = "${posts.size}\nPosts"
                    profileImageBase64 = document.getString("profileImageBase64") ?: ""
                    if (profileImageBase64.isNotEmpty()) {
                        val bytes = Base64.decode(profileImageBase64, Base64.DEFAULT)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        binding.profileDp.setImageBitmap(bmp)
                    } else {
                        binding.profileDp.setImageResource(R.drawable.ic_default_avatar)
                    }
                } } }
    private fun loadUserPostStats() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        binding.swipeRefreshLayout.isRefreshing = true
        firestore.collection("Posts")
            .whereEqualTo("userId", uid)
            .get()
            .addOnSuccessListener { snapshots ->
                binding.swipeRefreshLayout.isRefreshing = false
                var totalPosts = 0
                var totalLikes = 0
                snapshots.forEach { doc ->
                    val post = doc.toObject(Post::class.java)
                    totalPosts++
                    totalLikes += post.likedBy.size
                }
                binding.postsCount.text = "$totalPosts\nPosts"
                binding.likesCount.text = "$totalLikes\nLikes"
            }
            .addOnFailureListener { e ->
                binding.swipeRefreshLayout.isRefreshing = false
                e.printStackTrace()
            }
    }
    private fun validateAndSave() {
        val name = binding.profileName.text?.toString()?.trim() ?: ""
        val phone = binding.profilePhone.text?.toString()?.trim() ?: ""
        val dob = binding.profileDob.text?.toString()?.trim() ?: ""

        if (name.isEmpty()) {
            showFloatingError(binding.profileName, "Name cannot be empty")
            return
        }
        if (!phone.matches(Regex("^0\\d{10}\$"))) {
            showFloatingError(binding.profilePhone, "Enter 11-digit number starting with 0")
            return
        }
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val dobDate = sdf.parse(dob)
        val calendar = Calendar.getInstance()
        calendar.time = dobDate!!
        val now = Calendar.getInstance()
        val age = now.get(Calendar.YEAR) - calendar.get(Calendar.YEAR)
        val futureDob = calendar.after(now)
        if (futureDob) {
            showFloatingError(binding.profileDob, "Future DOB not allowed")
            return
        }
        if (age < 18 || age > 90) {
            showFloatingError(binding.profileDob, "Age must be between 18 and 90")
            return
        }
        val uid = firebaseAuth.currentUser?.uid ?: return
        val userMap = hashMapOf(
            "fullName" to name,
            "email" to (binding.profileEmail.text?.toString() ?: ""),
            "phone" to phone,
            "bio" to (binding.profileBio.text?.toString()?.trim() ?: ""),
            "city" to (binding.profileCity.text?.toString()?.trim() ?: ""),
            "dob" to dob,
            "profileImageBase64" to profileImageBase64
        )
        firestore.collection("Users").document(uid)
            .update(userMap as Map<String, Any>)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Profile updated successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to update profile", Toast.LENGTH_SHORT).show()
            }
    }
    private fun showFloatingError(anchorView: View, message: String) {
        activePopup?.dismiss()
        val popupView = layoutInflater.inflate(R.layout.error_popup, null)
        popupView.findViewById<TextView>(R.id.errorText).text = message
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = false
            elevation = 8f
        }
        activePopup = popupWindow
        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupWidth = popupView.measuredWidth
        val anchorWidth = anchorView.width
        val offsetX = anchorWidth - popupWidth
        val offsetY = 8
        popupWindow.showAsDropDown(anchorView, offsetX, offsetY)
        popupView.postDelayed({ popupWindow.dismiss() }, 30000)
    }
    private fun showImageOptions() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_image_options, null)
        dialog.setContentView(sheetView)
        val chooseNew = sheetView.findViewById<TextView>(R.id.chooseNewPhoto)
        val removePhoto = sheetView.findViewById<TextView>(R.id.removePhoto)
        chooseNew.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImageLauncher.launch(intent)
            dialog.dismiss()
        }
        removePhoto.setOnClickListener {
            binding.profileDp.setImageResource(R.drawable.ic_default_avatar)
            profileImageBase64 = ""
            selectedImageUri = null
            val uid = firebaseAuth.currentUser?.uid
            if (!uid.isNullOrEmpty()) {
                firestore.collection("Users").document(uid)
                    .update("profileImageBase64", "")
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
        return Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

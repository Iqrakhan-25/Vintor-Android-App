package com.example.vintor

import Post
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.vintor.databinding.FragmentPostBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class PostFragment : Fragment() {
    private var _binding: FragmentPostBinding? = null
    private val binding get() = _binding!!
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val currentUser = auth.currentUser
    private var selectedImageUri: Uri? = null
    private val imagePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                selectedImageUri = result.data!!.data
                selectedImageUri?.let {
                    binding.mediaContainer.visibility = View.VISIBLE
                    binding.mediaPreview.setImageURI(it)
                    binding.mediaPreview.visibility = View.VISIBLE
                }
            }
        }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPostBinding.inflate(inflater, container, false)

        binding.selectMediaBtn.setOnClickListener { pickImage() }
        binding.uploadPostBtn.setOnClickListener { uploadPost() }

        return binding.root
    }
    private fun pickImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }
        imagePicker.launch(intent)
    }
    private fun uploadPost() {
        val caption = binding.captionInput.text.toString().trim()
        val uri = selectedImageUri

        if (caption.isEmpty() && uri == null) {
            Toast.makeText(requireContext(), "Add a caption or select an image", Toast.LENGTH_SHORT).show()
            return
        }
        showLoading(true)
        binding.uploadPostBtn.isEnabled = false
        lifecycleScope.launch(Dispatchers.IO) {
            val imageUrl = if (uri != null) uploadImageToImgBB(uri) else ""
            val userName = currentUser?.displayName ?: "User"
            val profileImage = currentUser?.photoUrl?.toString() ?: ""

            val postId = firestore.collection("Posts").document().id
            val newPost = Post(
                id = postId,
                userId = currentUser?.uid ?: "",
                content = caption,
                imageUrl = imageUrl,
                userName = userName,
                profileImage = profileImage,
                likedBy = mutableListOf(),
                timestamp = Timestamp.now()
            )
            try {
                firestore.collection("Posts").document(postId).set(newPost).await()
                withContext(Dispatchers.Main) {
                    resetUI()
                    Toast.makeText(requireContext(), "Post uploaded!", Toast.LENGTH_SHORT).show()
                    // Notify both Home and Profile fragments to refresh
                    requireActivity().supportFragmentManager.setFragmentResult("REFRESH_HOME", Bundle())
                    requireActivity().supportFragmentManager.setFragmentResult("REFRESH_PROFILE", Bundle())
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to upload post", Toast.LENGTH_SHORT).show()
                    binding.uploadPostBtn.isEnabled = true
                }
            } finally {
                withContext(Dispatchers.Main) { showLoading(false) }
            }
        }
    }
    private fun uploadImageToImgBB(uri: Uri): String {
        return try {
            val apiKey = "2841feea1d89b556ef3d120fe765ae4c"
            val client = OkHttpClient()
            val file = copyUriToTempFile(uri) ?: return ""
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", file.name, file.asRequestBody("image/*".toMediaTypeOrNull()))
                .build()
            val request = Request.Builder()
                .url("https://api.imgbb.com/1/upload?key=$apiKey")
                .post(requestBody)
                .build()
            client.newCall(request).execute().use { response ->
                val json = JSONObject(response.body?.string() ?: "{}")
                json.optJSONObject("data")?.optString("url") ?: ""
            }
        } catch (_: Exception) {
            ""
        }
    }
    private fun copyUriToTempFile(uri: Uri): File? {
        return try {
            val input: InputStream = requireContext().contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(requireContext().cacheDir, "upload_${System.currentTimeMillis()}")
            val output = FileOutputStream(tempFile)
            input.copyTo(output)
            input.close()
            output.close()
            tempFile
        } catch (_: Exception) {
            null
        }
    }
    private fun resetUI() {
        binding.captionInput.text?.clear()
        binding.mediaContainer.visibility = View.GONE
        binding.mediaPreview.setImageURI(null)
        selectedImageUri = null
        binding.uploadPostBtn.isEnabled = true
    }
    private fun showLoading(show: Boolean) {
        binding.uploadProgress.visibility = if (show) View.VISIBLE else View.GONE
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.example.vintor
import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import android.widget.PopupWindow
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class Signup : AppCompatActivity() {

    private lateinit var fullNameET: TextInputEditText
    private lateinit var emailET: TextInputEditText
    private lateinit var phoneET: TextInputEditText
    private lateinit var passwordET: TextInputEditText
    private lateinit var confirmPasswordET: TextInputEditText
    private lateinit var signupBtn: MaterialButton
    private lateinit var loginRedirect: TextView
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var confirmPasswordLayout: TextInputLayout
    private lateinit var emailLayout: TextInputLayout
    private lateinit var phoneLayout: TextInputLayout
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var loadingDialog: Dialog
    private val firestore = FirebaseFirestore.getInstance()
    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false

    private val purpleFocus by lazy { Color.parseColor("#6A1B9A") }
    private val grayDefault by lazy { Color.parseColor("#BDBDBD") }
    private val redError by lazy { Color.parseColor("#E53935") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)
        fullNameET = findViewById(R.id.fullNameET)
        emailET = findViewById(R.id.emailET)
        phoneET = findViewById(R.id.phoneET)
        passwordET = findViewById(R.id.passwordET)
        confirmPasswordET = findViewById(R.id.confirmPasswordET)
        signupBtn = findViewById(R.id.signupButton)
        loginRedirect = findViewById(R.id.loginLink)
        passwordLayout = findViewById(R.id.passwordLayout)
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout)
        emailLayout = findViewById(R.id.emailLayout)
        phoneLayout = findViewById(R.id.phoneLayout)
        firebaseAuth = FirebaseAuth.getInstance()
        setupLoadingDialog()
        setupPasswordToggles()
        setFocusAndValidation(emailLayout)
        setFocusAndValidation(phoneLayout)
        setFocusAndValidation(passwordLayout)
        setFocusAndValidation(confirmPasswordLayout)
        signupBtn.setOnClickListener { registerUser() }
        loginRedirect.setOnClickListener {
            startActivity(Intent(this, Login::class.java))
            finish()
        }
    }
    private fun setupLoadingDialog() {
        loadingDialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_loading, null)
        loadingDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        loadingDialog.setContentView(view)
        loadingDialog.setCancelable(false)
        loadingDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width = (resources.displayMetrics.widthPixels * 0.85).toInt()
        loadingDialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
    }
    private fun setupPasswordToggles() {
        passwordLayout.setEndIconOnClickListener {
            isPasswordVisible = !isPasswordVisible
            passwordET.inputType = if (isPasswordVisible)
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            passwordET.setSelection(passwordET.text?.length ?: 0)

            passwordLayout.endIconDrawable = ContextCompat.getDrawable(
                this,
                if (isPasswordVisible) R.drawable.eye_open else R.drawable.eye_close
            )
        }
        confirmPasswordLayout.setEndIconOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            confirmPasswordET.inputType = if (isConfirmPasswordVisible)
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            confirmPasswordET.setSelection(confirmPasswordET.text?.length ?: 0)

            confirmPasswordLayout.endIconDrawable = ContextCompat.getDrawable(
                this,
                if (isConfirmPasswordVisible) R.drawable.eye_open else R.drawable.eye_close
            )
        }
    }
    private fun showFloatingError(anchorView: View, message: String) {
        val popupView = LayoutInflater.from(this).inflate(R.layout.error_popup, null)
        val errorText = popupView.findViewById<TextView>(R.id.errorText)
        errorText.text = message
        if (anchorView.id == R.id.passwordET || anchorView.id == R.id.confirmPasswordET) {
            errorText.append("\nTap here for a strong password")
            errorText.setOnClickListener {
                val suggested = generateStrongPassword()
                passwordET.setText(suggested)
                confirmPasswordET.setText(suggested)
                Toast.makeText(this, "Strong password auto-filled!", Toast.LENGTH_SHORT).show()
            }
        }
        when (anchorView.id) {
            R.id.emailET -> emailLayout
            R.id.phoneET -> phoneLayout
            R.id.passwordET -> passwordLayout
            R.id.confirmPasswordET -> confirmPasswordLayout
            else -> null
        }?.let { layout ->
            layout.boxStrokeColor = redError
            layout.hintTextColor = ColorStateList.valueOf(redError)
        }
        val popupWindow = PopupWindow(
            popupView,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.isOutsideTouchable = true
        popupWindow.isFocusable = false
        popupWindow.elevation = 8f
        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupWidth = popupView.measuredWidth
        val anchorWidth = anchorView.width
        val offsetX = anchorWidth - popupWidth
        val offsetY = 8
        popupWindow.showAsDropDown(anchorView, offsetX, offsetY)
        popupView.postDelayed({ popupWindow.dismiss() }, 30000)
    }
    private fun generateStrongPassword(): String {
        val upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val lower = "abcdefghijklmnopqrstuvwxyz"
        val digits = "0123456789"
        val symbols = "@#\$%^&+="
        val allChars = upper + lower + digits + symbols
        val passwordChars = mutableListOf(
            upper.random(),
            lower.random(),
            digits.random(),
            symbols.random()
        )
        repeat(8) { passwordChars.add(allChars.random()) }
        passwordChars.shuffle()
        return passwordChars.joinToString("")
    }
    private fun setFocusAndValidation(layout: TextInputLayout) {
        val editText = layout.editText ?: return
        editText.setOnFocusChangeListener { _, hasFocus ->
            layout.boxStrokeColor = if (hasFocus) purpleFocus else grayDefault
            layout.hintTextColor = ColorStateList.valueOf(if (hasFocus) purpleFocus else grayDefault)
        }
        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                layout.boxStrokeColor = if (editText.hasFocus()) purpleFocus else grayDefault
                layout.hintTextColor = ColorStateList.valueOf(
                    if (editText.hasFocus()) purpleFocus else grayDefault
                )
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }
    private fun registerUser() {
        val fullName = fullNameET.text.toString().trim()
        val email = emailET.text.toString().trim()
        val phone = phoneET.text.toString().trim()
        val password = passwordET.text.toString().trim()
        val confirmPassword = confirmPasswordET.text.toString().trim()
        if (fullName.isEmpty() || email.isEmpty() || phone.isEmpty()
            || password.isEmpty() || confirmPassword.isEmpty()
        ) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailET.requestFocus()
            showFloatingError(emailET, "Invalid email")
            return
        }
        if (!phone.matches(Regex("^0\\d{10}$"))) {
            phoneET.requestFocus()
            showFloatingError(phoneET, "Enter 11-digit number")
            return
        }
        if (password.length < 6 ||
            !password.matches(Regex(".*[A-Z].*")) ||
            !password.matches(Regex(".*\\d.*")) ||
            !password.matches(Regex(".*[@#\$%^&+=].*"))
        ) {
            passwordET.requestFocus()
            showFloatingError(passwordET, "Use uppercase, number & symbol")
            return
        }
        if (password != confirmPassword) {
            confirmPasswordET.requestFocus()
            showFloatingError(confirmPasswordET, "Passwords do not match")
            return
        }
        loadingDialog.show()
        firebaseAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                loadingDialog.dismiss()
                if (task.isSuccessful) {
                    val uid = firebaseAuth.currentUser?.uid ?: return@addOnCompleteListener
                    val userMap = hashMapOf(
                        "fullName" to fullName,
                        "email" to email,
                        "phone" to phone,
                        "bio" to "",
                        "city" to "",
                        "dob" to "",
                        "profileImageBase64" to "",
                        "uid" to uid
                    )
                    firestore.collection("Users").document(uid)
                        .set(userMap)
                        .addOnSuccessListener {
                            firebaseAuth.currentUser?.sendEmailVerification()
                            Toast.makeText(
                                this,
                                "Account created & data saved! Please verify your email.",
                                Toast.LENGTH_LONG
                            ).show()

                            firebaseAuth.signOut()
                            startActivity(Intent(this, Login::class.java))
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                this,
                                "Signup success but Firestore save failed: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                } else {
                    Toast.makeText(
                        this,
                        "Signup failed: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }
}

package com.example.vintor
import android.content.Context
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
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth

class Login : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var emailET: TextInputEditText
    private lateinit var passwordET: TextInputEditText
    private lateinit var emailLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var loginButton: MaterialButton
    private lateinit var signupLink: TextView
    private lateinit var forgotPassword: TextView
    private lateinit var rememberMeCheckbox: CheckBox
    private lateinit var loadingDialog: AlertDialog
    private var isPasswordVisible = false
    private var currentPopup: PopupWindow? = null
    // SharedPreferences keys
    private val PREF_NAME = "loginPrefs"
    private val KEY_EMAIL = "email"
    private val KEY_PASSWORD = "password"
    private val KEY_REMEMBER = "remember"
    private val purpleFocus by lazy { Color.parseColor("#6A1B9A") }
    private val grayDefault by lazy { Color.parseColor("#BDBDBD") }
    private val redError by lazy { Color.parseColor("#E53935") }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        auth = FirebaseAuth.getInstance()
        setupLoadingDialog()
        // Bind views
        emailET = findViewById(R.id.UsernameET)
        passwordET = findViewById(R.id.PasswordET)
        emailLayout = findViewById(R.id.usernameLayout)
        passwordLayout = findViewById(R.id.passwordLayout)
        loginButton = findViewById(R.id.loginButton)
        signupLink = findViewById(R.id.signupLink)
        forgotPassword = findViewById(R.id.forgotPassword)
        rememberMeCheckbox = findViewById(R.id.rememberMeCheckbox)

        loadLoginPreferences()
        setFocusEffect(emailLayout)
        setFocusEffect(passwordLayout)

        // Reset colors while typing
        addResetWatcher(emailET, emailLayout)
        addResetWatcher(passwordET, passwordLayout)

        // Password eye toggle
        passwordLayout.setEndIconOnClickListener {
            isPasswordVisible = !isPasswordVisible
            passwordET.inputType = if (isPasswordVisible)
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            passwordET.setSelection(passwordET.text?.length ?: 0)
            passwordLayout.endIconDrawable = getDrawable(
                if (isPasswordVisible) R.drawable.eye_open else R.drawable.eye_close
            )
        }
        loginButton.setOnClickListener { loginUser() }
        signupLink.setOnClickListener {
            startActivity(Intent(this, Signup::class.java))
            finish()
        }
        forgotPassword.setOnClickListener {
            startActivity(Intent(this, Forgot::class.java))
        }
    }
    private fun setupLoadingDialog() {
        loadingDialog = AlertDialog.Builder(this)
            .setView(R.layout.dialog_loading)
            .setCancelable(false)
            .create()
    }
    private fun loginUser() {
        val email = emailET.text.toString().trim()
        val password = passwordET.text.toString().trim()
        // 1️⃣ Validation
        when {
            email.isEmpty() -> {
                showFloatingError(emailET, emailLayout, "Email required")
                return
            }
            password.isEmpty() -> {
                showFloatingError(passwordET, passwordLayout, "Password required")
                return
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                showFloatingError(emailET, emailLayout, "Invalid email format")
                return
            }
        }
        //  Firebase login
        loadingDialog.show()
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                loadingDialog.dismiss()
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null && user.isEmailVerified) {
                        saveLoginPreferences(email, password, rememberMeCheckbox.isChecked)
                        Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, DashBoard::class.java))
                        finish()
                    } else {
                        showFloatingError(emailET, emailLayout, "Please verify your email")
                    }
                } else {
                    Toast.makeText(this, "Invalid credentials. Please try again!", Toast.LENGTH_SHORT).show()
                    emailET.text?.clear()
                    passwordET.text?.clear()
                    emailET.clearFocus()
                    passwordET.clearFocus()
                }
            }
    }
    private fun showFloatingError(
        anchorView: View,
        layout: TextInputLayout,
        message: String
    ) {
        currentPopup?.dismiss()
        val popupView = LayoutInflater.from(this).inflate(R.layout.error_popup, null)
        popupView.findViewById<TextView>(R.id.errorText).text = message
        layout.boxStrokeColor = redError
        layout.hintTextColor = ColorStateList.valueOf(redError)
        val popupWindow = PopupWindow(
            popupView,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.isOutsideTouchable = true
        popupWindow.isFocusable = false
        popupWindow.elevation = 8f
        // Right alignment
        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupWidth = popupView.measuredWidth
        val offsetX = anchorView.width - popupWidth
        val offsetY = 8
        popupWindow.showAsDropDown(anchorView, offsetX, offsetY)
        currentPopup = popupWindow
        popupView.postDelayed({ popupWindow.dismiss() }, 30000)
    }
    private fun addResetWatcher(editText: TextInputEditText, layout: TextInputLayout) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val color = if (editText.hasFocus()) purpleFocus else grayDefault
                layout.boxStrokeColor = color
                layout.hintTextColor = ColorStateList.valueOf(color)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }
    private fun setFocusEffect(layout: TextInputLayout) {
        val editText = layout.editText ?: return
        editText.setOnFocusChangeListener { _, hasFocus ->
            val color = if (hasFocus) purpleFocus else grayDefault
            layout.boxStrokeColor = color
            layout.hintTextColor = ColorStateList.valueOf(color)
        }
    }
    private fun saveLoginPreferences(email: String, password: String, remember: Boolean) {
        val sharedPref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
        if (remember) {
            sharedPref.putString(KEY_EMAIL, email)
            sharedPref.putString(KEY_PASSWORD, password)
            sharedPref.putBoolean(KEY_REMEMBER, true)
        } else sharedPref.clear()
        sharedPref.apply()
    }
    private fun loadLoginPreferences() {
        val sharedPref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (sharedPref.getBoolean(KEY_REMEMBER, false)) {
            emailET.setText(sharedPref.getString(KEY_EMAIL, ""))
            passwordET.setText(sharedPref.getString(KEY_PASSWORD, ""))
            rememberMeCheckbox.isChecked = true
        }
    }
}

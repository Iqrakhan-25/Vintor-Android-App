package com.example.vintor
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
class Forgot : AppCompatActivity() {

    private lateinit var emailET: TextInputEditText
    private lateinit var emailLayout: TextInputLayout
    private lateinit var sendResetBtn: MaterialButton
    private lateinit var backToLogin: TextView
    private lateinit var auth: FirebaseAuth
    private var currentPopup: PopupWindow? = null
    private val purpleFocus by lazy { Color.parseColor("#6A1B9A") }  // Focus color
    private val grayDefault by lazy { Color.parseColor("#BDBDBD") }  // Default color
    private val redError by lazy { Color.parseColor("#E53935") }     // Error color

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot)
        auth = FirebaseAuth.getInstance()
        emailET = findViewById(R.id.emailET)
        emailLayout = findViewById(R.id.emailLayout)
        sendResetBtn = findViewById(R.id.sendResetBtn)
        backToLogin = findViewById(R.id.backToLogin)
        emailLayout.boxStrokeColor = grayDefault
        emailLayout.hintTextColor = ColorStateList.valueOf(grayDefault)
        emailET.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                emailLayout.boxStrokeColor = purpleFocus
                emailLayout.hintTextColor = ColorStateList.valueOf(purpleFocus)
            } else {
                emailLayout.boxStrokeColor = grayDefault
                emailLayout.hintTextColor = ColorStateList.valueOf(grayDefault)
            }
        }
        sendResetBtn.setOnClickListener {
            val email = emailET.text.toString().trim()
            currentPopup?.dismiss()
            // Validate only on button click
            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showFloatingError(emailET, emailLayout, "Enter a valid email")
                emailET.requestFocus()
                return@setOnClickListener
            }
            sendResetBtn.isEnabled = false
            sendResetBtn.text = "Sending..."
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    Toast.makeText(this, "Reset link sent! Check your email.", Toast.LENGTH_SHORT).show()
                    sendResetBtn.text = "Send Reset Link"
                    sendResetBtn.isEnabled = true
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_LONG).show()
                    sendResetBtn.text = "Send Reset Link"
                    sendResetBtn.isEnabled = true
                } }
        backToLogin.setOnClickListener {
            startActivity(Intent(this, Login::class.java))
            finish()
        }
    }
    //  Floating Popup + Red Highlight
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
        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupWidth = popupView.measuredWidth
        val offsetX = anchorView.width - popupWidth
        val offsetY = 8
        popupWindow.showAsDropDown(anchorView, offsetX, offsetY)
        currentPopup = popupWindow
        popupView.postDelayed({ popupWindow.dismiss() }, 30000)
    }
}

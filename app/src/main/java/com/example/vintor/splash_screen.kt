package com.example.vintor

import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class splash_screen : AppCompatActivity() {

    private lateinit var loadingText: TextView
    private var dotCount = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        loadingText = findViewById(R.id.loadingDots)
        val textShader: Shader = LinearGradient(
            0f, 0f, 300f, 0f,
            intArrayOf(
                0xFFE1306C.toInt(), // pink
                0xFFFD1D1D.toInt(), // red
                0xFFF77737.toInt(), // orange
                0xFF833AB4.toInt()  // purple
            ),
            null,
            Shader.TileMode.CLAMP
        )
        loadingText.paint.shader = textShader
        // Animate the dots
        val dotHandler = Handler(Looper.getMainLooper())
        dotHandler.post(object : Runnable {
            override fun run() {
                loadingText.text = ".".repeat(dotCount)
                dotCount = if (dotCount <4) dotCount + 1 else 1
                dotHandler.postDelayed(this, 300)
            }
        })
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this@splash_screen, Login::class.java))
            finish()
        }, 3000)
    }
}

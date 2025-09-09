package com.example.vintor
import android.content.Intent
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Bundle
import android.widget.TextView
import android.view.ViewGroup
import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.example.vintor.fragments.ChatFragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
class DashBoard : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private var activeFragment: Fragment? = null
    // Cached fragments
    private val homeFragment = HomeFragment()
    private val chatFragment = ChatFragment()
    private val postFragment = PostFragment()
    private val notificationFragment = NotificationFragment()
    private val profileFragment = ProfileFragment()
    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dash_board)
        bottomNav = findViewById(R.id.bottomNavigationView)
        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        val appNameText = findViewById<TextView>(R.id.appNameText)
        appNameText.post {
            val textShader = LinearGradient(
                0f, 0f, 0f, appNameText.textSize,
                intArrayOf(
                    Color.parseColor("#6A1B9A"),
                    Color.parseColor("#8548B4"),
                    Color.parseColor("#DD2A7B"),
                    Color.parseColor("#C83B85"),
                    Color.parseColor("#DD2A7B"),
                ),
                null,
                Shader.TileMode.CLAMP
            )
            appNameText.paint.shader = textShader
            appNameText.invalidate()
        }
        // Add all fragments initially
        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, profileFragment, "Profile").hide(profileFragment)
            .add(R.id.fragment_container, notificationFragment, "Notification").hide(notificationFragment)
            .add(R.id.fragment_container, postFragment, "Post").hide(postFragment)
            .add(R.id.fragment_container, chatFragment, "Chat").hide(chatFragment)
            .add(R.id.fragment_container, homeFragment, "Home")
            .commit()
        activeFragment = homeFragment
        // Open chat fragment directly if passed from intent
        val openFragment = intent.getStringExtra("open_fragment")
        if (openFragment == "chat") {
            openChatFragmentFromDashboard()
        } else {
            bottomNav.selectedItemId = R.id.nav_home
            switchFragment(homeFragment)
        }
        setupBottomNavigationListener()
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.opt_profile -> {
                    openProfileFragmentFromHome()
                    true
                }
                R.id.opt_about -> {
                    showAboutDialog()
                    true
                }
                R.id.opt_logout -> {
                    showLogoutConfirmation()
                    true
                }
                else -> false
            }
        }
    }
    override fun onResume() {
        super.onResume()
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userDocRef = FirebaseFirestore.getInstance().collection("Users").document(currentUserId)
        // Mark user as online and update lastActive timestamp
        val updates = mapOf(
            "chatActivityOpen" to true, "lastActive" to System.currentTimeMillis()
        )
        userDocRef.update(updates)
    }
    override fun onPause() {
        super.onPause()
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userDocRef = FirebaseFirestore.getInstance().collection("Users").document(currentUserId)
        // Mark user offline but update lastActive timestamp
        val updates = mapOf(
            "chatActivityOpen" to false,
            "lastActive" to System.currentTimeMillis()
        )
        userDocRef.update(updates)
    }
    private fun setupBottomNavigationListener() {
        bottomNav.setOnItemSelectedListener { item ->
            val targetFragment = when (item.itemId) {
                R.id.nav_home -> homeFragment
                R.id.nav_chat -> chatFragment
                R.id.nav_post -> postFragment
                R.id.nav_notification -> notificationFragment
                R.id.nav_profile -> profileFragment
                else -> homeFragment
            }
            switchFragment(targetFragment)
            true
        }
    }
    fun openProfileFragmentFromHome() {
        bottomNav.selectedItemId = R.id.nav_profile
        switchFragment(profileFragment)
    }
    fun openChatFragmentFromDashboard() {
        bottomNav.selectedItemId = R.id.nav_chat
        switchFragment(chatFragment)
    }
    // Safe switching (no crash on null)
    private fun switchFragment(targetFragment: Fragment) {
        if (activeFragment == targetFragment) return
        val transaction = supportFragmentManager.beginTransaction()
        activeFragment?.let { transaction.hide(it) }
        transaction.show(targetFragment).commit()
        activeFragment = targetFragment
    }
    private fun showAboutDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("About Vintor")
            .setMessage(
                "Vintor is your all-in-one social space where you can post your moments, chat instantly, " +
                        "and connect with people that matter.\nDeveloped by IQRA Khan"
            )
            .setPositiveButton("OK", null)
            .create()

        dialog.show()

        // Small width, centered
        val width = (resources.displayMetrics.widthPixels * 0.85).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_dialog)
    }
    private fun showLogoutConfirmation() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                startActivity(Intent(this, Login::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        // Small width, centered
        val width = (resources.displayMetrics.widthPixels * 0.8).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_dialog)
    }

}

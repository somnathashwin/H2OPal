package com.lended.h2opal.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.lended.h2opal.R
import com.lended.h2opal.adapters.FragmentPagerAdapter
import com.lended.h2opal.databinding.ActivityMainBinding
import com.lended.h2opal.fragments.HomeFragment
import com.lended.h2opal.fragments.ProfileFragment
import com.lended.h2opal.fragments.RemainderFragment
import com.lended.h2opal.helpers.HydrationAlarmScheduler
import com.lended.h2opal.helpers.HydrationLogWorker
import com.lended.h2opal.helpers.HydrationManager
import java.util.concurrent.TimeUnit
import androidx.core.content.edit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val selectedColor by lazy { ContextCompat.getColor(this, R.color.tab_selected) }
    private val defaultColor by lazy { ContextCompat.getColor(this, R.color.tab_unselected) }
    private var userId: String = "default_user"

    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001

    private var lastSyncTime = 0L
    private val syncInterval = 5000L // 5 seconds between syncs
    private val DEBUG = false // Set to true for debug logging

    private companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Set the default night mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        super.onCreate(savedInstanceState)

        // Check if it's the first time the app is launched
        val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isFirstTime = sharedPref.getBoolean("is_first_time", true)

        if (isFirstTime) {
            // Mark that the app has been launched
            sharedPref.edit().putBoolean("is_first_time", false).apply()
            // Launch WelcomeActivity
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }

        // Check if the user is signed in
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account == null) {
            // Launch SigninGooglePage if not signed in
            startActivity(Intent(this, SigninGooglePage::class.java))
            finish()
            return
        }

        // Normal MainActivity setup
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Extract a user ID (either ID or email)
        userId = account.id ?: account.email ?: "default_user"
        Log.d(TAG, "User ID: $userId")

        // Save userId for background receivers/workers
        getSharedPreferences("hydration_prefs", Context.MODE_PRIVATE).edit {
            putString("last_user_id", userId)
        }

        // Initialize HydrationManager
        HydrationManager.initialize(applicationContext, userId)

        // Create fragments with proper user ID
        val fragments = listOf(
            HomeFragment(),
            RemainderFragment.newInstance(userId),
            ProfileFragment()
        )

        val adapter = FragmentPagerAdapter(this, fragments)
        binding.mainViewPager.adapter = adapter
        binding.mainViewPager.isUserInputEnabled = false
        binding.mainViewPager.currentItem = 0
        highlightTab(0)

        binding.homeBtn.setOnClickListener {
            triggerTabClick(0)
        }

        binding.remainderBtn.setOnClickListener {
            triggerTabClick(1)
        }

        binding.profileBtn.setOnClickListener {
            triggerTabClick(2)
        }

        HydrationAlarmScheduler.scheduleRepeatingAlarm(applicationContext)
        scheduleHydrationLogging(applicationContext)
        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        if (DEBUG) Log.d(TAG, "onResume")
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSyncTime > syncInterval) {
            lastSyncTime = currentTime
            
            // Ensure hydration data is synchronized when app resumes
            val account = GoogleSignIn.getLastSignedInAccount(this)
            val currentUserId = account?.id ?: account?.email ?: "default_user"
            
            // Update user ID if it changed
            if (currentUserId != userId) {
                userId = currentUserId
                if (DEBUG) Log.d(TAG, "User ID changed to: $userId")
                getSharedPreferences("hydration_prefs", Context.MODE_PRIVATE).edit {
                    putString("last_user_id", userId)
                }
            }
            
            HydrationManager.initialize(applicationContext, userId)
            // Use optimized synchronization to ensure all fragments have identical data
            HydrationManager.synchronizeIfNeeded()
            
            // Refresh current fragment
            refreshCurrentFragment()
        }
    }

    private fun refreshCurrentFragment() {
        val currentFragment = supportFragmentManager.fragments.getOrNull(binding.mainViewPager.currentItem)
        if (currentFragment is HomeFragment || currentFragment is ProfileFragment || currentFragment is RemainderFragment) {
            // The fragment's onResume will handle the refresh
            if (DEBUG) Log.d(TAG, "Current fragment will refresh in onResume")
        }
    }

    /** Test method to verify hydration synchronization across fragments */
    private fun testHydrationSynchronization() {
        if (DEBUG) Log.d(TAG, "Testing unified hydration synchronization...")
        
        // Use optimized synchronization
        HydrationManager.synchronizeIfNeeded()
        
        // Verify synchronization
        val isSynchronized = HydrationManager.verifyFragmentSynchronization()
        
        // Log current unified values
        val percent = HydrationManager.getUnifiedHydrationPercentage()
        val status = HydrationManager.getUnifiedHydrationStatus()
        val mood = HydrationManager.getUnifiedPalMood()
        val intake = HydrationManager.totalWaterIntake.value ?: 0.0
        val goal = HydrationManager.hydrationGoal.value ?: 3.0
        
        if (DEBUG) {
            Log.d(TAG, "Unified hydration data - Percent: $percent%, Status: $status, Mood: $mood, Intake: $intake, Goal: $goal")
            Log.d(TAG, "All fragments synchronized: $isSynchronized")
        }
        
        // Notify all fragments
        refreshCurrentFragment()
    }

    private fun triggerTabClick(index: Int) {
        binding.root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        animateClick(index)
        binding.mainViewPager.currentItem = index
        highlightTab(index)
    }

    private fun animateClick(index: Int) {
        val buttons = listOf(binding.homeBtn, binding.remainderBtn, binding.profileBtn)
        buttons[index].animate().scaleX(1.2f).scaleY(1.2f).setDuration(100).withEndAction {
            buttons[index].animate().scaleX(1f).scaleY(1f).duration = 100
        }.start()
    }

    private fun highlightTab(selectedIndex: Int) {
        val icons = listOf(binding.homeBtn, binding.remainderBtn, binding.profileBtn)
        val iconIds = listOf(
            R.drawable.ic_home,
            R.drawable.ic_reminders,
            R.drawable.ic_profile
        )
        val selectedIconIds = listOf(
            R.drawable.ic_home,
            R.drawable.ic_reminders,
            R.drawable.ic_profile
        )

        for (i in icons.indices) {
            icons[i].setImageResource(if (i == selectedIndex) selectedIconIds[i] else iconIds[i])
            icons[i].alpha = if (i == selectedIndex) 1f else 0.5f
        }
    }

    private fun scheduleHydrationLogging(context: Context) {
        val request = PeriodicWorkRequestBuilder<HydrationLogWorker>(30, TimeUnit.MINUTES)
            .setInitialDelay(10, TimeUnit.SECONDS)
            .addTag("hydration_log_worker")
            .build()
    
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "hydration_log_task",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }
}

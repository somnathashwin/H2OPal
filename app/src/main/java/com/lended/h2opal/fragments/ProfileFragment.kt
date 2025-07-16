package com.lended.h2opal.fragments

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.lended.h2opal.R
import com.lended.h2opal.helpers.SubscriptionPopupHelper
import com.lended.h2opal.utils.ToastCustom
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.lended.h2opal.activities.SigninGooglePage
import com.lended.h2opal.helpers.HydrationManager
import com.lended.h2opal.helpers.HydrationManager.restoreFromDriveBackup
import com.lended.h2opal.helpers.HydrationManager.saveToDriveBackup

class ProfileFragment : Fragment() {

    private var chartRefreshHandler: android.os.Handler? = null
    private var chartRefreshRunnable: Runnable? = null
    private var userId: String = "default_user"
    private lateinit var chart: com.github.mikephil.charting.charts.LineChart
    private var lastChartUpdate = 0L
    private val chartUpdateInterval = 30000L // 30 seconds between chart updates
    private val DEBUG = false // Set to true for debug logging
    private var lastChartData = ""
    private var lastHistorySize = 0

    private companion object {
        private const val TAG = "ProfileFragment"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        
        // Get user ID consistently
        userId = getUserId()
        
        // Initialize HydrationManager early
        HydrationManager.initialize(requireContext(), userId)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.profile_fragment, container, false)

        // Initialize HydrationManager with the correct userId before using it
        val userId = getUserId()
        HydrationManager.initialize(requireContext(), userId)

        val subscriptionButton = view.findViewById<LinearLayout>(R.id.goldenWaterBtn)
        val userName = view.findViewById<TextView>(R.id.userName)

        // Get the signed-in user's info
        val googleSignInAccount = GoogleSignIn.getLastSignedInAccount(requireContext())
        googleSignInAccount?.let { account ->
            userName.text = account.displayName // Set the username from Google account
        } ?: run {
            userName.text = "Guest" // Default text if no user is signed in
        }

        val optionsBtn = view.findViewById<ImageButton>(R.id.menuBtn)
        optionsBtn.setOnClickListener {
            showOptionsPopup(it)
        }

        subscriptionButton.setOnClickListener {
            animateClick(subscriptionButton)
            SubscriptionPopupHelper(requireContext()).show()
        }

        // Set fullscreen mode and translucent navbar
        requireActivity().window?.decorView?.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                )

        // Making the status bar hidden and navigation bar translucent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requireActivity().window?.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            requireActivity().window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            requireActivity().window?.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            // Make the navigation bar translucent
            requireActivity().window?.setNavigationBarColor(0x80000000.toInt()) // 50% transparency
        }

        chart = view.findViewById<com.github.mikephil.charting.charts.LineChart>(R.id.graphViewBasic)
        setupHydrationChart(chart)

        // Start periodic chart refresh
        chartRefreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
        chartRefreshRunnable = object : Runnable {
            override fun run() {
                setupHydrationChart(chart)
                chartRefreshHandler?.postDelayed(this, chartUpdateInterval)
            }
        }
        chartRefreshHandler?.post(chartRefreshRunnable!!)

        // Set up observers for hydration data changes
        setupHydrationObservers()

        return view
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        // Use unified synchronization to ensure all fragments have identical data
        HydrationManager.synchronizeAllFragments()
        setupHydrationChart(chart)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Stop periodic chart refresh
        chartRefreshHandler?.removeCallbacks(chartRefreshRunnable!!)
        chartRefreshHandler = null
        chartRefreshRunnable = null
    }

    private fun getUserId(): String {
        // First try to get from Google Sign In
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        val googleUserId = account?.id ?: account?.email
        
        // Fallback to SharedPreferences
        val prefs = requireContext().getSharedPreferences("hydration_prefs", Context.MODE_PRIVATE)
        val savedUserId = prefs.getString("last_user_id", "default")
        
        return googleUserId ?: savedUserId ?: "default"
    }

    private fun showOptionsPopup(anchorView: View) {
        val popupView = LayoutInflater.from(requireContext()).inflate(R.layout.popup, null)

        val themeBtn = popupView.findViewById<TextView>(R.id.themeBtn)
        val resetBtn = popupView.findViewById<TextView>(R.id.resetBtn)
        val logoutBtn = popupView.findViewById<TextView>(R.id.logoutBtn)
        val saveData = popupView.findViewById<TextView>(R.id.saveData)
        val retrieveData = popupView.findViewById<TextView>(R.id.retrieveData)

        // Convert 200dp to pixels
        val popupWidth = (200 * resources.displayMetrics.density).toInt()
        val popupHeight = ViewGroup.LayoutParams.WRAP_CONTENT

        val popupWindow = PopupWindow(popupView, popupWidth, popupHeight, true)

        popupWindow.setBackgroundDrawable(null)
        popupWindow.isOutsideTouchable = true
        popupWindow.elevation = 10f

        // Margin from screen edge (e.g., 16dp)
        val marginEnd = (16 * resources.displayMetrics.density).toInt()

        // Get screen width
        val screenWidth = resources.displayMetrics.widthPixels

        // Get anchor view position
        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)
        val anchorX = location[0]

        // Calculate offsetX to prevent sticking to screen edge
        val offsetX = if (anchorX + popupWidth + marginEnd > screenWidth) {
            screenWidth - anchorX - popupWidth - marginEnd
        } else {
            0
        }

        // Show popup with calculated horizontal offset
        popupWindow.showAsDropDown(anchorView, offsetX, 10)

        // Button listeners
        themeBtn.setOnClickListener {
            popupWindow.dismiss()
            Toast.makeText(requireContext(), "Theme customization coming soon!", Toast.LENGTH_SHORT).show()
        }

        resetBtn.setOnClickListener {
            popupWindow.dismiss()
            saveHydrationData(requireContext())
            showResetConfirmationPopup()
        }

        saveData.setOnClickListener {
            saveHydrationData(requireContext())
            Toast.makeText(requireContext(), "Data saved to drive", Toast.LENGTH_SHORT).show()
        }

        retrieveData.setOnClickListener {
            retrieveHydrationData(requireContext())
            Toast.makeText(requireContext(), "Data retrieved from drive", Toast.LENGTH_SHORT).show()
        }


        logoutBtn.setOnClickListener {
            popupWindow.dismiss()
            signOutAndRedirect()
        }
    }

    private fun signOutAndRedirect() {
        // Save hydration data before logout
        saveHydrationData(requireContext())

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        val googleSignInClient = GoogleSignIn.getClient(requireContext(), gso)

        googleSignInClient.signOut().addOnCompleteListener {
            ToastCustom.showCustomToast(requireContext(), "Logged out successfully", R.drawable.dead_pal)

            val intent = Intent(requireContext(), SigninGooglePage::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    private fun saveHydrationData(context: Context) {
        val userId = getUserId()
        saveToDriveBackup(context, userId)
    }

    private fun retrieveHydrationData(context: Context) {
        val userId = getUserId()
        restoreFromDriveBackup(context, userId)
    }


    private fun animateClick(view: View) {
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    private fun vibrateDevice() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(300)
        }
    }

    private fun showResetConfirmationPopup() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.confirmation_popup, null)

        val cancelBtn = dialogView.findViewById<Button>(R.id.cancelBtn)
        val confirmBtn = dialogView.findViewById<Button>(R.id.confirmBtn)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        cancelBtn.setOnClickListener {
            dialog.dismiss()
        }

        confirmBtn.setOnClickListener {
            HydrationManager.resetHydrationData()
            ToastCustom.showCustomToast(requireContext(), "Hydration data has been reset!", R.drawable.dead_pal)
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        vibrateDevice()
    }

    private fun setupHydrationChart(chart: com.github.mikephil.charting.charts.LineChart) {
        // Use unified synchronization before setting up chart
        HydrationManager.synchronizeIfNeeded()
        
        val rawHistory = HydrationManager.getHydrationHistory()
        
        // Check if chart data has actually changed
        val currentData = rawHistory.toString()
        if (currentData == lastChartData && rawHistory.size == lastHistorySize) {
            return // No changes, skip update
        }
        
        lastChartData = currentData
        lastHistorySize = rawHistory.size
        
        if (DEBUG) {
            android.util.Log.d("ProfileFragment", "Hydration history size: ${rawHistory.size}")
        }
        
        if (rawHistory.isEmpty()) {
            chart.clear()
            chart.invalidate()
            return
        }

        HydrationManager.startAutoLogging()

        val bucketed = rawHistory
            .groupBy { (timestamp, _) -> timestamp / (1 * 1000) } // Change 10*1000 to 1*1000 for 1 second buckets
            .map { (bucket, list) ->
                val last = list.maxByOrNull { it.first }!!
                Pair(bucket * 1 * 1000, last.second)
            }
            .sortedBy { it.first }

        val entries = bucketed.map { (timestamp, percent) ->
            Entry(timestamp.toFloat(), percent.toFloat())
        }
        
        if (DEBUG) {
            android.util.Log.d("ProfileFragment", "Chart entries: ${entries.size}")
        }

        val lineDataSet = LineDataSet(entries, "Hydration %")
        lineDataSet.color = resources.getColor(R.color.teal_700, null)
        lineDataSet.setDrawCircles(true)
        lineDataSet.circleRadius = 4f
        lineDataSet.setCircleColor(resources.getColor(R.color.teal_700, null))
        lineDataSet.lineWidth = 2f
        lineDataSet.valueTextSize = 12f

        val data = LineData(lineDataSet)
        chart.data = data

        chart.axisRight.isEnabled = false
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            private val dateFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            override fun getFormattedValue(value: Float): String {
                return dateFormat.format(java.util.Date(value.toLong()))
            }
        }
        chart.xAxis.granularity = (10 * 60 * 1000).toFloat() // 10 minutes
        chart.xAxis.labelRotationAngle = -45f
        chart.description.text = "Hydration Trend (10sec intervals)"
        chart.animateX(1000)
        chart.invalidate()
        
        // Log current unified data for verification (only in debug)
        if (DEBUG) {
            val currentPercent = HydrationManager.getUnifiedHydrationPercentage()
            val currentStatus = HydrationManager.getUnifiedHydrationStatus()
            val currentMood = HydrationManager.getUnifiedPalMood()
            Log.d(TAG, "Chart updated with unified data - Percent: $currentPercent%, Status: $currentStatus, Mood: $currentMood")
        }
    }

    private fun setupHydrationObservers() {
        // Debounced chart update
        val debouncedChartUpdate = {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastChartUpdate > chartUpdateInterval) {
                lastChartUpdate = currentTime
                setupHydrationChart(chart)
            }
        }

        // Observe hydration data changes to update chart
        HydrationManager.totalWaterIntake.observe(viewLifecycleOwner) { value ->
            if (DEBUG) Log.d(TAG, "Total water intake updated: $value")
            debouncedChartUpdate()
        }
        HydrationManager.hydrationGoal.observe(viewLifecycleOwner) { goal ->
            if (DEBUG) Log.d(TAG, "Hydration goal updated: $goal")
            debouncedChartUpdate()
        }
        HydrationManager.lastHydrationTime.observe(viewLifecycleOwner) { time ->
            if (DEBUG) Log.d(TAG, "Last hydration time updated: $time")
            debouncedChartUpdate()
        }
        // Also observe status and mood changes
        HydrationManager.hydrationStatus.observe(viewLifecycleOwner) { status ->
            if (DEBUG) Log.d(TAG, "Hydration status updated: $status")
        }
        HydrationManager.palMood.observe(viewLifecycleOwner) { mood ->
            if (DEBUG) Log.d(TAG, "Pal mood updated: $mood")
        }
    }

}

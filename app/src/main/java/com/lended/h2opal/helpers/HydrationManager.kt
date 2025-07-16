package com.lended.h2opal.helpers

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.lended.h2opal.models.HydrationData
import java.io.File
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicBoolean

object HydrationManager {

    private const val PREFS_NAME = "hydration_prefs"
    private const val KEY_TOTAL_WATER = "total_water"
    private const val KEY_LAST_HYDRATION = "last_hydration_time"
    private const val KEY_NEXT_HYDRATION = "next_hydration_time"
    private const val KEY_HYDRATION_HISTORY = "hydration_history"
    private const val KEY_USER_ID = "current_user_id"
    private const val KEY_HYDRATION_GOAL = "hydration_goal"
    private const val FILE_NAME = "hydration_backup.json"
    private const val DEBUG = false // Set to true for debug logging

    private lateinit var prefs: SharedPreferences
    private var accountId: String = "default"
    private val gson = Gson()

    // Thread-safe initialization flag
    private val isInitializing = AtomicBoolean(false)
    private var isInitialized = false
    private var currentContext: Context? = null

    var defaultHydrationGoal = 3.0 // Default value, will be updated based on user profile

    // LiveData for hydration metrics and mood/status
    private val _totalWaterIntake = MutableLiveData(0.0)
    private val _lastHydrationTime = MutableLiveData(0L)
    private val _nextHydrationTime = MutableLiveData(0L)
    private val _hydrationStatus = MutableLiveData("")
    private val _palMood = MutableLiveData("")
    private val _hydrationGoal = MutableLiveData(3.0)

    val totalWaterIntake: LiveData<Double> get() = _totalWaterIntake
    val lastHydrationTime: LiveData<Long> get() = _lastHydrationTime
    val nextHydrationTime: LiveData<Long> get() = _nextHydrationTime
    val hydrationStatus: LiveData<String> get() = _hydrationStatus
    val palMood: LiveData<String> get() = _palMood
    val hydrationGoal: LiveData<Double> get() = _hydrationGoal

    // Handler and Runnable for auto-logging and decay
    private var loggingHandler: Handler? = null
    private var loggingRunnable: Runnable? = null
    private var decayHandler: Handler? = null
    private var decayRunnable: Runnable? = null

    /** Initializes hydration manager for the user - should only be called once */
    fun initialize(context: Context, userId: String) {
        if (isInitialized && accountId == userId) {
            Log.d("HydrationManager", "Already initialized for user: $userId")
            return
        }

        // Prevent concurrent initialization
        if (!isInitializing.compareAndSet(false, true)) {
            Log.d("HydrationManager", "Initialization already in progress for user: $userId")
            return
        }

        try {
            Log.d("HydrationManager", "Initializing for user: $userId")
            
            // Cleanup previous instance if different user
            if (isInitialized && accountId != userId) {
                cleanup()
            }
            
            accountId = userId
            currentContext = context.applicationContext
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            
            // Save current user ID
            prefs.edit().putString(KEY_USER_ID, userId).apply()

            // Load hydration goal
            val savedGoal = prefs.getFloat(key(KEY_HYDRATION_GOAL), 3.0f).toDouble()
            _hydrationGoal.postValue(savedGoal)
            defaultHydrationGoal = savedGoal // Keep backward compatibility

            // Load data in the background
            Handler(Looper.getMainLooper()).post {
                _totalWaterIntake.postValue(prefs.getFloat(key(KEY_TOTAL_WATER), 0f).toDouble())
                _lastHydrationTime.postValue(prefs.getLong(key(KEY_LAST_HYDRATION), System.currentTimeMillis() - (60 * 60 * 1000)))
                _nextHydrationTime.postValue(prefs.getLong(key(KEY_NEXT_HYDRATION), System.currentTimeMillis() + (30 * 60 * 1000)))
                updateStatusAndMood()
                
                // Apply decay for time passed since last hydration
                applyDecayForTimePassed()
                
                // Start background processes
                startAutoLogging()
                startDecayScheduler()
            }
            
            isInitialized = true
        } finally {
            isInitializing.set(false)
        }
    }

    /** Gets the current user ID from SharedPreferences */
    fun getCurrentUserId(): String {
        return if (::prefs.isInitialized) {
            prefs.getString(KEY_USER_ID, "default") ?: "default"
        } else {
            "default"
        }
    }

    /** Sets hydration goal and persists it */
    fun setHydrationGoal(goal: Double) {
        if (!::prefs.isInitialized) return
        
        _hydrationGoal.postValue(goal)
        defaultHydrationGoal = goal // Keep backward compatibility
        prefs.edit().putFloat(key(KEY_HYDRATION_GOAL), goal.toFloat()).apply()
        updateStatusAndMood()
        
        // Use unified synchronization to ensure all fragments are updated
        synchronizeAllFragments()
        
        Log.d("HydrationManager", "Hydration goal updated to: ${goal}L")
    }

    /** Gets current hydration goal */
    fun getHydrationGoal(): Double {
        return _hydrationGoal.value ?: 3.0
    }

    /** Applies decay for time passed since last hydration */
    private fun applyDecayForTimePassed() {
        if (!::prefs.isInitialized) return

        val lastHydration = prefs.getLong(key(KEY_LAST_HYDRATION), System.currentTimeMillis())
        val now = System.currentTimeMillis()
        val hoursPassed = (now - lastHydration) / (60 * 60 * 1000)

        if (hoursPassed > 0) {
            val currentIntake = prefs.getFloat(key(KEY_TOTAL_WATER), 0f).toDouble()
            val decayed = BigDecimal(currentIntake)
                .subtract(BigDecimal(0.02 * hoursPassed))
                .coerceAtLeast(BigDecimal.ZERO)
                .toDouble()

            _totalWaterIntake.postValue(decayed)
            prefs.edit().putFloat(key(KEY_TOTAL_WATER), decayed.toFloat()).apply()
            updateStatusAndMood()
            
            Log.d("HydrationManager", "Applied decay for $hoursPassed hours: $currentIntake -> $decayed")
        }
    }

    /** Call this when user drinks water */
    fun hydrateNow(amount: Double = 0.25) {
        if (!::prefs.isInitialized) {
            Log.w("HydrationManager", "Not initialized, cannot hydrate")
            return
        }

        val current = BigDecimal(_totalWaterIntake.value ?: 0.0)
        val newTotal = current.add(BigDecimal(amount)).toDouble()
        val now = System.currentTimeMillis()

        _totalWaterIntake.postValue(newTotal)
        _lastHydrationTime.postValue(now)
        _nextHydrationTime.postValue(now + (2 * 60 * 60 * 1000))

        prefs.edit().apply {
            putFloat(key(KEY_TOTAL_WATER), newTotal.toFloat())
            putLong(key(KEY_LAST_HYDRATION), now)
            putLong(key(KEY_NEXT_HYDRATION), now + (2 * 60 * 60 * 1000))
            apply()
        }

        logHydrationHistory()
        updateStatusAndMood()
        
        // Use unified synchronization to ensure all fragments are updated
        synchronizeAllFragments()
        
        Log.d("HydrationManager", "Hydrated: +${amount}L, Total: ${newTotal}L")
    }

    /** Gradually decays hydration value based on time since last hydration */
    fun decayHydrationOverTime() {
        if (!::prefs.isInitialized) return

        val lastHydration = prefs.getLong(key(KEY_LAST_HYDRATION), System.currentTimeMillis())
        val now = System.currentTimeMillis()
        val hoursPassed = (now - lastHydration) / (60 * 60 * 1000)

        if (hoursPassed <= 0) return

        val currentIntake = prefs.getFloat(key(KEY_TOTAL_WATER), 0f).toDouble()
        val decayed = BigDecimal(currentIntake)
            .subtract(BigDecimal(0.02 * hoursPassed)) // Reduced from 0.05
            .coerceAtLeast(BigDecimal.ZERO)
            .toDouble()

        _totalWaterIntake.postValue(decayed)
        prefs.edit().putFloat(key(KEY_TOTAL_WATER), decayed.toFloat()).apply()

        updateStatusAndMood()
        
        // Use unified synchronization to ensure all fragments are updated
        synchronizeAllFragments()
        
        Log.d("HydrationManager", "Decay applied: $currentIntake -> $decayed (${hoursPassed}h passed)")
    }

    /** Clears all hydration data for current user */
    fun resetHydrationData() {
        if (!::prefs.isInitialized) return

        _totalWaterIntake.postValue(0.0)
        _lastHydrationTime.postValue(0L)
        _nextHydrationTime.postValue(0L)
        _hydrationStatus.postValue("")
        _palMood.postValue("")

        prefs.edit().apply {
            putFloat(key(KEY_TOTAL_WATER), 0f)
            putLong(key(KEY_LAST_HYDRATION), 0L)
            putLong(KEY_NEXT_HYDRATION, 0L)
            putString(key(KEY_HYDRATION_HISTORY), "")
            apply()
        }
        
        // Use unified synchronization to ensure all fragments are updated
        synchronizeAllFragments()
        
        Log.d("HydrationManager", "Hydration data reset for user: $accountId")
    }

    /** Updates hydration status and mood based on water intake percentage */
    private fun updateStatusAndMood() {
        val currentIntake = _totalWaterIntake.value ?: 0.0
        val goal = _hydrationGoal.value ?: 3.0
        val percentage = ((currentIntake / goal) * 100).toInt()

        val status = when {
            percentage >= 90 -> "Happy"
            percentage >= 60 -> "Neutral"
            percentage >= 40 -> "Sad"
            else -> "Angry"
        }

        val mood = when {
            percentage >= 90 -> "Happy"
            percentage >= 50 -> "Neutral"
            percentage >= 40 -> "Sad"
            else -> "Angry"
        }

        // Only update if values have changed
        if (_hydrationStatus.value != status) {
            _hydrationStatus.postValue(status)
        }
        if (_palMood.value != mood) {
            _palMood.postValue(mood)
        }
    }

    /** Returns hydration history as list of (timestamp, percent) */
    fun getHydrationHistory(): List<Pair<Long, Double>> {
        if (!::prefs.isInitialized) return emptyList()
        
        val raw = prefs.getString(key(KEY_HYDRATION_HISTORY), "") ?: ""
        return raw.split(";").mapNotNull {
            val parts = it.split(",")
            if (parts.size == 2) {
                val time = parts[0].toLongOrNull()
                val percent = parts[1].toDoubleOrNull()
                if (time != null && percent != null) Pair(time, percent) else null
            } else null
        }
    }

    /** Logs hydration progress into history */
    fun logHydrationHistory() {
        if (!::prefs.isInitialized) return
        
        val currentIntake = _totalWaterIntake.value ?: prefs.getFloat(key(KEY_TOTAL_WATER), 0f).toDouble()
        val goal = _hydrationGoal.value ?: 3.0
        val percent = ((currentIntake / goal) * 100).coerceAtMost(100.0)
        val timestamp = System.currentTimeMillis()

        val existing = prefs.getString(key(KEY_HYDRATION_HISTORY), "") ?: ""
        val updated = "$existing;$timestamp,$percent".trim(';')

        prefs.edit().putString(key(KEY_HYDRATION_HISTORY), updated).apply()
    }

    /** Returns a friendly hydration reminder string */
    fun getNextHydrationReminder(): String {
        val nextTime = _nextHydrationTime.value ?: return "Hydrate Now!"
        val remaining = nextTime - System.currentTimeMillis()
        return if (remaining <= 0) {
            "Hydrate Now!"
        } else {
            val mins = (remaining / 60000).toInt()
            "Next in $mins min"
        }
    }

    /** Starts logging hydration history every 10 minutes */
    fun startAutoLogging() {
        if (loggingHandler != null) return // Already running

        loggingHandler = Handler(Looper.getMainLooper())
        loggingRunnable = object : Runnable {
            override fun run() {
                logHydrationHistory()
                loggingHandler?.postDelayed(this, 10 * 60 * 1000) // 10 minutes
            }
        }
        loggingHandler?.post(loggingRunnable!!)
        
        Log.d("HydrationManager", "Auto logging started")
    }

    /** Starts decay scheduler that runs every hour */
    fun startDecayScheduler() {
        if (decayHandler != null) return // Already running

        decayHandler = Handler(Looper.getMainLooper())
        decayRunnable = object : Runnable {
            override fun run() {
                decayHydrationOverTime()
                decayHandler?.postDelayed(this, 60 * 60 * 1000) // 1 hour
            }
        }
        decayHandler?.post(decayRunnable!!)
        
        Log.d("HydrationManager", "Decay scheduler started")
    }

    /** Stops all background processes */
    fun cleanup() {
        loggingHandler?.removeCallbacks(loggingRunnable!!)
        decayHandler?.removeCallbacks(decayRunnable!!)
        loggingHandler = null
        loggingRunnable = null
        decayHandler = null
        decayRunnable = null
        isInitialized = false
        
        Log.d("HydrationManager", "Cleanup completed")
    }

    /** Creates a key with user ID prefix for SharedPreferences */
    private fun key(baseKey: String): String {
        return "${accountId}_$baseKey"
    }

    /** Saves hydration data to Google Drive backup */
    fun saveToDriveBackup(context: Context, userId: String) {
        try {
            val data = HydrationData(
                userId = userId,
                totalWater = _totalWaterIntake.value ?: 0.0,
                lastHydration = _lastHydrationTime.value ?: 0L,
                nextHydration = _nextHydrationTime.value ?: 0L,
                history = getHydrationHistory()
            )

            val json = gson.toJson(data)
            val file = File(context.getExternalFilesDir(null), "${userId}_$FILE_NAME")
            file.writeText(json)
            
            Log.d("Backup", "Hydration data saved to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("Backup", "Failed to save hydration data: ${e.message}")
        }
    }

    /** Restores hydration data from Google Drive backup */
    fun restoreFromDriveBackup(context: Context, userId: String) {
        try {
            val backupDir = context.getExternalFilesDir(null)
            val files = backupDir?.listFiles { file ->
                file.name.startsWith("${userId}_") && file.name.endsWith("_$FILE_NAME")
            } ?: emptyArray()

            if (files.isEmpty()) {
                Log.d("Backup", "No backup files found for user: $userId")
                return
            }

            // Get the most recent backup file
            val matchedFile = files.maxByOrNull { it.lastModified() }
                ?: return

            val json = matchedFile.readText()
            val data = gson.fromJson(json, HydrationData::class.java)

            // Ensure LiveData is updated from prefs
            _totalWaterIntake.postValue(prefs.getFloat(key(KEY_TOTAL_WATER), 0f).toDouble())
            _lastHydrationTime.postValue(prefs.getLong(key(KEY_LAST_HYDRATION), 0L))
            _nextHydrationTime.postValue(prefs.getLong(key(KEY_NEXT_HYDRATION), 0L))
            _totalWaterIntake.value = data.totalWater
            _lastHydrationTime.value = data.lastHydration
            _nextHydrationTime.value = data.nextHydration

            updateStatusAndMood()

            Log.d("Backup", "Hydration data restored from: ${matchedFile.absolutePath}")

        } catch (e: Exception) {
            Log.e("Backup", "Failed to restore hydration data: ${e.message}")
        }
    }

    /** Force refresh all LiveData from SharedPreferences */
    fun refreshFromStorage() {
        if (!::prefs.isInitialized) return
        
        _totalWaterIntake.postValue(prefs.getFloat(key(KEY_TOTAL_WATER), 0f).toDouble())
        _lastHydrationTime.postValue(prefs.getLong(key(KEY_LAST_HYDRATION), 0L))
        _nextHydrationTime.postValue(prefs.getLong(key(KEY_NEXT_HYDRATION), 0L))
        _hydrationGoal.postValue(prefs.getFloat(key(KEY_HYDRATION_GOAL), 3.0f).toDouble())
        updateStatusAndMood()
        
        Log.d("HydrationManager", "Data refreshed from storage")
    }

    /** Force refresh all LiveData and apply decay for time passed */
    fun forceRefreshWithDecay() {
        if (!::prefs.isInitialized) return
        
        // Apply decay for time passed since last hydration
        applyDecayForTimePassed()
        
        // Refresh all LiveData
        refreshFromStorage()
        
        Log.d("HydrationManager", "Data refreshed with decay applied")
    }

    /** Notify all observers that data has changed (useful for manual UI updates) */
    fun notifyDataChanged() {
        if (!::prefs.isInitialized) return
        
        // Force update all LiveData to trigger observers
        val currentIntake = _totalWaterIntake.value ?: 0.0
        val currentLastTime = _lastHydrationTime.value ?: 0L
        val currentNextTime = _nextHydrationTime.value ?: 0L
        val currentGoal = _hydrationGoal.value ?: 3.0
        
        _totalWaterIntake.postValue(currentIntake)
        _lastHydrationTime.postValue(currentLastTime)
        _nextHydrationTime.postValue(currentNextTime)
        _hydrationGoal.postValue(currentGoal)
        updateStatusAndMood()
        
        Log.d("HydrationManager", "Data change notification sent")
    }

    /** Unified synchronization method - ensures all fragments have identical data */
    fun synchronizeAllFragments() {
        if (!::prefs.isInitialized) return
        
        // Only log if debug is enabled to reduce overhead
        if (DEBUG) {
            Log.d("HydrationManager", "Starting unified fragment synchronization")
        }
        
        // Apply decay for any time passed
        applyDecayForTimePassed()
        
        // Get the most current values from SharedPreferences
        val currentIntake = prefs.getFloat(key(KEY_TOTAL_WATER), 0f).toDouble()
        val currentLastTime = prefs.getLong(key(KEY_LAST_HYDRATION), 0L)
        val currentNextTime = prefs.getLong(key(KEY_NEXT_HYDRATION), 0L)
        val currentGoal = prefs.getFloat(key(KEY_HYDRATION_GOAL), 3.0f).toDouble()
        
        // Only update LiveData if values have actually changed
        if (_totalWaterIntake.value != currentIntake) {
            _totalWaterIntake.postValue(currentIntake)
        }
        if (_lastHydrationTime.value != currentLastTime) {
            _lastHydrationTime.postValue(currentLastTime)
        }
        if (_nextHydrationTime.value != currentNextTime) {
            _nextHydrationTime.postValue(currentNextTime)
        }
        if (_hydrationGoal.value != currentGoal) {
            _hydrationGoal.postValue(currentGoal)
        }
        
        // Update status and mood based on unified data
        updateStatusAndMood()
        
        if (DEBUG) {
            Log.d("HydrationManager", "Unified sync complete - Intake: $currentIntake, Goal: $currentGoal")
        }
    }

    /** Optimized version that only updates if data has changed */
    fun synchronizeIfNeeded() {
        if (!::prefs.isInitialized) return
        
        val currentIntake = prefs.getFloat(key(KEY_TOTAL_WATER), 0f).toDouble()
        val currentLastTime = prefs.getLong(key(KEY_LAST_HYDRATION), 0L)
        val currentNextTime = prefs.getLong(key(KEY_NEXT_HYDRATION), 0L)
        val currentGoal = prefs.getFloat(key(KEY_HYDRATION_GOAL), 3.0f).toDouble()
        
        // Check if any values have changed
        val hasChanges = (_totalWaterIntake.value != currentIntake) ||
                        (_lastHydrationTime.value != currentLastTime) ||
                        (_nextHydrationTime.value != currentNextTime) ||
                        (_hydrationGoal.value != currentGoal)
        
        if (hasChanges) {
            synchronizeAllFragments()
        }
    }

    /** Get unified hydration percentage that all fragments should display */
    fun getUnifiedHydrationPercentage(): Int {
        val intake = _totalWaterIntake.value ?: 0.0
        val goal = _hydrationGoal.value ?: 3.0
        return ((intake / goal) * 100).toInt().coerceIn(0, 100)
    }

    /** Get unified hydration status that all fragments should display */
    fun getUnifiedHydrationStatus(): String {
        return _hydrationStatus.value ?: ""
    }

    /** Get unified pal mood that all fragments should display */
    fun getUnifiedPalMood(): String {
        return _palMood.value ?: ""
    }

    /** Verify that all fragments are displaying the same data */
    fun verifyFragmentSynchronization(): Boolean {
        if (!::prefs.isInitialized) return false
        
        // Get current values from SharedPreferences
        val prefsIntake = prefs.getFloat(key(KEY_TOTAL_WATER), 0f).toDouble()
        val prefsLastTime = prefs.getLong(key(KEY_LAST_HYDRATION), 0L)
        val prefsNextTime = prefs.getLong(key(KEY_NEXT_HYDRATION), 0L)
        val prefsGoal = prefs.getFloat(key(KEY_HYDRATION_GOAL), 3.0f).toDouble()
        
        // Get current LiveData values
        val liveIntake = _totalWaterIntake.value ?: 0.0
        val liveLastTime = _lastHydrationTime.value ?: 0L
        val liveNextTime = _nextHydrationTime.value ?: 0L
        val liveGoal = _hydrationGoal.value ?: 3.0
        
        // Check if they match
        val intakeMatches = prefsIntake == liveIntake
        val lastTimeMatches = prefsLastTime == liveLastTime
        val nextTimeMatches = prefsNextTime == liveNextTime
        val goalMatches = prefsGoal == liveGoal
        
        val isSynchronized = intakeMatches && lastTimeMatches && nextTimeMatches && goalMatches
        
        Log.d("HydrationManager", "Fragment synchronization check: $isSynchronized")
        Log.d("HydrationManager", "Intake match: $intakeMatches ($prefsIntake vs $liveIntake)")
        Log.d("HydrationManager", "Last time match: $lastTimeMatches ($prefsLastTime vs $liveLastTime)")
        Log.d("HydrationManager", "Next time match: $nextTimeMatches ($prefsNextTime vs $liveNextTime)")
        Log.d("HydrationManager", "Goal match: $goalMatches ($prefsGoal vs $liveGoal)")
        
        return isSynchronized
    }
}
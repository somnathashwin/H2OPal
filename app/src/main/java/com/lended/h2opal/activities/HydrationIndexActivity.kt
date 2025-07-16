package com.lended.h2opal.activities

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import java.io.File
import java.util.*
import com.lended.h2opal.R
import com.lended.h2opal.helpers.HydrationManager
import com.lended.h2opal.models.HydrationProfile
import androidx.core.content.edit

class HydrationIndexActivity : AppCompatActivity() {

    private lateinit var weightInput: TextInputEditText
    private lateinit var ageInput: TextInputEditText
    private lateinit var genderDropdown: AutoCompleteTextView
    private lateinit var activityLevelDropdown: AutoCompleteTextView
    private lateinit var wakeTimeInput: TextInputEditText
    private lateinit var sleepTimeInput: TextInputEditText
    private lateinit var getStartedBtn: ImageView

    private val gson = Gson()
    private var userId: String = "default_user"
    private val PREFS_NAME = "hydration_prefs"
    private val SETUP_COMPLETE_KEY = "setup_complete"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.get_details_page)

        // Check if setup is complete
        val sharedPref = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isSetupComplete = sharedPref.getBoolean(SETUP_COMPLETE_KEY, false)

        if (isSetupComplete) {
            // Redirect to MainActivity if setup is already complete
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // Get Google account ID or email
        val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(this)
        userId = account?.id ?: account?.email ?: "default_user"

        initViews()
        setupDropdowns()
        setupTimePickers()
        preloadProfileIfExists()
        setupGetStartedButton()
    }

    // Use a per-user filename
    private fun getProfileFileName(): String {
        return "hydration_${userId}.lended"
    }

    private fun saveHydrationProfile(profile: HydrationProfile) {
        val json = gson.toJson(profile)
        File(filesDir, getProfileFileName()).writeText(json)
    }

    private fun loadHydrationProfile(): HydrationProfile? {
        val file = File(filesDir, getProfileFileName())
        if (!file.exists()) return null
        return gson.fromJson(file.readText(), HydrationProfile::class.java)
    }

    private fun initViews() {
        weightInput = findViewById(R.id.weight)
        ageInput = findViewById(R.id.age)
        genderDropdown = findViewById(R.id.genderInput)
        activityLevelDropdown = findViewById(R.id.activityLevel)
        wakeTimeInput = findViewById(R.id.wakeTime)
        sleepTimeInput = findViewById(R.id.sleepTime)
        getStartedBtn = findViewById(R.id.getStarted)
    }

    private fun setupDropdowns() {
        val genderOptions = listOf("Male", "Female", "Prefer not to Say")
        val activityOptions = listOf("Sedentary", "Lightly active", "Moderately active", "Very active", "Extremely active")

        genderDropdown.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genderOptions))
        activityLevelDropdown.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, activityOptions))

        // Disable typing
        genderDropdown.keyListener = null
        activityLevelDropdown.keyListener = null

        // Ensure dropdown shows on click
        genderDropdown.setOnClickListener { genderDropdown.showDropDown() }
        activityLevelDropdown.setOnClickListener { activityLevelDropdown.showDropDown() }
    }

    private fun setupTimePickers() {
        val calendar = Calendar.getInstance()

        wakeTimeInput.setOnClickListener {
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            TimePickerDialog(this, { _, h, m ->
                wakeTimeInput.setText(String.format("%02d:%02d", h, m))
            }, hour, minute, true).show()
        }

        sleepTimeInput.setOnClickListener {
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            TimePickerDialog(this, { _, h, m ->
                sleepTimeInput.setText(String.format("%02d:%02d", h, m))
            }, hour, minute, true).show()
        }

        // Disable typing
        wakeTimeInput.keyListener = null
        sleepTimeInput.keyListener = null
    }

    private fun preloadProfileIfExists() {
        loadHydrationProfile()?.let { profile ->
            weightInput.setText(profile.weight)
            ageInput.setText(profile.age)
            genderDropdown.setText(profile.gender, false)
            activityLevelDropdown.setText(profile.activityLevel, false)
            wakeTimeInput.setText(profile.wakeTime)
            sleepTimeInput.setText(profile.sleepTime)
        }
    }

    private fun setupGetStartedButton() {
        getStartedBtn.setOnClickListener {
            val weight = weightInput.text.toString().trim()
            val age = ageInput.text.toString().trim()
            val gender = genderDropdown.text.toString().trim()
            val activity = activityLevelDropdown.text.toString().trim()
            val wakeTime = wakeTimeInput.text.toString().trim()
            val sleepTime = sleepTimeInput.text.toString().trim()

            if (weight.isEmpty() || age.isEmpty() || gender.isEmpty() ||
                activity.isEmpty() || wakeTime.isEmpty() || sleepTime.isEmpty()
            ) {
                Toast.makeText(this, "Please fill out all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val profile = HydrationProfile(
                weight = weight,
                age = age,
                gender = gender,
                activityLevel = activity,
                wakeTime = wakeTime,
                sleepTime = sleepTime
            )

            saveHydrationProfile(profile)

            // Initialize HydrationManager with user details
            HydrationManager.initialize(this, userId)

            // Calculate initial hydration goal based on user details
            val hydrationGoal = calculateHydrationGoal(profile)
            HydrationManager.setHydrationGoal(hydrationGoal)

            // Mark setup as complete
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit() {
                putBoolean(
                    SETUP_COMPLETE_KEY,
                    true
                )
            }

            // Check if the user is signed in
            val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(this)
            if (account == null) {
                // Redirect to SigninGooglePage if not signed in
                startActivity(Intent(this, SigninGooglePage::class.java))
                finish()
            } else {
                // Redirect to MainActivity if signed in
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }

    private fun calculateHydrationGoal(profile: HydrationProfile): Double {
        val weight = profile.weight.toDoubleOrNull() ?: 0.0
        val age = profile.age.toIntOrNull() ?: 30
        val isMale = profile.gender.equals("Male", ignoreCase = true)
        
        // Basic hydration calculation formula (ml per kg of body weight)
        var baseAmount = if (isMale) 35 else 31
        
        // Adjust for age (older people need slightly less)
        if (age > 60) baseAmount -= 2
        
        // Calculate daily requirement in liters
        return (weight * baseAmount) / 1000.0
    }

}

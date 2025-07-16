package com.lended.h2opal.activities

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.lended.h2opal.R
import com.lended.h2opal.adapters.OnboardingAdapter
import com.lended.h2opal.databinding.WelcomeActivityBinding
import com.lended.h2opal.models.OnboardingItem
import java.io.File

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: WelcomeActivityBinding
    private lateinit var adapter: OnboardingAdapter
    private lateinit var preferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = getSharedPreferences("onboarding", MODE_PRIVATE)

        // Check if it's first time AND if hydration profile exists
        val isFirstTime = preferences.getBoolean("isFirstTime", false)
        val hydrationProfileExists = File(filesDir, "hydration_${getUserId()}.lended").exists()

        binding = WelcomeActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val items = listOf(
            OnboardingItem(R.drawable.h2o_pal_img, "Meet H2O Pal", "A personal friend who monitors your health."),
            OnboardingItem(R.drawable.remainders_img, "Reminders Everytime", "Never forget to drink water again."),
            OnboardingItem(R.drawable.hydration_analysis, "Hydration Report", "Get detailed hydration analysis daily."),
            OnboardingItem(R.drawable.easy_to_use, "Easy 2 Use", "Clean and intuitive interface made just for you.")
        )

        adapter = OnboardingAdapter(items)
        binding.onboardingViewPager.adapter = adapter

        binding.nextBtn.setOnClickListener {
            vibrate()
            val currentItem = binding.onboardingViewPager.currentItem
            if (currentItem < items.size - 1) {
                binding.onboardingViewPager.currentItem = currentItem + 1
            } else {
                navigateToHydrationIndex()
            }
        }

        binding.onboardingViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // Show/hide next button depending on page
                binding.nextBtn.text = if (position == items.lastIndex) "Continue" else "Next"
            }
        })
    }

    private fun navigateToSignInPage() {
        preferences.edit().putBoolean("isFirstTime", true).apply()
        startActivity(Intent(this, SigninGooglePage::class.java))
        finish()
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun navigateToHydrationIndex() {
        startActivity(Intent(this, HydrationIndexActivity::class.java))
        finish()
    }

    private fun vibrate() {
        val vibrator = getSystemService<Vibrator>()
        vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun getUserId(): String {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        return account?.id ?: account?.email ?: "default_user"
    }
}

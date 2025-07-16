package com.lended.h2opal.fragments

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.ScaleAnimation
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.lended.h2opal.R
import com.lended.h2opal.helpers.HydrationManager
import com.lended.h2opal.utils.ToastCustom

class HomeFragment : Fragment() {

    private lateinit var hydratePercent: TextView
    private lateinit var hydrateStatus: TextView
    private lateinit var h2opalMood: TextView
    private lateinit var totalIntake: TextView
    private lateinit var lastHydrate: TextView
    private lateinit var nextHydrate: TextView
    private lateinit var hydratedNowBtn: ImageButton
    private lateinit var h2opalImage: ImageView
    private lateinit var boinkImage: ImageView
    private lateinit var remainderFragment: RemainderFragment

    private var userId: String = "default_user"

    private var lastUpdateTime = 0L
    private val updateDebounceTime = 100L // 100ms debounce
    private val DEBUG = false // Set to true for debug logging

    private var lastUIUpdate = ""
    private var lastPercent = -1
    private var lastIntake = -1.0
    private var lastLastTime = -1L
    private var lastNextTime = -1L

    private companion object {
        private const val NOTIFICATION_INTERVAL = 30 * 60 * 1000L
        private const val ARG_USER_ID = "user_id"
        private const val TAG = "HomeFragment"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        // Restore userId from saved state if available
        userId = savedInstanceState?.getString(ARG_USER_ID) ?: run {
            val account = GoogleSignIn.getLastSignedInAccount(requireContext())
            account?.id ?: account?.email ?: "default_user"
        }

        // Initialize HydrationManager early
        HydrationManager.initialize(requireContext(), userId)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView")
        return inflater.inflate(R.layout.home_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")

        // Initialize ReminderFragment
        remainderFragment = RemainderFragment.newInstance(userId)

        // Bind views
        hydratePercent = view.findViewById(R.id.hydratePercent)
        hydrateStatus = view.findViewById(R.id.hydrateStatus)
        h2opalMood = view.findViewById(R.id.h2opalMood)
        totalIntake = view.findViewById(R.id.totalIntake)
        lastHydrate = view.findViewById(R.id.lastHydrate)
        nextHydrate = view.findViewById(R.id.nextHydrate)
        hydratedNowBtn = view.findViewById(R.id.hydratedNowBtn)
        h2opalImage = view.findViewById(R.id.h2opal)
        boinkImage = view.findViewById(R.id.boink_img)

        boinkImage.visibility = View.GONE

        // Set up observers
        setupObservers()

        hydratedNowBtn.setOnClickListener {
            performButtonAnimation(hydratedNowBtn)
            showHydrationPopup()
        }

        // H2Opal Image Click to trigger 'Hit' Animation
        h2opalImage.setOnClickListener {
            performHitAnimation()
        }

        // Force initial UI update
        updateHydrationUI()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        // Use unified synchronization to ensure all fragments have identical data
        HydrationManager.synchronizeAllFragments()
        updateHydrationUI()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.d(TAG, "onSaveInstanceState")
        outState.putString(ARG_USER_ID, userId)
    }

    private fun setupObservers() {
        // Use a single observer to reduce overhead
        val combinedObserver = Observer<Any> {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUpdateTime > updateDebounceTime) {
                lastUpdateTime = currentTime
                updateHydrationUI()
            }
        }

        // Observe all LiveData with a single debounced update
        HydrationManager.totalWaterIntake.observe(viewLifecycleOwner) { 
            if (DEBUG) Log.d(TAG, "Total water intake updated")
            combinedObserver.onChanged(it)
        }
        HydrationManager.lastHydrationTime.observe(viewLifecycleOwner) { 
            if (DEBUG) Log.d(TAG, "Last hydration time updated")
            combinedObserver.onChanged(it)
        }
        HydrationManager.nextHydrationTime.observe(viewLifecycleOwner) { 
            if (DEBUG) Log.d(TAG, "Next hydration time updated")
            combinedObserver.onChanged(it)
        }
        HydrationManager.hydrationStatus.observe(viewLifecycleOwner) { status ->
            if (DEBUG) Log.d(TAG, "Hydration status updated: $status")
            hydrateStatus.text = "Hydration Status: ${HydrationManager.getUnifiedHydrationStatus()}"
            combinedObserver.onChanged(status)
        }
        HydrationManager.palMood.observe(viewLifecycleOwner) { mood ->
            if (DEBUG) Log.d(TAG, "Pal mood updated: $mood")
            h2opalMood.text = "H2Opal Mood: ${HydrationManager.getUnifiedPalMood()}"
            combinedObserver.onChanged(mood)
        }
        HydrationManager.hydrationGoal.observe(viewLifecycleOwner) { 
            if (DEBUG) Log.d(TAG, "Hydration goal updated")
            combinedObserver.onChanged(it)
        }
    }

    private fun showHydrationPopup() {
        val inflater = LayoutInflater.from(requireContext())
        val popupView = inflater.inflate(R.layout.hydrated_popup, null)

        val editText = popupView.findViewById<EditText>(R.id.hydratedContent)
        val addBtn = popupView.findViewById<ImageButton>(R.id.addBtn)

        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.isOutsideTouchable = true
        popupWindow.setBackgroundDrawable(resources.getDrawable(R.drawable.rounded_background, null))

        view?.post {
            // Fade In Animation
            val fadeIn = AlphaAnimation(0f, 1f).apply {
                duration = 300
            }
            popupView.startAnimation(fadeIn)

            popupWindow.showAtLocation(requireView(), Gravity.CENTER, 0, 0)
        }

        addBtn.setOnClickListener {
            val input = editText.text.toString().trim()
            val quantity = input.toDoubleOrNull()

            if (quantity != null && quantity > 0) {
                HydrationManager.hydrateNow(quantity)
                ToastCustom.showCustomToast(
                    requireContext(),
                    "Added ${"%.2f".format(quantity)}L to intake 💧",
                    R.drawable.dehydrating_pal
                )
                popupWindow.dismiss()
            } else {
                editText.error = "Please enter a valid number"
                ToastCustom.showCustomToast(
                    requireContext(),
                    "Please enter a valid number",
                    R.drawable.dehydrating_pal
                )
            }
        }

        popupWindow.setOnDismissListener {
            // Fade Out Animation
            val fadeOut = AlphaAnimation(1f, 0f).apply {
                duration = 300
            }
            popupView.startAnimation(fadeOut)
        }
    }


    private fun updateHydrationUI() {
        // Get current values
        val percent = HydrationManager.getUnifiedHydrationPercentage()
        val intake = HydrationManager.totalWaterIntake.value ?: 0.0
        val last = HydrationManager.lastHydrationTime.value ?: 0L
        val next = HydrationManager.nextHydrationTime.value ?: 0L

        // Check if values have actually changed
        if (percent == lastPercent && intake == lastIntake && last == lastLastTime && next == lastNextTime) {
            return // No changes, skip update
        }

        // Update cache
        lastPercent = percent
        lastIntake = intake
        lastLastTime = last
        lastNextTime = next

        activity?.runOnUiThread {
            if (DEBUG) Log.d(TAG, "Updating UI with unified data")

            hydratePercent.text = "$percent%"
            totalIntake.text = "Total Intake: ${"%.2f".format(intake)} L"
            lastHydrate.text = "Last Hydrated: ${getTimeAgo(last)} ago"
            nextHydrate.text = if (next <= System.currentTimeMillis()) {
                "Hydrate now!"
            } else {
                "Next Reminder: ${getTimeUntil(next)}"
            }
            
            if (DEBUG) {
                Log.d(TAG, "UI Updated - Percent: $percent%, Intake: $intake, Last: $last, Next: $next")
            }
        }
    }

    private fun getTimeAgo(time: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - time
        val minutes = (diff / (1000 * 60)) % 60
        val hours = (diff / (1000 * 60 * 60)) % 24
        return if (hours > 0) "$hours hr $minutes min" else "$minutes min"
    }

    private fun getTimeUntil(time: Long): String {
        val now = System.currentTimeMillis()
        val diff = time - now
        val minutes = (diff / (1000 * 60)) % 60
        val hours = (diff / (1000 * 60 * 60)) % 24
        return if (hours > 0) "$hours hr $minutes min" else "$minutes min"
    }

    private fun performButtonAnimation(button: ImageButton) {
        button.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        button.animate().scaleX(0.8f).scaleY(0.8f).setDuration(100).withEndAction {
            button.animate().scaleX(1f).scaleY(1f).duration = 100
        }.start()
    }

    private fun performHitAnimation() {
        val boinkDrawables = listOf(
            R.drawable.boink_red,
            R.drawable.boink_yellow,
            R.drawable.boink_blue,
            R.drawable.boink_pink
        )

        val drawableResId = boinkDrawables.random()
        val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val currentImage = h2opalImage.drawable

        h2opalImage.setImageResource(R.drawable.drinknow_pal)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(500)
        }

        boinkImage.setImageResource(drawableResId)
        boinkImage.visibility = View.VISIBLE

        val scaleIn = ScaleAnimation(
            0.5f, 1f, 0.5f, 1f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f
        ).apply { duration = 200 }

        val fadeIn = AlphaAnimation(0f, 1f).apply { duration = 200 }

        boinkImage.startAnimation(scaleIn)
        boinkImage.startAnimation(fadeIn)

        val scaleOut = ScaleAnimation(
            1f, 0.5f, 1f, 0.5f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 200
            startOffset = 500
        }

        val fadeOut = AlphaAnimation(1f, 0f).apply {
            duration = 200
            startOffset = 500
        }

        boinkImage.postDelayed({
            boinkImage.startAnimation(scaleOut)
            boinkImage.startAnimation(fadeOut)
            boinkImage.visibility = View.INVISIBLE
        }, 700)

        h2opalImage.postDelayed({
            h2opalImage.setImageDrawable(currentImage)
        }, 1000)
    }
}
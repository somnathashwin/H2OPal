package com.lended.h2opal.helpers

import android.app.Dialog
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import com.lended.h2opal.databinding.SubscriptionPopupBinding
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.core.models.Size
import java.util.concurrent.TimeUnit

class SubscriptionPopupHelper(private val context: Context) {

    private lateinit var binding: SubscriptionPopupBinding
    private lateinit var dialog: Dialog

    fun show() {
        dialog = Dialog(context)
        binding = SubscriptionPopupBinding.inflate(LayoutInflater.from(context))

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(binding.root)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Set the dialog to fullscreen
        val layoutParams = dialog.window?.attributes
        layoutParams?.width = WindowManager.LayoutParams.MATCH_PARENT
        layoutParams?.height = WindowManager.LayoutParams.MATCH_PARENT
        dialog.window?.attributes = layoutParams
        vibrateCheerfully()

        binding.monthlySubscribe.setOnClickListener {
            Toast.makeText(context, "Monthly subscription selected", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        binding.yearlySubscribe.setOnClickListener {
            Toast.makeText(context, "Yearly subscription selected", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun vibrateCheerfully() {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 100, 50, 150), -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 100, 50, 100, 50, 150), -1)
        }
    }
}

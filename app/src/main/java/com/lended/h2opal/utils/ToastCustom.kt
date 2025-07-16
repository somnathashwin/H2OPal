package com.lended.h2opal.utils

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.lended.h2opal.R

object ToastCustom {
    fun showCustomToast(context: Context, message: String, iconRes: Int = R.drawable.happy_pal) {
        val layoutInflater = LayoutInflater.from(context)
        val layout: View = layoutInflater.inflate(R.layout.custom_toast, null)

        val toastText = layout.findViewById<TextView>(R.id.toast_text)
        val toastIcon = layout.findViewById<ImageView>(R.id.toast_icon)

        toastText.text = message
        toastIcon.setImageResource(iconRes)

        Toast(context).apply {
            duration = Toast.LENGTH_SHORT
            view = layout
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 100)
            show()
        }
    }
}

// OnboardingAdapter.kt
package com.lended.h2opal.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lended.h2opal.databinding.ItemOnboardingBinding
import com.lended.h2opal.models.OnboardingItem

class OnboardingAdapter(private val items: List<OnboardingItem>) :
    RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder>() {

    inner class OnboardingViewHolder(val binding: ItemOnboardingBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
        val binding = ItemOnboardingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OnboardingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
        val item = items[position]
        with(holder.binding) {
            imageDesc.setImageResource(item.imageResId)
            title.text = item.title
            description.text = item.description
        }
    }

    override fun getItemCount(): Int = items.size
}

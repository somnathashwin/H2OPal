package com.lended.h2opal.adapters

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class FragmentPagerAdapter(activity: AppCompatActivity, private val fragments: List<Fragment>) :
    FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = fragments.size

    override fun createFragment(position: Int): Fragment {
        return fragments[position]
    }
}

package com.bandlightconnect.app

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SettingsFragment()     // Position 0: Settings (Left)
            1 -> AutomationsFragment()  // Position 1: Automations (Center)
            2 -> AboutFragment()        // Position 2: About (Right)
            else -> AutomationsFragment()
        }
    }
}
package com.bandlightconnect.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.lang.reflect.Field

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val viewPager: ViewPager2 = findViewById(R.id.viewPager)
        val bottomNavigation: BottomNavigationView = findViewById(R.id.bottomNavigation)

        // Setup ViewPager adapter
        val adapter = ViewPagerAdapter(this)
        viewPager.adapter = adapter

        // Reduce ViewPager2 swipe sensitivity to prevent conflict with RecyclerView vertical scrolling
        viewPager.reduceDragSensitivity()

        // Sync BottomNavigation state when user swipes ViewPager
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                when (position) {
                    0 -> bottomNavigation.selectedItemId = R.id.nav_settings
                    1 -> bottomNavigation.selectedItemId = R.id.nav_automations
                    2 -> bottomNavigation.selectedItemId = R.id.nav_about
                }
            }
        })

        // Sync ViewPager page when user clicks BottomNavigation items
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_settings -> viewPager.currentItem = 0
                R.id.nav_automations -> viewPager.currentItem = 1
                R.id.nav_about -> viewPager.currentItem = 2
            }
            true
        }

        // Set initial fragment to Automations (index 1) without smooth scroll animation
        viewPager.setCurrentItem(1, false)
    }
}

/**
 * Extension function to reduce ViewPager2 swipe sensitivity.
 * It accesses the internal RecyclerView of ViewPager2 and increases its mTouchSlop.
 */
fun ViewPager2.reduceDragSensitivity(sensitivityFactor: Int = 4) {
    try {
        val recyclerViewField: Field = ViewPager2::class.java.getDeclaredField("mRecyclerView")
        recyclerViewField.isAccessible = true
        val recyclerView = recyclerViewField.get(this) as RecyclerView

        val touchSlopField: Field = RecyclerView::class.java.getDeclaredField("mTouchSlop")
        touchSlopField.isAccessible = true
        val touchSlop = touchSlopField.get(recyclerView) as Int

        // Apply the new sensitivity factor
        touchSlopField.set(recyclerView, touchSlop * sensitivityFactor)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
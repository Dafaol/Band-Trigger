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

    private fun requestNearbyAndLocalNetworkPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // No Android 12+ (API 31+), pede BLUETOOTH_CONNECT (grupo Dispositivos por perto)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        // Detecta dinamicamente se o Android atual suporta ACCESS_LOCAL_NETWORK (Android 17+)
        val localNetPerm = "android.permission.ACCESS_LOCAL_NETWORK"
        val supportsLocalNet = try {
            packageManager.getPermissionInfo(localNetPerm, 0)
            true
        } catch (_: Exception) {
            false
        }

        if (supportsLocalNet &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                localNetPerm
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(localNetPerm)
        }

        if (permissionsToRequest.isNotEmpty()) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                1007
            )
        }
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
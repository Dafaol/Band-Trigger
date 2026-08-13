package com.bandlightconnect.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsFragment : Fragment() {

    private lateinit var switchHijackFocus: SwitchMaterial

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        switchHijackFocus = view.findViewById(R.id.switchHijackFocus)

        // Load saved state
        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val isCurrentlyEnabled = sharedPrefs.getBoolean("AUTO_FOCUS_ENABLED", false)
        switchHijackFocus.isChecked = isCurrentlyEnabled

        // Handle Switch clicks
        switchHijackFocus.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!isNotificationServiceEnabled()) {
                    // Revert switch visually until permission is actually granted
                    switchHijackFocus.isChecked = false
                    showPermissionDialog()
                } else {
                    sharedPrefs.edit().putBoolean("AUTO_FOCUS_ENABLED", true).apply()
                    Toast.makeText(requireContext(), "Hijack Band Focus Enabled", Toast.LENGTH_SHORT).show()
                }
            } else {
                sharedPrefs.edit().putBoolean("AUTO_FOCUS_ENABLED", false).apply()
                Toast.makeText(requireContext(), "Hijack Band Focus Disabled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Refresh state when coming back from Android Settings
    override fun onResume() {
        super.onResume()
        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val isCurrentlyEnabled = sharedPrefs.getBoolean("AUTO_FOCUS_ENABLED", false)

        if (isCurrentlyEnabled && !isNotificationServiceEnabled()) {
            sharedPrefs.edit().putBoolean("AUTO_FOCUS_ENABLED", false).apply()
            switchHijackFocus.isChecked = false
        } else {
            switchHijackFocus.isChecked = isCurrentlyEnabled
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = requireContext().packageName
        val flat = android.provider.Settings.Secure.getString(requireContext().contentResolver, "enabled_notification_listeners")
        return flat?.contains(pkgName) == true
    }

    private fun showPermissionDialog() {
        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Permission Required")
            .setMessage("To use 'Hijack Band Focus', the app needs Notification Access.\n\nNote: This allows the app to intercept media pause events.")
            .setPositiveButton("Grant Access") { _, _ ->
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
        }
        dialog.show()
    }
}
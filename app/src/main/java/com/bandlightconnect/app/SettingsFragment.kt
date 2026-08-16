package com.bandlightconnect.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val switchCamera = requireView().findViewById<SwitchMaterial>(R.id.switchHiddenCamera)
        if (isGranted) {
            sharedPrefs.edit().putBoolean("CAMERA_ENABLED", true).apply()
        } else {
            switchCamera.isChecked = false
            sharedPrefs.edit().putBoolean("CAMERA_ENABLED", false).apply()
            Toast.makeText(requireContext(), "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestAudioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val switchAudio = requireView().findViewById<SwitchMaterial>(R.id.switchAudioRecorder)
        if (isGranted) {
            sharedPrefs.edit().putBoolean("AUDIO_ENABLED", true).apply()
        } else {
            switchAudio.isChecked = false
            sharedPrefs.edit().putBoolean("AUDIO_ENABLED", false).apply()
            Toast.makeText(requireContext(), "Microphone permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)

        val switchHijackFocus = view.findViewById<SwitchMaterial>(R.id.switchHijackFocus)
        val switchHiddenCamera = view.findViewById<SwitchMaterial>(R.id.switchHiddenCamera)
        val switchAudioRecorder = view.findViewById<SwitchMaterial>(R.id.switchAudioRecorder)

        // Load initial states
        switchHijackFocus.isChecked = sharedPrefs.getBoolean("AUTO_FOCUS_ENABLED", false)
        switchHiddenCamera.isChecked = sharedPrefs.getBoolean("CAMERA_ENABLED", false)
        switchAudioRecorder.isChecked = sharedPrefs.getBoolean("AUDIO_ENABLED", false)

        switchHijackFocus.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (isNotificationServiceEnabled()) {
                    sharedPrefs.edit().putBoolean("AUTO_FOCUS_ENABLED", true).apply()
                } else {
                    switchHijackFocus.isChecked = false
                    sharedPrefs.edit().putBoolean("AUTO_FOCUS_ENABLED", false).apply()
                    Toast.makeText(requireContext(), "Please enable notification access for Band Trigger", Toast.LENGTH_LONG).show()
                    try {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Could not open settings. Please enable manually.", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                sharedPrefs.edit().putBoolean("AUTO_FOCUS_ENABLED", false).apply()
            }
        }

        switchHiddenCamera.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    sharedPrefs.edit().putBoolean("CAMERA_ENABLED", true).apply()
                } else {
                    requestCameraPermission.launch(Manifest.permission.CAMERA)
                }
            } else {
                sharedPrefs.edit().putBoolean("CAMERA_ENABLED", false).apply()
            }
        }

        switchAudioRecorder.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    sharedPrefs.edit().putBoolean("AUDIO_ENABLED", true).apply()
                } else {
                    requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            } else {
                sharedPrefs.edit().putBoolean("AUDIO_ENABLED", false).apply()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Automatically sync switch state if user just returned from system settings
        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val switchHijackFocus = view?.findViewById<SwitchMaterial>(R.id.switchHijackFocus)
        if (isNotificationServiceEnabled() && sharedPrefs.getBoolean("AUTO_FOCUS_ENABLED", false)) {
            switchHijackFocus?.isChecked = true
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val packageName = requireContext().packageName
        val flat = Settings.Secure.getString(requireContext().contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }
}
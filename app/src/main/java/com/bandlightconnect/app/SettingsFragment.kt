package com.bandlightconnect.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    // --- Permission Launchers ---
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

        val switchHiddenCamera = view.findViewById<SwitchMaterial>(R.id.switchHiddenCamera)
        val switchAudioRecorder = view.findViewById<SwitchMaterial>(R.id.switchAudioRecorder)

        // Load saved state
        switchHiddenCamera.isChecked = sharedPrefs.getBoolean("CAMERA_ENABLED", false)
        switchAudioRecorder.isChecked = sharedPrefs.getBoolean("AUDIO_ENABLED", false)

        // Camera Switch Logic
        switchHiddenCamera.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    sharedPrefs.edit().putBoolean("CAMERA_ENABLED", true).apply()
                } else {
                    // Trigger Android Permission Popup
                    requestCameraPermission.launch(Manifest.permission.CAMERA)
                }
            } else {
                sharedPrefs.edit().putBoolean("CAMERA_ENABLED", false).apply()
            }
        }

        // Audio Switch Logic
        switchAudioRecorder.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    sharedPrefs.edit().putBoolean("AUDIO_ENABLED", true).apply()
                } else {
                    // Trigger Android Permission Popup
                    requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            } else {
                sharedPrefs.edit().putBoolean("AUDIO_ENABLED", false).apply()
            }
        }
    }
}
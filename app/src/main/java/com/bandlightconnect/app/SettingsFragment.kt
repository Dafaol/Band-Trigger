package com.bandlightconnect.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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

    // NOVO: Pedido de permissão para a notificação do contador
    private val requestNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val switchCounterNotif = requireView().findViewById<SwitchMaterial>(R.id.switchCounterNotification)
        if (isGranted) {
            sharedPrefs.edit().putBoolean("COUNTER_NOTIFICATION_ENABLED", true).apply()
        } else {
            switchCounterNotif.isChecked = false
            sharedPrefs.edit().putBoolean("COUNTER_NOTIFICATION_ENABLED", false).apply()
            Toast.makeText(requireContext(), "Notification permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)

        val switchHijackFocus = view.findViewById<SwitchMaterial>(R.id.switchHijackFocus)
        val switchHiddenCamera = view.findViewById<SwitchMaterial>(R.id.switchHiddenCamera)
        val switchAudioRecorder = view.findViewById<SwitchMaterial>(R.id.switchAudioRecorder)
        val switchCounterNotif = view.findViewById<SwitchMaterial>(R.id.switchCounterNotification)

        switchHijackFocus.isChecked = sharedPrefs.getBoolean("AUTO_FOCUS_ENABLED", false)
        switchHiddenCamera.isChecked = sharedPrefs.getBoolean("CAMERA_ENABLED", false)
        switchAudioRecorder.isChecked = sharedPrefs.getBoolean("AUDIO_ENABLED", false)
        switchCounterNotif.isChecked = sharedPrefs.getBoolean("COUNTER_NOTIFICATION_ENABLED", false)

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

        // NOVO: Lógica atualizada para pedir permissão de notificação no Android 13+
        switchCounterNotif.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                        sharedPrefs.edit().putBoolean("COUNTER_NOTIFICATION_ENABLED", true).apply()
                    } else {
                        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    sharedPrefs.edit().putBoolean("COUNTER_NOTIFICATION_ENABLED", true).apply()
                }
            } else {
                sharedPrefs.edit().putBoolean("COUNTER_NOTIFICATION_ENABLED", false).apply()
                val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.cancel(1001)
            }
        }

        val btnExitApp = view.findViewById<View>(R.id.btnExitApp)
        btnExitApp.setOnClickListener {
            val dialog = android.app.AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Turn Off Band Trigger")
                .setMessage("This will disable the 'Hijack Band Focus' feature and stop the background service.\n\nYou will need to turn it back on in Settings next time you use the app.\n\nExit anyway?")
                .setPositiveButton("Turn Off") { _, _ ->
                    sharedPrefs.edit().putBoolean("AUTO_FOCUS_ENABLED", false).apply()
                    requireContext().stopService(Intent(requireContext(), MediaService::class.java))
                    requireActivity().finishAffinity()
                }
                .setNegativeButton("Cancel", null)
                .create()

            dialog.setOnShowListener {
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#FF5252"))
                dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.WHITE)
            }
            dialog.show()
        }
    }

    override fun onResume() {
        super.onResume()
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
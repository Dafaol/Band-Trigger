package com.bandlightconnect.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.concurrent.TimeUnit

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

    private val requestCalendarPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val switchCalendar = requireView().findViewById<SwitchMaterial>(R.id.switchCalendarSync)
        if (isGranted) {
            sharedPrefs.edit().putBoolean("CALENDAR_SYNC_ENABLED", true).apply()
            val savedInterval = sharedPrefs.getLong("CALENDAR_SYNC_INTERVAL", 15L)
            startCalendarSyncWork(savedInterval)
            requireView().findViewById<View>(R.id.layoutSyncInterval).visibility = View.VISIBLE
            Toast.makeText(requireContext(), "Syncing Calendar...", Toast.LENGTH_SHORT).show()
        } else {
            switchCalendar.isChecked = false
            sharedPrefs.edit().putBoolean("CALENDAR_SYNC_ENABLED", false).apply()
            Toast.makeText(requireContext(), "Calendar permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)

        val switchHijackFocus = view.findViewById<SwitchMaterial>(R.id.switchHijackFocus)
        val switchHiddenCamera = view.findViewById<SwitchMaterial>(R.id.switchHiddenCamera)
        val switchAudioRecorder = view.findViewById<SwitchMaterial>(R.id.switchAudioRecorder)
        val switchCounterNotif = view.findViewById<SwitchMaterial>(R.id.switchCounterNotification)

        val switchCalendarSync = view.findViewById<SwitchMaterial>(R.id.switchCalendarSync)
        val layoutSyncInterval = view.findViewById<View>(R.id.layoutSyncInterval)
        val textSyncInterval = view.findViewById<TextView>(R.id.textSyncInterval)

        switchHijackFocus.isChecked = sharedPrefs.getBoolean("AUTO_FOCUS_ENABLED", false)
        switchHiddenCamera.isChecked = sharedPrefs.getBoolean("CAMERA_ENABLED", false)
        switchAudioRecorder.isChecked = sharedPrefs.getBoolean("AUDIO_ENABLED", false)
        switchCounterNotif.isChecked = sharedPrefs.getBoolean("COUNTER_NOTIFICATION_ENABLED", false)

        val isCalendarEnabled = sharedPrefs.getBoolean("CALENDAR_SYNC_ENABLED", false)
        switchCalendarSync.isChecked = isCalendarEnabled
        layoutSyncInterval.visibility = if (isCalendarEnabled) View.VISIBLE else View.GONE

        val currentInterval = sharedPrefs.getLong("CALENDAR_SYNC_INTERVAL", 15L)
        fun updateIntervalText(mins: Long) {
            textSyncInterval.text = when(mins) {
                15L -> "15 Minutes"
                60L -> "1 Hour"
                360L -> "6 Hours"
                720L -> "12 Hours"
                1440L -> "24 Hours"
                else -> "$mins Minutes"
            }
        }
        updateIntervalText(currentInterval)

        // Comportamentos das chaves normais
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

        // Lógica do Calendário
        switchCalendarSync.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                    sharedPrefs.edit().putBoolean("CALENDAR_SYNC_ENABLED", true).apply()
                    layoutSyncInterval.visibility = View.VISIBLE
                    val interval = sharedPrefs.getLong("CALENDAR_SYNC_INTERVAL", 15L)
                    startCalendarSyncWork(interval)
                    Toast.makeText(requireContext(), "Syncing Calendar...", Toast.LENGTH_SHORT).show()
                } else {
                    requestCalendarPermission.launch(Manifest.permission.READ_CALENDAR)
                }
            } else {
                sharedPrefs.edit().putBoolean("CALENDAR_SYNC_ENABLED", false).apply()
                layoutSyncInterval.visibility = View.GONE
                WorkManager.getInstance(requireContext()).cancelUniqueWork("CalendarSyncJob")
            }
        }

        layoutSyncInterval.setOnClickListener {
            val options = arrayOf("15 Minutes", "1 Hour", "6 Hours", "12 Hours", "24 Hours")
            val values = longArrayOf(15, 60, 360, 720, 1440)
            val currentVal = sharedPrefs.getLong("CALENDAR_SYNC_INTERVAL", 15L)
            val selectedIdx = values.indexOf(currentVal).takeIf { it >= 0 } ?: 0

            val dialog = android.app.AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Select Sync Interval")
                .setSingleChoiceItems(options, selectedIdx) { d, which ->
                    val newVal = values[which]
                    sharedPrefs.edit().putLong("CALENDAR_SYNC_INTERVAL", newVal).apply()
                    updateIntervalText(newVal)
                    startCalendarSyncWork(newVal)
                    Toast.makeText(requireContext(), "Interval updated", Toast.LENGTH_SHORT).show()
                    d.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .create()

            dialog.setOnShowListener {
                dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.WHITE)
            }
            dialog.show()
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

    private fun startCalendarSyncWork(intervalMins: Long) {
        val workManager = WorkManager.getInstance(requireContext())

        // Dispara uma vez na mesma hora para feedback
        val instantRequest = OneTimeWorkRequestBuilder<CalendarSyncWorker>().build()
        workManager.enqueue(instantRequest)

        // Substitui a tarefa periódica com o novo tempo
        val periodicRequest = PeriodicWorkRequestBuilder<CalendarSyncWorker>(intervalMins, TimeUnit.MINUTES).build()
        workManager.enqueueUniquePeriodicWork(
            "CalendarSyncJob",
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )
    }
}
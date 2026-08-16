package com.bandlightconnect.app

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class FocusListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        checkIfMediaPaused(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        checkIfMediaPaused(sbn)
    }

    private fun checkIfMediaPaused(sbn: StatusBarNotification?) {
        if (sbn == null) return

        // 1. Verify if user enabled auto-focus hijack
        val sharedPrefs = getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val isEnabled = sharedPrefs.getBoolean("AUTO_FOCUS_ENABLED", false)
        if (!isEnabled) return

        // 2. Ignore our own package to prevent loopback
        if (sbn.packageName == packageName) return

        // 3. Search for media token in notification extras
        val extras = sbn.notification.extras
        val token = extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
        if (token != null) {
            try {
                val controller = MediaController(this, token)
                val state = controller.playbackState?.state

                // 4. If third-party media is paused, reclaim smartband focus
                if (state == PlaybackState.STATE_PAUSED) {
                    Log.d("BandTrigger", "Media paused by: ${sbn.packageName}. Reclaiming watch focus!")
                    val intent = Intent(this, MediaService::class.java)
                    try {
                        startService(intent)
                    } catch (e: Exception) {
                        Log.e("BandTrigger", "Error starting MediaService", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("BandTrigger", "Error reading playback state", e)
            }
        }
    }
}
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
import android.os.Handler
import android.os.Looper

class FocusListenerService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private var hijackRunnable: Runnable? = null

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

        val sharedPrefs = getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val isEnabled = sharedPrefs.getBoolean("AUTO_FOCUS_ENABLED", false)

        if (!isEnabled) return

        if (sbn.packageName == packageName) return

        val extras = sbn.notification.extras
        val token = extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)

        if (token != null) {
            try {
                val controller = MediaController(this, token)
                val state = controller.playbackState?.state

                if (state == PlaybackState.STATE_PLAYING) {
                    hijackRunnable?.let { handler.removeCallbacks(it) }
                }
                else if (state == PlaybackState.STATE_PAUSED) {
                    hijackRunnable?.let { handler.removeCallbacks(it) }

                    hijackRunnable = Runnable {
                        val currentState = controller.playbackState?.state
                        if (currentState == PlaybackState.STATE_PAUSED) {
                            Log.d("BandTrigger", "Media paused by: ${sbn.packageName}. Reclaiming watch focus!")


                            val intent = Intent(this, MediaService::class.java).apply {
                                action = "ACTION_HIJACK"
                            }

                            try {
                                startService(intent)
                            } catch (e: Exception) {
                                Log.e("BandTrigger", "Error starting MediaService", e)
                            }
                        }
                    }
                    handler.postDelayed(hijackRunnable!!, 400)
                }
            } catch (e: Exception) {
                Log.e("BandTrigger", "Error reading playback state", e)
            }
        }
    }
}
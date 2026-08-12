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

        // 1. Verifica se o usuário ligou a opção no menu
        val sharedPrefs = getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val isEnabled = sharedPrefs.getBoolean("AUTO_FOCUS_ENABLED", false)
        if (!isEnabled) return

        // 2. Ignora o nosso próprio aplicativo para não criar um loop fantasma
        if (sbn.packageName == packageName) return

        // 3. Procura pelo Token Universal de Mídia (funciona para Spotify, ReVanced, etc.)
        val extras = sbn.notification.extras
        val token = extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)

        if (token != null) {
            try {
                val controller = MediaController(this, token)
                val state = controller.playbackState?.state

                // 4. Se a mídia universal estiver pausada, damos a "carteirada"
                if (state == PlaybackState.STATE_PAUSED) {
                    Log.d("BandTrigger", "Mídia pausada pelo app: ${sbn.packageName}. Roubando o foco!")

                    // Dispara um comando para o MediaService acordar e retomar o controle do relógio
                    val intent = Intent(this, MediaService::class.java)
                    try {
                        startService(intent)
                    } catch (e: Exception) {
                        Log.e("BandTrigger", "Erro ao iniciar o serviço de mídia", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("BandTrigger", "Erro ao ler o status da mídia", e)
            }
        }
    }
}
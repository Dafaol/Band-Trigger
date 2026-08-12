package com.bandlightconnect.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.util.Log
import org.json.JSONArray

class MediaService : Service() {
    private var mediaSession: MediaSession? = null

    // Lista interna do serviço e posição atual
    private var automationList = mutableListOf<Automation>()
    private var currentIndex = 0

    override fun onCreate() {
        super.onCreate()

        mediaSession = MediaSession(this, "BandTriggerSession")

        // 1. Carrega a lista do banco de dados (JSON) assim que o serviço inicia
        loadAutomationsFromMemory()

        // 2. Atualiza a tela do relógio com o primeiro item da lista
        updateWatchDisplay()

        mediaSession?.setCallback(object : MediaSession.Callback() {
            override fun onPlay() {
                Log.d("BandTrigger", "▶️ PLAY pressed")
                updatePlaybackState(PlaybackState.STATE_PLAYING)
                triggerCurrentWebhook(isTurnOn = true)
            }

            override fun onPause() {
                Log.d("BandTrigger", "⏸️ PAUSE pressed")
                updatePlaybackState(PlaybackState.STATE_PAUSED)
                triggerCurrentWebhook(isTurnOn = false)
            }

            override fun onSkipToNext() {
                if (automationList.isNotEmpty()) {
                    // Pula para o próximo. Se chegar no final, volta para o início (0)
                    currentIndex = (currentIndex + 1) % automationList.size
                    updateWatchDisplay()
                    Log.d("BandTrigger", "⏭️ NEXT -> Index: $currentIndex")
                }
            }

            override fun onSkipToPrevious() {
                if (automationList.isNotEmpty()) {
                    // Volta para o anterior. Se chegar no zero, vai para o último da lista
                    currentIndex = if (currentIndex - 1 < 0) automationList.size - 1 else currentIndex - 1
                    updateWatchDisplay()
                    Log.d("BandTrigger", "⏮️ PREVIOUS -> Index: $currentIndex")
                }
            }
        })

        updatePlaybackState(PlaybackState.STATE_PAUSED)
        mediaSession?.isActive = true
    }

    private fun loadAutomationsFromMemory() {
        val sharedPrefs = getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val jsonString = sharedPrefs.getString("AUTOMATIONS_LIST", "[]")
        try {
            val jsonArray = JSONArray(jsonString)
            automationList.clear()
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val name = jsonObject.getString("name")
                val urlOn = jsonObject.getString("turnOnUrl")
                val urlOff = jsonObject.getString("turnOffUrl")
                automationList.add(Automation(name, urlOn, urlOff))
            }
        } catch (e: Exception) {
            Log.e("BandTrigger", "Error loading automations", e)
        }
    }

    private fun updateWatchDisplay() {
        // Se a lista estiver vazia, avisa no relógio. Se não, mostra o nome da automação atual.
        val title = if (automationList.isNotEmpty()) automationList[currentIndex].name else "No Automations"

        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, "Band Trigger")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, title)
            .build()

        mediaSession?.setMetadata(metadata)
    }

    private fun triggerCurrentWebhook(isTurnOn: Boolean) {
        if (automationList.isEmpty()) return

        val currentAutomation = automationList[currentIndex]
        val urlToCall = if (isTurnOn) currentAutomation.turnOnUrl else currentAutomation.turnOffUrl

        if (urlToCall.isNotEmpty()) {
            Thread {
                try {
                    val connection = java.net.URL(urlToCall).openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    Log.d("BandTrigger", "Webhook triggered: ${connection.responseCode}")
                    connection.disconnect()
                } catch (e: Exception) {
                    Log.e("BandTrigger", "Error triggering webhook", e)
                }
            }.start()
        }
    }

    private fun updatePlaybackState(state: Int) {
        val playbackState = PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS)
            .setState(state, 0, 1.0f)
            .build()

        mediaSession?.setPlaybackState(playbackState)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
    }
}
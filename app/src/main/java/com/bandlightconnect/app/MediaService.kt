package com.bandlightconnect.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.util.Log
import org.json.JSONArray

class MediaService : Service() {
    private var mediaSession: MediaSession? = null

    // VARIÁVEL ADICIONADA: O gerente de áudio que vai dar a carteirada
    private lateinit var audioManager: AudioManager

    // Lista interna do serviço e posição atual
    private var automationList = mutableListOf<Automation>()
    private var currentIndex = 0

    override fun onCreate() {
        super.onCreate()

        // INICIALIZANDO O GERENTE DE ÁUDIO
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mediaSession = MediaSession(this, "BandTriggerSession")

        loadAutomationsFromMemory()
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
                    currentIndex = (currentIndex + 1) % automationList.size
                    updateWatchDisplay()
                    Log.d("BandTrigger", "⏭️ NEXT -> Index: $currentIndex")
                }
            }

            override fun onSkipToPrevious() {
                if (automationList.isNotEmpty()) {
                    currentIndex = if (currentIndex - 1 < 0) automationList.size - 1 else currentIndex - 1
                    updateWatchDisplay()
                    Log.d("BandTrigger", "⏮️ PREVIOUS -> Index: $currentIndex")
                }
            }
        })

        updatePlaybackState(PlaybackState.STATE_PAUSED)
        mediaSession?.isActive = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        loadAutomationsFromMemory()
        updateWatchDisplay()

        // Dá a carteirada do áudio
        requestAudioFocus()
        mediaSession?.isActive = true

        // TRUQUE DO BLUETOOTH: Fingimos que demos "Play" para forçar o relógio a olhar pra cá
        updatePlaybackState(PlaybackState.STATE_PLAYING)

        // E 100 milissegundos depois, voltamos silenciosamente pro "Pause"
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            updatePlaybackState(PlaybackState.STATE_PAUSED)
        }, 100)

        return START_STICKY
    }

    // FUNÇÃO ADICIONADA: Onde o roubo de foco realmente acontece
    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener { /* Ignora se perder o foco */ }
                .build()
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { /* Ignora se perder o foco */ },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
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
        if (::audioManager.isInitialized) {
            audioManager.abandonAudioFocus { }
        }
        mediaSession?.release()
    }
}
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

    // AudioManager instance used to hijack audio focus
    private lateinit var audioManager: AudioManager

    // Instância do nosso gravador de áudio
    private val audioRecorder = AudioRecorderHelper()

    // Internal service list and current index
    private var automationList = mutableListOf<Automation>()
    private var currentIndex = 0

    override fun onCreate() {
        super.onCreate()

        // Initialize AudioManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        mediaSession = MediaSession(this, "BandTriggerSession")
        loadAutomationsFromMemory()
        updateWatchDisplay()

        mediaSession?.setCallback(object : MediaSession.Callback() {
            override fun onPlay() {
                Log.d("BandTrigger", "  PLAY pressed")
                updatePlaybackState(PlaybackState.STATE_PLAYING)
                triggerCurrentWebhook(isTurnOn = true)
            }

            override fun onPause() {
                Log.d("BandTrigger", "  PAUSE pressed")
                updatePlaybackState(PlaybackState.STATE_PAUSED)
                triggerCurrentWebhook(isTurnOn = false)
            }

            override fun onSkipToNext() {
                if (automationList.isNotEmpty()) {
                    currentIndex = (currentIndex + 1) % automationList.size
                    updateWatchDisplay()
                    Log.d("BandTrigger", "  NEXT -> Index: $currentIndex")
                }
            }

            override fun onSkipToPrevious() {
                if (automationList.isNotEmpty()) {
                    currentIndex = if (currentIndex - 1 < 0) automationList.size - 1 else currentIndex - 1
                    updateWatchDisplay()
                    Log.d("BandTrigger", "  PREVIOUS -> Index: $currentIndex")
                }
            }
        })

        updatePlaybackState(PlaybackState.STATE_PAUSED)
        mediaSession?.isActive = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        loadAutomationsFromMemory()
        updateWatchDisplay()

        // Request audio focus to hijack media controls
        requestAudioFocus()
        mediaSession?.isActive = true

        // Bluetooth trick: simulate Play then Pause to force watch to sync
        updatePlaybackState(PlaybackState.STATE_PLAYING)

        // Return silently to pause state after 100 milliseconds
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            updatePlaybackState(PlaybackState.STATE_PAUSED)
        }, 100)

        return START_STICKY
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener { /* Ignore focus loss */ }
                .build()
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { /* Ignore focus loss */ },
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
        val commandToExecute = if (isTurnOn) currentAutomation.turnOnUrl else currentAutomation.turnOffUrl

        if (commandToExecute.isNotEmpty()) {

            // 1. Hidden Camera Command
            if (commandToExecute.trim().equals("CAMERA", ignoreCase = true)) {
                Log.d("BandTrigger", "Executing hardware command: CAMERA")
                val intent = Intent(this, HiddenCameraActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                }
                startActivity(intent)

                // Trick: Force state back to "Paused" after 500 milliseconds
                // The delay ensures the watch has time to process the Bluetooth change
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    updatePlaybackState(PlaybackState.STATE_PAUSED)
                }, 500)
            }

            // 2. Comando do Gravador de Áudio
            else if (commandToExecute.trim().equals("RECORD", ignoreCase = true)) {
                Log.d("BandTrigger", "Executing hardware command: RECORD")
                if (isTurnOn) {
                    // Play apertado: Salva na pasta PÚBLICA de Músicas do Android
                    val publicMusicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
                    val bandTriggerDir = java.io.File(publicMusicDir, "BandTrigger")

                    // Cria a pasta "BandTrigger" dentro de Músicas, se não existir
                    if (!bandTriggerDir.exists()) {
                        bandTriggerDir.mkdirs()
                    }

                    audioRecorder.startRecording(this, bandTriggerDir)
                } else {
                    // Pause apertado: Encerra a gravação
                    audioRecorder.stopRecording()
                }
            }

            // 3. Webhook HTTP Padrão
            else if (commandToExecute.startsWith("http", ignoreCase = true)) {
                Thread {
                    try {
                        val connection = java.net.URL(commandToExecute).openConnection() as java.net.HttpURLConnection
                        connection.requestMethod = "GET"
                        Log.d("BandTrigger", "Webhook triggered: ${connection.responseCode}")
                        connection.disconnect()
                    } catch (e: Exception) {
                        Log.e("BandTrigger", "Error triggering webhook", e)
                    }
                }.start()
            } else {
                Log.w("BandTrigger", "Unknown command format: $commandToExecute")
            }
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
        audioRecorder.stopRecording() // Garante que não fique gravando ao fechar
        mediaSession?.release()
    }
}
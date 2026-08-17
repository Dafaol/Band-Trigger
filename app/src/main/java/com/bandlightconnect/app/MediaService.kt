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
    private lateinit var audioManager: AudioManager
    private val audioRecorder = AudioRecorderHelper()

    private val foldersList = mutableListOf<Folder>()
    private val automationList = mutableListOf<Automation>()

    private var currentFolderId: String? = null
    private val activeDisplayList = mutableListOf<BandDisplayItem>()
    private var currentIndex = 0

    sealed class BandDisplayItem {
        data class FolderItem(val folder: Folder) : BandDisplayItem()
        data class AutomationItem(val automation: Automation) : BandDisplayItem()
        object BackItem : BandDisplayItem()
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mediaSession = MediaSession(this, "BandTriggerSession")

        loadAutomationsFromMemory()
        rebuildDisplayList()

        mediaSession?.setCallback(object : MediaSession.Callback() {
            override fun onPlay() {
                handlePlayPause()
            }
            override fun onPause() {
                handlePlayPause()
            }
            override fun onSkipToNext() {
                if (activeDisplayList.isNotEmpty()) {
                    currentIndex = (currentIndex + 1) % activeDisplayList.size
                    updateWatchDisplay()
                    syncPlaybackStateForCurrentItem()
                }
            }
            override fun onSkipToPrevious() {
                if (activeDisplayList.isNotEmpty()) {
                    currentIndex = if (currentIndex - 1 < 0) activeDisplayList.size - 1 else currentIndex - 1
                    updateWatchDisplay()
                    syncPlaybackStateForCurrentItem()
                }
            }
        })

        updatePlaybackState(PlaybackState.STATE_PAUSED)
        mediaSession?.isActive = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        loadAutomationsFromMemory()
        rebuildDisplayList()

        requestAudioFocus()
        mediaSession?.isActive = true

        updatePlaybackState(PlaybackState.STATE_PLAYING)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            updatePlaybackState(PlaybackState.STATE_PAUSED)
        }, 100)

        return START_STICKY
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener { }
                .build()
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus({ }, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun loadAutomationsFromMemory() {
        val sharedPrefs = getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)

        foldersList.clear()
        try {
            val fArray = JSONArray(sharedPrefs.getString("FOLDERS_LIST", "[]"))
            for (i in 0 until fArray.length()) {
                val obj = fArray.getJSONObject(i)
                foldersList.add(Folder(id = obj.getString("id"), name = obj.getString("name")))
            }
        } catch (e: Exception) { Log.e("BandTrigger", "Error loading folders", e) }

        automationList.clear()
        try {
            val jsonArray = JSONArray(sharedPrefs.getString("AUTOMATIONS_LIST", "[]"))
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val name = jsonObject.getString("name")
                val urlOn = jsonObject.getString("turnOnUrl")
                val urlOff = jsonObject.getString("turnOffUrl")
                val folderId = if (jsonObject.has("folderId") && !jsonObject.isNull("folderId")) jsonObject.getString("folderId") else null
                val isCurrentlyOn = jsonObject.optBoolean("isCurrentlyOn", false)
                automationList.add(Automation(name = name, turnOnUrl = urlOn, turnOffUrl = urlOff, isCurrentlyOn = isCurrentlyOn, folderId = folderId))
            }
        } catch (e: Exception) { Log.e("BandTrigger", "Error loading automations", e) }
    }

    private fun rebuildDisplayList() {
        activeDisplayList.clear()
        if (currentFolderId == null) {
            foldersList.forEach { activeDisplayList.add(BandDisplayItem.FolderItem(it)) }
            automationList.filter { it.folderId == null }.forEach { activeDisplayList.add(BandDisplayItem.AutomationItem(it)) }
        } else {
            activeDisplayList.add(BandDisplayItem.BackItem)
            automationList.filter { it.folderId == currentFolderId }.forEach { activeDisplayList.add(BandDisplayItem.AutomationItem(it)) }
        }

        if (currentIndex >= activeDisplayList.size) currentIndex = 0
        updateWatchDisplay()
    }

    private fun handlePlayPause() {
        if (activeDisplayList.isEmpty()) return

        when (val item = activeDisplayList[currentIndex]) {
            is BandDisplayItem.BackItem -> {
                currentFolderId = null
                currentIndex = 0
                rebuildDisplayList()
                updatePlaybackState(PlaybackState.STATE_PAUSED)
            }
            is BandDisplayItem.FolderItem -> {
                currentFolderId = item.folder.id
                currentIndex = 0
                rebuildDisplayList()
                updatePlaybackState(PlaybackState.STATE_PAUSED)
            }
            is BandDisplayItem.AutomationItem -> {
                val currentAutomation = item.automation
                currentAutomation.isCurrentlyOn = !currentAutomation.isCurrentlyOn

                if (currentAutomation.isCurrentlyOn) {
                    Log.d("BandTrigger", "Smart Toggle: TURN ON")
                    updatePlaybackState(PlaybackState.STATE_PLAYING)
                    triggerCurrentWebhook(currentAutomation, isTurnOn = true)
                } else {
                    Log.d("BandTrigger", "Smart Toggle: TURN OFF")
                    updatePlaybackState(PlaybackState.STATE_PAUSED)
                    triggerCurrentWebhook(currentAutomation, isTurnOn = false)
                }
                updateWatchDisplay()
            }
        }
    }

    private fun syncPlaybackStateForCurrentItem() {
        if (activeDisplayList.isEmpty()) return
        when (val item = activeDisplayList[currentIndex]) {
            is BandDisplayItem.AutomationItem -> {
                val state = if (item.automation.isCurrentlyOn) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
                updatePlaybackState(state)
            }
            else -> updatePlaybackState(PlaybackState.STATE_PAUSED)
        }
    }

    private fun updateWatchDisplay() {
        if (activeDisplayList.isEmpty()) {
            val metadata = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Band Trigger")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "No Automations")
                .build()
            mediaSession?.setMetadata(metadata)
            return
        }

        when (val item = activeDisplayList[currentIndex]) {
            is BandDisplayItem.BackItem -> {
                val metadata = MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "Band Trigger")
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "[ ⬅️ Back / Raiz ]")
                    .build()
                mediaSession?.setMetadata(metadata)
            }
            is BandDisplayItem.FolderItem -> {
                val metadata = MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "Band Trigger")
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "[ 📁 ${item.folder.name} ]")
                    .build()
                mediaSession?.setMetadata(metadata)
            }
            is BandDisplayItem.AutomationItem -> {
                val stateText = if (item.automation.isCurrentlyOn) "[ ON ]" else "[ OFF ]"
                val titleWithState = "${item.automation.name}  $stateText"
                val metadata = MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "Band Trigger")
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, titleWithState)
                    .build()
                mediaSession?.setMetadata(metadata)
            }
        }
    }

    private fun triggerCurrentWebhook(automation: Automation, isTurnOn: Boolean) {
        val commandToExecute = if (isTurnOn) automation.turnOnUrl else automation.turnOffUrl

        if (commandToExecute.isNotEmpty()) {
            if (commandToExecute.trim().equals("CAMERA", ignoreCase = true)) {
                val intent = Intent(this, HiddenCameraActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                }
                startActivity(intent)

                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    automation.isCurrentlyOn = false
                    updatePlaybackState(PlaybackState.STATE_PAUSED)
                    updateWatchDisplay()
                }, 500)
            }
            else if (commandToExecute.trim().equals("RECORD", ignoreCase = true)) {
                if (isTurnOn) {
                    val publicMusicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
                    val bandTriggerDir = java.io.File(publicMusicDir, "BandTrigger")
                    if (!bandTriggerDir.exists()) bandTriggerDir.mkdirs()
                    audioRecorder.startRecording(this, bandTriggerDir)
                } else {
                    audioRecorder.stopRecording()
                }
            }
            else if (commandToExecute.startsWith("http", ignoreCase = true)) {
                Thread {
                    try {
                        val connection = java.net.URL(commandToExecute).openConnection() as java.net.HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.responseCode
                        connection.disconnect()
                    } catch (e: Exception) {
                        Log.e("BandTrigger", "Error triggering webhook", e)
                    }
                }.start()
            }

            val isPcMedia = commandToExecute.contains("playpause", ignoreCase = true)
            if (isPcMedia) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    automation.isCurrentlyOn = false
                    updatePlaybackState(PlaybackState.STATE_PAUSED)
                    updateWatchDisplay()
                }, 500)
            }
        }
    }

    private fun updatePlaybackState(state: Int) {
        val playbackState = PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS)
            .setState(state, 0, 1.0f)
            .build()
        mediaSession?.setPlaybackState(playbackState)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (::audioManager.isInitialized) audioManager.abandonAudioFocus { }
        audioRecorder.stopRecording()
        mediaSession?.release()
    }
}
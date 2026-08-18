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
import org.json.JSONObject
import java.util.UUID

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
                val obj = jsonArray.getJSONObject(i)

                val id = obj.optString("id", UUID.randomUUID().toString())
                val name = obj.getString("name")
                val type = obj.optString("type", "WEBHOOK")
                val webhookUrlOn = obj.optString("webhookUrlOn", "")
                val webhookUrlOff = obj.optString("webhookUrlOff", "")
                val isToggle = obj.optBoolean("isToggle", false)
                val currentState = obj.optBoolean("currentState", false)
                val folderId = if (obj.has("folderId") && !obj.isNull("folderId")) obj.getString("folderId") else null

                automationList.add(Automation(
                    id = id,
                    name = name,
                    type = type,
                    webhookUrlOn = webhookUrlOn,
                    webhookUrlOff = webhookUrlOff,
                    isToggle = isToggle,
                    currentState = currentState,
                    folderId = folderId
                ))
            }
        } catch (e: Exception) { Log.e("BandTrigger", "Error loading automations", e) }
    }

    private fun rebuildDisplayList() {
        activeDisplayList.clear()

        if (currentFolderId == null) {
            // Se estiver na Raiz, carrega a exata ordem misturada do App!
            val sharedPrefs = getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
            val orderArrayStr = sharedPrefs.getString("ROOT_UI_ORDER", "[]")
            val orderArray = JSONArray(orderArrayStr)
            val addedIds = mutableSetOf<String>()

            for (i in 0 until orderArray.length()) {
                val idStr = orderArray.getString(i)
                if (idStr.startsWith("FOLDER_")) {
                    val fId = idStr.removePrefix("FOLDER_")
                    val folder = foldersList.find { it.id == fId }
                    if (folder != null) {
                        activeDisplayList.add(BandDisplayItem.FolderItem(folder))
                        addedIds.add(fId)
                    }
                } else if (idStr.startsWith("AUTO_")) {
                    val aId = idStr.removePrefix("AUTO_")
                    val auto = automationList.find { it.id == aId }
                    if (auto != null && auto.folderId == null) {
                        activeDisplayList.add(BandDisplayItem.AutomationItem(auto))
                        addedIds.add(aId)
                    }
                }
            }
            foldersList.forEach { if (!addedIds.contains(it.id)) activeDisplayList.add(BandDisplayItem.FolderItem(it)) }
            automationList.filter { it.folderId == null }.forEach { if (!addedIds.contains(it.id)) activeDisplayList.add(BandDisplayItem.AutomationItem(it)) }

        } else {
            // Se estiver dentro de uma pasta:
            // 1. Mostra as automações primeiro
            automationList.filter { it.folderId == currentFolderId }.forEach { activeDisplayList.add(BandDisplayItem.AutomationItem(it)) }
            // 2. Coloca o Botão de Voltar ("Sair Pasta") como o ÚLTIMO item!
            activeDisplayList.add(BandDisplayItem.BackItem)
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
                currentAutomation.currentState = !currentAutomation.currentState

                // SALVA O NOVO ESTADO IMEDIATAMENTE PARA NÃO BUGAR O APP
                saveAutomationsToMemory()

                if (currentAutomation.currentState) {
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
                val state = if (item.automation.currentState) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
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
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "[ 🏠 Exit Folder ]")
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
                val stateText = if (item.automation.isToggle) {
                    if (item.automation.currentState) "[ ON ]" else "[ OFF ]"
                } else {
                    "[ TRIGGER ]"
                }
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
        val commandToExecute = if (isTurnOn) automation.webhookUrlOn else automation.webhookUrlOff

        if (commandToExecute.isNotEmpty()) {
            if (automation.type.equals("CAMERA", ignoreCase = true)) {
                val intent = Intent(this, HiddenCameraActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                }
                startActivity(intent)

                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    automation.currentState = false
                    updatePlaybackState(PlaybackState.STATE_PAUSED)
                    updateWatchDisplay()
                }, 500)
            }
            else if (automation.type.equals("WOL", ignoreCase = true)) {
                sendWakeOnLan(commandToExecute)
            }
            else if (automation.type.equals("AUDIO", ignoreCase = true)) {
                if (isTurnOn) {
                    val publicMusicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
                    val bandTriggerDir = java.io.File(publicMusicDir, "BandTrigger")
                    if (!bandTriggerDir.exists()) bandTriggerDir.mkdirs()
                    audioRecorder.startRecording(this, bandTriggerDir)
                } else {
                    audioRecorder.stopRecording()
                }
            }
            else if (automation.type.equals("WEBHOOK", ignoreCase = true) || automation.type.equals("PC_MEDIA", ignoreCase = true)) {
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

            val isPcMedia = automation.type.equals("PC_MEDIA", ignoreCase = true)
            if (isPcMedia || !automation.isToggle) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    automation.currentState = false

                    // SALVA O RESET PARA A TELA NÃO TRAVAR
                    saveAutomationsToMemory()

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

    private fun saveAutomationsToMemory() {
        val sharedPrefs = getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (auto in automationList) {
            jsonArray.put(JSONObject().apply {
                put("id", auto.id)
                put("name", auto.name)
                put("type", auto.type)
                put("webhookUrlOn", auto.webhookUrlOn)
                put("webhookUrlOff", auto.webhookUrlOff)
                put("isToggle", auto.isToggle)
                put("currentState", auto.currentState)
                put("folderId", auto.folderId)
            })
        }
        sharedPrefs.edit().putString("AUTOMATIONS_LIST", jsonArray.toString()).apply()
    }

    private fun sendWakeOnLan(macStr: String) {
        Thread {
            try {
                // Limpa o MAC Address (remove os dois pontos ou traços)
                val hex = macStr.split(":", "-")
                if (hex.size != 6) {
                    Log.e("BandTrigger", "MAC Address inválido")
                    return@Thread
                }

                val macBytes = ByteArray(6)
                for (i in 0..5) {
                    macBytes[i] = Integer.parseInt(hex[i], 16).toByte()
                }

                // Monta o Magic Packet: 6 bytes de 0xFF seguidos de 16 vezes o MAC Address
                val bytes = ByteArray(6 + 16 * macBytes.size)
                for (i in 0..5) bytes[i] = 0xff.toByte()
                var i = 6
                while (i < bytes.size) {
                    System.arraycopy(macBytes, 0, bytes, i, macBytes.size)
                    i += macBytes.size
                }

                // Envia o pacote em Broadcast na porta 9
                val address = java.net.InetAddress.getByName("255.255.255.255")
                val packet = java.net.DatagramPacket(bytes, bytes.size, address, 9)
                val socket = java.net.DatagramSocket()
                socket.broadcast = true
                socket.send(packet)
                socket.close()

                Log.d("BandTrigger", "Magic Packet enviado para $macStr")
            } catch (e: Exception) {
                Log.e("BandTrigger", "Erro ao enviar Wake on LAN", e)
            }
        }.start()
    }
}
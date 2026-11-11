package com.bandlightconnect.app

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

class MediaService : Service() {
    // Variáveis do Alarme
    private var isAlarmRinging = false
    private val alarmHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var alarmRunnable: Runnable? = null
    private val ALARM_NOTIFICATION_ID = 2002
    private val ALARM_CHANNEL_ID = "AlarmChannelV4"

    // Variáveis do Contador
    private var clickCounter = 0
    private var lastCounterName = "Counter"
    private val COUNTER_NOTIFICATION_ID = 1001
    private val COUNTER_CHANNEL_ID = "CounterChannelV2"

    // Variáveis do Fuel Tracker (GPS + Notificação)
    private var locationManager: LocationManager? = null
    private var lastLocation: Location? = null
    private var isFuelTracking = false
    private val FUEL_NOTIFICATION_ID = 3003
    private val FUEL_CHANNEL_ID = "FuelTrackerChannelV1"

    private val fuelLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!isFuelTracking) return
            if (location.hasAccuracy() && location.accuracy > 35f) return

            val prev = lastLocation
            if (prev != null) {
                val distanceMeters = location.distanceTo(prev)
                if (distanceMeters >= 5f) {
                    val distanceKm = distanceMeters / 1000f
                    val sharedPrefs = getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
                    val avgKmL = sharedPrefs.getFloat("FUEL_AVG_KML", 35.0f).coerceAtLeast(1.0f)
                    val cap = sharedPrefs.getFloat("FUEL_CAPACITY", 14.0f)
                    val reserve = sharedPrefs.getFloat("FUEL_RESERVE", 2.0f)
                    val currentLiters = sharedPrefs.getFloat("FUEL_CURRENT_LITERS", cap)
                    val currentTripKm = sharedPrefs.getFloat("FUEL_TRIP_KM", 0.0f)
                    val currentOdo = sharedPrefs.getFloat("FUEL_ODOMETER", 0.0f)
                    val alreadyAlerted = sharedPrefs.getBoolean("FUEL_RESERVE_ALERTED", false)

                    val litersUsed = distanceKm / avgKmL
                    val newLiters = (currentLiters - litersUsed).coerceAtLeast(0.0f)
                    val newTripKm = currentTripKm + distanceKm
                    val newOdo = if (currentOdo > 0f) currentOdo + distanceKm else distanceKm

                    val editor = sharedPrefs.edit()
                        .putFloat("FUEL_CURRENT_LITERS", newLiters)
                        .putFloat("FUEL_TRIP_KM", newTripKm)
                        .putFloat("FUEL_ODOMETER", newOdo)

                    // Dispara o alarme na pulseira automaticamente ao atingir a reserva
                    if (newLiters <= reserve && !alreadyAlerted) {
                        editor.putBoolean("FUEL_RESERVE_ALERTED", true)
                        startAlarmLoop(String.format(Locale.US, "⛽ Reserve (%.1fL)", newLiters))
                    }
                    editor.apply()

                    lastLocation = location
                    updateWatchDisplay()
                    updateFuelNotification()
                }
            } else {
                lastLocation = location
            }
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

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
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        mediaSession = MediaSession(this, "BandTriggerSession")

        val sharedPrefs = getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        isFuelTracking = sharedPrefs.getBoolean("FUEL_TRACKING_ACTIVE", false)
        if (isFuelTracking) {
            startFuelGpsTracking()
            updateFuelNotification()
        }

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
        if (intent?.action == "ACTION_FIRE_ALARM") {
            val alarmName = intent.getStringExtra("ALARM_NAME") ?: "Lembrete"
            startAlarmLoop(alarmName)
            return START_STICKY
        }

        if (intent?.action == "ACTION_RESET_COUNTER") {
            clickCounter = 0
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancel(COUNTER_NOTIFICATION_ID)
            updateWatchDisplay()
            return START_STICKY
        }

        if (intent?.action == "ACTION_TOGGLE_FUEL_TRIP") {
            toggleFuelTripState()
            return START_STICKY
        }

        if (intent?.action == "ACTION_REFRESH_FUEL") {
            updateWatchDisplay()
            if (isFuelTracking) updateFuelNotification()
            return START_STICKY
        }

        loadAutomationsFromMemory()
        rebuildDisplayList()

        if (audioManager.isMusicActive) {
            mediaSession?.isActive = true
            updateWatchDisplay()
        } else {
            requestAudioFocus()
            mediaSession?.isActive = true
            updatePlaybackState(PlaybackState.STATE_PLAYING)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                updatePlaybackState(PlaybackState.STATE_PAUSED)
            }, 100)
        }

        return START_STICKY
    }

    private fun toggleFuelTripState() {
        isFuelTracking = !isFuelTracking
        val sharedPrefs = getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("FUEL_TRACKING_ACTIVE", isFuelTracking).apply()

        automationList.filter { it.type.equals("FUEL", ignoreCase = true) }.forEach {
            it.currentState = isFuelTracking
        }
        saveAutomationsToMemory()

        if (isFuelTracking) {
            startFuelGpsTracking()
            updatePlaybackState(PlaybackState.STATE_PLAYING)
        } else {
            stopFuelGpsTracking()
            updatePlaybackState(PlaybackState.STATE_PAUSED)
        }
        updateFuelNotification()
        updateWatchDisplay()
    }

    private fun startFuelGpsTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                lastLocation = null
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    3000L,
                    5f,
                    fuelLocationListener
                )
            } catch (e: Exception) {
                Log.e("BandTrigger", "Erro ao iniciar GPS do Fuel Tracker", e)
            }
        }
    }

    private fun stopFuelGpsTracking() {
        try {
            locationManager?.removeUpdates(fuelLocationListener)
            lastLocation = null
        } catch (e: Exception) {
            Log.e("BandTrigger", "Erro ao parar GPS do Fuel Tracker", e)
        }
    }

    private fun updateFuelNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (!isFuelTracking) {
            notificationManager.cancel(FUEL_NOTIFICATION_ID)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                FUEL_CHANNEL_ID,
                "Band Trigger Fuel Tracker",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val sharedPrefs = getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val cap = sharedPrefs.getFloat("FUEL_CAPACITY", 14.0f)
        val liters = sharedPrefs.getFloat("FUEL_CURRENT_LITERS", cap)
        val avg = sharedPrefs.getFloat("FUEL_AVG_KML", 35.0f)
        val tripKm = sharedPrefs.getFloat("FUEL_TRIP_KM", 0.0f)
        val odoKm = sharedPrefs.getFloat("FUEL_ODOMETER", 0.0f)
        val rangeKm = (liters * avg).toInt()

        val stopIntent = Intent(this, MediaService::class.java).apply {
            action = "ACTION_TOGGLE_FUEL_TRIP"
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }
        val stopPendingIntent = android.app.PendingIntent.getService(this, 2, stopIntent, pendingFlags)

        val builder = androidx.core.app.NotificationCompat.Builder(this, FUEL_CHANNEL_ID)
            .setSmallIcon(R.drawable.logo_band_trigger_small_icon)
            .setContentTitle(String.format(Locale.US, "Fuel Tracker: %.2f L (~%d km)", liters, rangeKm))
            .setContentText(String.format(Locale.US, "Trip: %.1f km | Odo: %.1f km", tripKm, odoKm))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop Trip", stopPendingIntent)

        notificationManager.notify(FUEL_NOTIFICATION_ID, builder.build())
    }

    private fun updateCounterNotification(automationName: String) {
        val sharedPrefs = getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val isNotifEnabled = sharedPrefs.getBoolean("COUNTER_NOTIFICATION_ENABLED", false)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (!isNotifEnabled) {
            notificationManager.cancel(COUNTER_NOTIFICATION_ID)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                COUNTER_CHANNEL_ID,
                "Band Trigger Counter",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val builder = androidx.core.app.NotificationCompat.Builder(this, COUNTER_CHANNEL_ID)
            .setSmallIcon(R.drawable.logo_band_trigger_small_icon)
            .setContentTitle(automationName)
            .setContentText("Count: $clickCounter")
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)

        if (clickCounter > 0) {
            val resetIntent = Intent(this, MediaService::class.java).apply {
                action = "ACTION_RESET_COUNTER"
            }
            val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            }
            val resetPendingIntent = android.app.PendingIntent.getService(this, 1, resetIntent, pendingFlags)
            builder.addAction(android.R.drawable.ic_menu_revert, "Reset", resetPendingIntent)
        }

        notificationManager.notify(COUNTER_NOTIFICATION_ID, builder.build())
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
                val type = obj.optString("type", "WEBHOOK")
                val automation = Automation(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    name = obj.getString("name"),
                    type = type,
                    webhookUrlOn = obj.optString("webhookUrlOn", ""),
                    webhookUrlOff = obj.optString("webhookUrlOff", ""),
                    isToggle = obj.optBoolean("isToggle", false),
                    currentState = if (type == "FUEL") isFuelTracking else obj.optBoolean("currentState", false),
                    folderId = if (obj.has("folderId") && !obj.isNull("folderId")) obj.getString("folderId") else null,
                    alarmDays = obj.optString("alarmDays", "")
                )
                automationList.add(automation)
                if (automation.type == "ALARM") {
                    scheduleAlarm(automation)
                }
            }
        } catch (e: Exception) { Log.e("BandTrigger", "Error loading automations", e) }
    }

    private fun rebuildDisplayList() {
        activeDisplayList.clear()
        if (currentFolderId == null) {
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
            automationList.filter { it.folderId == currentFolderId }.forEach { activeDisplayList.add(BandDisplayItem.AutomationItem(it)) }
            activeDisplayList.add(BandDisplayItem.BackItem)
        }

        if (currentIndex >= activeDisplayList.size) currentIndex = 0
        updateWatchDisplay()
    }

    private fun handlePlayPause() {
        if (isAlarmRinging) {
            stopAlarmLoop()
            return
        }

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
                if (currentAutomation.type.equals("FUEL", ignoreCase = true)) {
                    toggleFuelTripState()
                    return
                }

                currentAutomation.currentState = !currentAutomation.currentState
                saveAutomationsToMemory()

                if (currentAutomation.type.equals("COUNTER", ignoreCase = true)) {
                    clickCounter++
                    lastCounterName = currentAutomation.name
                    updateCounterNotification(lastCounterName)
                    updateWatchDisplay()
                } else if (currentAutomation.type.equals("ALARM", ignoreCase = true)) {
                    startAlarmLoop(currentAutomation.name)
                } else {
                    if (currentAutomation.currentState) {
                        updatePlaybackState(PlaybackState.STATE_PLAYING)
                        triggerCurrentWebhook(currentAutomation, isTurnOn = true)
                    } else {
                        updatePlaybackState(PlaybackState.STATE_PAUSED)
                        triggerCurrentWebhook(currentAutomation, isTurnOn = false)
                    }
                    updateWatchDisplay()
                }
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
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "[ 🔙 Exit Folder ]")
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
                val stateText = if (item.automation.type.equals("COUNTER", ignoreCase = true)) {
                    "[ COUNT: $clickCounter ]"
                } else if (item.automation.type.equals("ALARM", ignoreCase = true)) {
                    "[ ⏰ ${item.automation.webhookUrlOn} ]"
                } else if (item.automation.type.equals("FUEL", ignoreCase = true)) {
                    val sharedPrefs = getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
                    val cap = sharedPrefs.getFloat("FUEL_CAPACITY", 14.0f)
                    val liters = sharedPrefs.getFloat("FUEL_CURRENT_LITERS", cap)
                    val avg = sharedPrefs.getFloat("FUEL_AVG_KML", 35.0f)
                    val rangeKm = (liters * avg).toInt()
                    val statusPrefix = if (isFuelTracking) "ON" else "OFF"
                    String.format(Locale.US, "[ %s: %.1fL | %dkm ]", statusPrefix, liters, rangeKm)
                } else if (item.automation.isToggle) {
                    if (item.automation.currentState) "[ ON ]" else "[ OFF ]"
                } else {
                    "[ TRIGGER ]"
                }

                val titleWithState = "${item.automation.name} $stateText"
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
        stopAlarmLoop()
        stopFuelGpsTracking()
        if (::audioManager.isInitialized) audioManager.abandonAudioFocus { }
        audioRecorder.stopRecording()
        mediaSession?.release()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(COUNTER_NOTIFICATION_ID)
        notificationManager.cancel(FUEL_NOTIFICATION_ID)
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
                put("alarmDays", auto.alarmDays)
            })
        }
        sharedPrefs.edit().putString("AUTOMATIONS_LIST", jsonArray.toString()).apply()
    }

    private fun sendWakeOnLan(macStr: String) {
        Thread {
            try {
                val hex = macStr.split(":", "-")
                if (hex.size != 6) return@Thread
                val macBytes = ByteArray(6)
                for (i in 0..5) macBytes[i] = Integer.parseInt(hex[i], 16).toByte()
                val bytes = ByteArray(6 + 16 * macBytes.size)
                for (i in 0..5) bytes[i] = 0xff.toByte()
                var i = 6
                while (i < bytes.size) {
                    System.arraycopy(macBytes, 0, bytes, i, macBytes.size)
                    i += macBytes.size
                }
                val address = java.net.InetAddress.getByName("255.255.255.255")
                val packet = java.net.DatagramPacket(bytes, bytes.size, address, 9)
                val socket = java.net.DatagramSocket()
                socket.broadcast = true
                socket.send(packet)
                socket.close()
            } catch (e: Exception) {
                Log.e("BandTrigger", "Erro ao enviar Wake on LAN", e)
            }
        }.start()
    }

    private fun getVibrator(): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun stopAlarmLoop() {
        isAlarmRinging = false
        alarmRunnable?.let { alarmHandler.removeCallbacks(it) }
        try {
            getVibrator().cancel()
        } catch (e: Exception) { }
        val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notifManager.cancel(ALARM_NOTIFICATION_ID)
        updatePlaybackState(PlaybackState.STATE_PAUSED)
        updateWatchDisplay()
    }

    private fun startAlarmLoop(alarmName: String) {
        isAlarmRinging = true
        val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val vibPattern = longArrayOf(0, 500, 200, 500, 200, 500)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                ALARM_CHANNEL_ID,
                "Band Trigger Alarms",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                vibrationPattern = vibPattern
            }
            notifManager.createNotificationChannel(channel)
        }

        val builder = androidx.core.app.NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.logo_band_trigger_small_icon)
            .setContentTitle("⏰ $alarmName")
            .setContentText("Press Play/Pause on the watch to disarm!")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
            .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
            .setVibrate(vibPattern)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)

        alarmRunnable = object : Runnable {
            var toggle = false
            var tickCount = 0
            override fun run() {
                if (!isAlarmRinging) return

                // Vibra o celular diretamente via hardware
                try {
                    val vibrator = getVibrator()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createWaveform(vibPattern, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(vibPattern, -1)
                    }
                } catch (e: Exception) {
                    Log.e("BandTrigger", "Erro ao vibrar hardware", e)
                }

                // A cada 2 ciclos (5s), reenvia a notificação para a Galaxy Fit 3 vibrar novamente
                if (tickCount % 2 == 0) {
                    notifManager.notify(ALARM_NOTIFICATION_ID, builder.build())
                }
                tickCount++

                val metadata = MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "⏰ $alarmName")
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, if (toggle) "[- PRESS PLAY -]" else "[- TO STOP -]")
                    .build()
                mediaSession?.setMetadata(metadata)
                updatePlaybackState(if (toggle) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED)
                toggle = !toggle
                alarmHandler.postDelayed(this, 2500)
            }
        }
        alarmHandler.post(alarmRunnable!!)
    }

    private fun scheduleAlarm(auto: Automation) {
        val parts = auto.webhookUrlOn.split(":")
        if (parts.size != 2) return
        val hour = parts[0].toIntOrNull() ?: return
        val minute = parts[1].toIntOrNull() ?: return

        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
        }

        val selectedDays = auto.alarmDays.split(",").mapNotNull { it.toIntOrNull() }
        if (selectedDays.isEmpty()) {
            if (calendar.before(java.util.Calendar.getInstance())) {
                calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        } else {
            val currentDayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
            var daysToAdd = 0
            for (i in 0..7) {
                val checkDay = (currentDayOfWeek + i - 1) % 7 + 1
                if (selectedDays.contains(checkDay)) {
                    if (i == 0 && calendar.before(java.util.Calendar.getInstance())) continue
                    daysToAdd = i
                    break
                }
            }
            if (daysToAdd == 0 && calendar.before(java.util.Calendar.getInstance())) daysToAdd = 7
            calendar.add(java.util.Calendar.DAY_OF_YEAR, daysToAdd)
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("ALARM_NAME", auto.name)
            putExtra("ALARM_ID", auto.id)
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(this, auto.id.hashCode(), intent, pendingFlags)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } else {
                alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            // Fallback seguro caso o sistema restrinja o alarme exato
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
        }
    }
}
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

    // Variáveis do Alarme
    private var isAlarmRinging = false
    private val alarmHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var alarmRunnable: Runnable? = null
    private val ALARM_NOTIFICATION_ID = 2002
    private val ALARM_CHANNEL_ID = "AlarmChannelV3"

    // Variáveis do Contador
    private var clickCounter = 0
    private var lastCounterName = "Counter"
    private val COUNTER_NOTIFICATION_ID = 1001
    private val COUNTER_CHANNEL_ID = "CounterChannelV2"

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

        if (intent?.action == "ACTION_FIRE_ALARM") {
            val alarmName = intent.getStringExtra("ALARM_NAME") ?: "Lembrete"
            startAlarmLoop(alarmName)
            return START_STICKY
        }

        // Se o comando for para zerar o contador através do botão na notificação
        if (intent?.action == "ACTION_RESET_COUNTER") {
            clickCounter = 0

            // Destrói a notificação do celular para limpar a tela
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancel(COUNTER_NOTIFICATION_ID)

            // Atualiza o visor do relógio para mostrar que zerou
            updateWatchDisplay()

            return START_STICKY
        }

        loadAutomationsFromMemory()
        rebuildDisplayList()

        // A CORREÇÃO: O celular é a fonte absoluta da verdade.
        // Se tem música tocando (transição rápida do 5G), apenas atualizamos a tela em silêncio.
        if (audioManager.isMusicActive) {
            mediaSession?.isActive = true
            updateWatchDisplay()
            Log.d("BandTrigger", "Transição de faixa detectada. Foco mantido no player de música.")
        } else {
            // Se está realmente em silêncio, o Band Trigger assume o controle.
            requestAudioFocus()
            mediaSession?.isActive = true
            updatePlaybackState(PlaybackState.STATE_PLAYING)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                updatePlaybackState(PlaybackState.STATE_PAUSED)
            }, 100)
        }

        return START_STICKY
    }

    private fun updateCounterNotification(automationName: String) {
        val sharedPrefs = getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val isNotifEnabled = sharedPrefs.getBoolean("COUNTER_NOTIFICATION_ENABLED", false)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        // Se a opção estiver desligada, destrói a notificação
        if (!isNotifEnabled) {
            notificationManager.cancel(COUNTER_NOTIFICATION_ID)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // MUDANÇA 1: IMPORTANCE_DEFAULT para o relógio não ignorar a notificação
            val channel = android.app.NotificationChannel(
                COUNTER_CHANNEL_ID,
                "Band Trigger Counter",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val builder = androidx.core.app.NotificationCompat.Builder(this, COUNTER_CHANNEL_ID)
            // MUDANÇA 2: Usando o ícone de lista do próprio app para não bugar no Android
            .setSmallIcon(R.drawable.logo_band_trigger_small_icon)
            // MUDANÇA 3: Nome do app explícito no título para não causar confusão
            .setContentTitle("$automationName")
            .setContentText("Count: $clickCounter")
            .setOnlyAlertOnce(true) // Impede que o celular apite a cada novo clique
            // MUDANÇA 4: Removemos o setOngoing(true) para o relógio aceitar receber
            .setAutoCancel(false)

        // Adiciona o botão de Reset apenas se o contador for maior que 0
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
                val automation = Automation(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    name = obj.getString("name"),
                    type = obj.optString("type", "WEBHOOK"),
                    webhookUrlOn = obj.optString("webhookUrlOn", ""),
                    webhookUrlOff = obj.optString("webhookUrlOff", ""),
                    isToggle = obj.optBoolean("isToggle", false),
                    currentState = obj.optBoolean("currentState", false),
                    folderId = if (obj.has("folderId") && !obj.isNull("folderId")) obj.getString("folderId") else null,
                    alarmDays = obj.optString("alarmDays", "")
                )
                automationList.add(automation)

                // NOVO: Se for um alarme, já agenda ele ao carregar a memória!
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
        // NOVO: Se o alarme estiver tocando, o Play/Pause serve como botão de "Desarmar"
        if (isAlarmRinging) {
            isAlarmRinging = false
            alarmRunnable?.let { alarmHandler.removeCallbacks(it) }

            val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notifManager.cancel(ALARM_NOTIFICATION_ID)

            updatePlaybackState(PlaybackState.STATE_PAUSED)
            updateWatchDisplay() // Volta a tela ao normal
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
                currentAutomation.currentState = !currentAutomation.currentState

                saveAutomationsToMemory()

                if (currentAutomation.type.equals("COUNTER", ignoreCase = true)) {
                    clickCounter++
                    lastCounterName = currentAutomation.name
                    updateCounterNotification(lastCounterName)
                    updateWatchDisplay()
                } else if (currentAutomation.type.equals("ALARM", ignoreCase = true)) {
                    // Disparo manual para testar o visual do alarme!
                    startAlarmLoop(currentAutomation.name)
                } else {
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
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "[ \uD83D\uDD19 Exit Folder ]")
                    .build()
                mediaSession?.setMetadata(metadata)
            }
            is BandDisplayItem.FolderItem -> {
                val metadata = MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "Band Trigger")
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "[ \uD83D\uDCC1 ${item.folder.name} ]")
                    .build()
                mediaSession?.setMetadata(metadata)
            }
            is BandDisplayItem.AutomationItem -> {
                val stateText = if (item.automation.type.equals("COUNTER", ignoreCase = true)) {
                    "[ 🔢 COUNT: $clickCounter ]"
                } else if (item.automation.type.equals("ALARM", ignoreCase = true)) {
                    "[ ⏰ ${item.automation.webhookUrlOn} ]"
                } else if (item.automation.isToggle) {
                    if (item.automation.currentState) "[ 🟢 ON ]" else "[ 🔴 OFF ]"
                } else {
                    "[ ⚡ TRIGGER ]"
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

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(COUNTER_NOTIFICATION_ID)
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
                val hex = macStr.split(":", "-")
                if (hex.size != 6) {
                    Log.e("BandTrigger", "MAC Address inválido")
                    return@Thread
                }
                val macBytes = ByteArray(6)
                for (i in 0..5) {
                    macBytes[i] = Integer.parseInt(hex[i], 16).toByte()
                }

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
                Log.d("BandTrigger", "Magic Packet enviado para $macStr")
            } catch (e: Exception) {
                Log.e("BandTrigger", "Erro ao enviar Wake on LAN", e)
            }
        }.start()
    }

    private fun startAlarmLoop(alarmName: String) {
        isAlarmRinging = true

        // 1. Dispara a notificação de ALTA PRIORIDADE para o celular e relógio VIBRAREM
        val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                ALARM_CHANNEL_ID,
                "Alarmes",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500) // Vibração agressiva
            }
            notifManager.createNotificationChannel(channel)
        }

        val builder = androidx.core.app.NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.logo_band_trigger_small_icon)
            .setContentTitle("🚨 $alarmName")
            .setContentText("Press Play/Pause on the watch to disarm!")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX) // MÁXIMA
            .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL) // FORÇA O CELULAR A APITAR E VIBRAR ALTO
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setAutoCancel(true)

        notifManager.notify(ALARM_NOTIFICATION_ID, builder.build())

        // 2. O Sequestro de Tela (Pisca a tela do relógio a cada 2.5s)
        alarmRunnable = object : Runnable {
            var toggle = false
            override fun run() {
                if (!isAlarmRinging) return

                val metadata = MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "🚨 $alarmName 🚨")
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, if (toggle) "[- PRESS PLAY -]" else "[- TO STOP -]")
                    .build()
                mediaSession?.setMetadata(metadata)

                updatePlaybackState(if (toggle) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED)
                toggle = !toggle

                // Repete a cada 2.5 segundos
                alarmHandler.postDelayed(this, 2500)
            }
        }

        // Inicia o loop imediatamente
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
            // Alarme único (não repete)
            if (calendar.before(java.util.Calendar.getInstance())) {
                calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        } else {
            // Alarme recorrente: encontrar o próximo dia válido
            val currentDayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
            var daysToAdd = 0

            // Verifica os próximos 7 dias
            for (i in 0..7) {
                val checkDay = (currentDayOfWeek + i - 1) % 7 + 1
                if (selectedDays.contains(checkDay)) {
                    if (i == 0 && calendar.before(java.util.Calendar.getInstance())) {
                        // É hoje, mas o horário já passou, então checa a próxima ocorrência
                        continue
                    }
                    daysToAdd = i
                    break
                }
            }
            // Se passou pelo loop e daysToAdd é 0, mas o horário já passou, repete na mesma semana
            if (daysToAdd == 0 && calendar.before(java.util.Calendar.getInstance())) {
                daysToAdd = 7
            }
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
            Log.d("BandTrigger", "Alarme ${auto.name} agendado para: ${calendar.time}")
        } catch (e: SecurityException) {
            Log.e("BandTrigger", "Sem permissão de alarme exato", e)
        }
    }
}
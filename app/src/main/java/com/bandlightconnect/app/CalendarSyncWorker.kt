package com.bandlightconnect.app

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

class CalendarSyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        Log.d("BandTrigger", "Iniciando Sincronização Fantasma do Calendário...")
        val sharedPrefs = applicationContext.getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)

        // 1. Carregar Pastas
        val foldersList = mutableListOf<Folder>()
        try {
            val fArray = JSONArray(sharedPrefs.getString("FOLDERS_LIST", "[]"))
            for (i in 0 until fArray.length()) {
                val obj = fArray.getJSONObject(i)
                foldersList.add(Folder(id = obj.getString("id"), name = obj.getString("name")))
            }
        } catch (e: Exception) { }

        // Procura ou Cria a pasta isolada para a Agenda
        var calendarFolder = foldersList.find { it.name == "Google Calendar" }
        if (calendarFolder == null) {
            calendarFolder = Folder(name = "Google Calendar")
            foldersList.add(calendarFolder)
            val jsonArray = JSONArray()
            foldersList.forEach { jsonArray.put(JSONObject().apply { put("id", it.id); put("name", it.name) }) }
            sharedPrefs.edit().putString("FOLDERS_LIST", jsonArray.toString()).apply()
        }

        // 2. Carregar Automações e limpar os eventos antigos
        val automationsList = mutableListOf<Automation>()
        try {
            val jsonArray = JSONArray(sharedPrefs.getString("AUTOMATIONS_LIST", "[]"))
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                automationsList.add(
                    Automation(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.getString("name"),
                        type = obj.optString("type", "WEBHOOK"),
                        webhookUrlOn = obj.optString("webhookUrlOn", ""),
                        webhookUrlOff = obj.optString("webhookUrlOff", ""),
                        isToggle = obj.optBoolean("isToggle", false),
                        folderId = if (obj.has("folderId") && !obj.isNull("folderId")) obj.getString("folderId") else null,
                        alarmDays = obj.optString("alarmDays", "")
                    )
                )
            }
        } catch (e: Exception) { }

        // Apaga todos os alarmes que estavam na pasta "Google Calendar" (para atualizar limpo)
        automationsList.removeAll { it.folderId == calendarFolder?.id }

        // 3. Consultar a Agenda (Apenas as próximas 24 horas para o alarme bater com o dia certo)
        val now = System.currentTimeMillis()
        val tomorrow = now + (24 * 60 * 60 * 1000)

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, now)
        ContentUris.appendId(builder, tomorrow)
        val uri = builder.build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.ALL_DAY
        )

        // Ignorar eventos de dia inteiro (All Day) e eventos que já passaram
        val selection = "${CalendarContract.Instances.ALL_DAY} = 0 AND ${CalendarContract.Instances.BEGIN} >= ?"
        val selectionArgs = arrayOf(now.toString())

        val cursor = applicationContext.contentResolver.query(uri, projection, selection, selectionArgs, "${CalendarContract.Instances.BEGIN} ASC")
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        var eventosEncontrados = 0
        cursor?.use {
            val titleIdx = it.getColumnIndex(CalendarContract.Instances.TITLE)
            val beginIdx = it.getColumnIndex(CalendarContract.Instances.BEGIN)

            while (it.moveToNext()) {
                eventosEncontrados++
                val title = it.getString(titleIdx) ?: "Event"
                val beginTime = it.getLong(beginIdx)
                val timeStr = timeFormat.format(beginTime)

                val auto = Automation(
                    name = "📅 $title",
                    type = "ALARM",
                    webhookUrlOn = timeStr,
                    folderId = calendarFolder?.id
                )
                // Evita duplicar alarmes se houver dois eventos exatamente na mesma hora
                if (!automationsList.any { a -> a.webhookUrlOn == timeStr && a.folderId == calendarFolder?.id }) {
                    automationsList.add(auto)
                }
            }
        }

        Log.d("BandTrigger", "Sincronização: Foram encontrados $eventosEncontrados eventos nas próximas 24h.")

        // 4. Salvar na memória
        val autoArray = JSONArray()
        for (auto in automationsList) {
            autoArray.put(JSONObject().apply {
                put("id", auto.id); put("name", auto.name); put("type", auto.type)
                put("webhookUrlOn", auto.webhookUrlOn); put("webhookUrlOff", auto.webhookUrlOff)
                put("isToggle", auto.isToggle); put("folderId", auto.folderId); put("alarmDays", auto.alarmDays)
            })
        }
        sharedPrefs.edit().putString("AUTOMATIONS_LIST", autoArray.toString()).apply()

        // 5. Acorda o MediaService para reagendar os novos alarmes no sistema do Android
        val intent = Intent(applicationContext, MediaService::class.java)
        applicationContext.startService(intent)

        return Result.success()
    }
}
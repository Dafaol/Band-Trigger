package com.bandlightconnect.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Quando o alarme dispara, acordamos o MediaService com a ação de alerta
        val serviceIntent = Intent(context, MediaService::class.java).apply {
            action = "ACTION_FIRE_ALARM"
            putExtra("ALARM_NAME", intent.getStringExtra("ALARM_NAME") ?: "Lembrete Band Trigger")
        }
        context.startService(serviceIntent)
    }
}
package com.bandlightconnect.app

import java.util.UUID

data class Automation(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var type: String,
    var webhookUrlOn: String = "",
    var webhookUrlOff: String = "",
    var isToggle: Boolean = false,
    var currentState: Boolean = false,
    var folderId: String? = null,
    var alarmDays: String = "" // NOVO: Salva os dias da semana (ex: "1,2,3,4,5")
)
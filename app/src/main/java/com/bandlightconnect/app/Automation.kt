package com.bandlightconnect.app

import java.util.UUID

data class Automation(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var type: String, // "WEBHOOK", "CAMERA", "AUDIO", "PC_MEDIA"
    var webhookUrlOn: String = "",
    var webhookUrlOff: String = "",
    var isToggle: Boolean = false,
    var currentState: Boolean = false,
    var folderId: String? = null // null = Root (sem pasta)
)
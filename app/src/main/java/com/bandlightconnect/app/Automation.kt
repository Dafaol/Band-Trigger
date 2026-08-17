package com.bandlightconnect.app

import java.util.UUID

data class Automation(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var turnOnUrl: String,
    var turnOffUrl: String,
    var isCurrentlyOn: Boolean = false, // Memória individual de estado!
    var folderId: String? = null // null = Root (sem pasta)
)
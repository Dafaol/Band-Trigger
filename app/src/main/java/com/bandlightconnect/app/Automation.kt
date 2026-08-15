package com.bandlightconnect.app

data class Automation(
    val name: String,
    val turnOnUrl: String,
    val turnOffUrl: String,
    var isCurrentlyOn: Boolean = false // Memória individual de estado!
)
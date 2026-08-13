package com.bandlightconnect.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

class RemoteCameraService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Vazio
    }

    override fun onInterrupt() {
        // Vazio
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    // O "Pulo do Gato": Simulando o botão do fone de ouvido
    fun triggerCameraShutter() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Cria o evento de "Apertar" a tecla do fone de ouvido
        val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK)
        // Cria o evento de "Soltar" a tecla do fone de ouvido
        val eventUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK)

        // Dispara os comandos para o sistema operacional
        audioManager.dispatchMediaKeyEvent(eventDown)
        audioManager.dispatchMediaKeyEvent(eventUp)
    }

    companion object {
        var instance: RemoteCameraService? = null
    }
}
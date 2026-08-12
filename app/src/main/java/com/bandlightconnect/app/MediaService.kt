package com.bandlightconnect.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.util.Log

class MediaService : Service() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        mediaSession = MediaSession(this, "BandLightConnectSession")

        mediaSession?.setCallback(object : MediaSession.Callback() {
            override fun onPlay() {
                Log.d("BandLightConnect", "▶️ PLAY")
                atualizarEstado(PlaybackState.STATE_PLAYING)

                Thread {
                    try {
                        // Vai buscar o link lá na memória do app
                        val memoria = getSharedPreferences("BandLightPrefs", Context.MODE_PRIVATE)
                        val linkSalvo = memoria.getString("URL_LIGAR", "")

                        if (linkSalvo != null && linkSalvo.isNotEmpty()) {
                            val conexao = java.net.URL(linkSalvo).openConnection() as java.net.HttpURLConnection
                            conexao.requestMethod = "GET"
                            Log.d("BandLightConnect", "Status Ligar: ${conexao.responseCode}")
                            conexao.disconnect()
                        }
                    } catch (e: Exception) {
                        Log.e("BandLightConnect", "Erro ao ligar", e)
                    }
                }.start()
            }

            override fun onPause() {
                Log.d("BandLightConnect", "⏸️ PAUSE")
                atualizarEstado(PlaybackState.STATE_PAUSED)

                Thread {
                    try {
                        // Vai buscar o link lá na memória do app
                        val memoria = getSharedPreferences("BandLightPrefs", Context.MODE_PRIVATE)
                        val linkSalvo = memoria.getString("URL_DESLIGAR", "")

                        if (linkSalvo != null && linkSalvo.isNotEmpty()) {
                            val conexao = java.net.URL(linkSalvo).openConnection() as java.net.HttpURLConnection
                            conexao.requestMethod = "GET"
                            Log.d("BandLightConnect", "Status Desligar: ${conexao.responseCode}")
                            conexao.disconnect()
                        }
                    } catch (e: Exception) {
                        Log.e("BandLightConnect", "Erro ao desligar", e)
                    }
                }.start()
            }

            override fun onSkipToNext() {
                Log.d("BandLightConnect", "⏭️ Apertou AVANÇAR -> Ação: AUMENTAR Brilho")
            }

            override fun onSkipToPrevious() {
                Log.d("BandLightConnect", "⏮️ Apertou VOLTAR -> Ação: DIMINUIR Brilho")
            }
        })

        // Inicia o app dizendo que está pausado, para o seu primeiro clique no relógio ser o PLAY
        atualizarEstado(PlaybackState.STATE_PAUSED)
        mediaSession?.isActive = true
    }

    // Função nova: Atualiza o status no seu relógio
    private fun atualizarEstado(estado: Int) {
        val state = PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS)
            .setState(estado, 0, 1.0f)
            .build()

        mediaSession?.setPlaybackState(state)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
    }
}
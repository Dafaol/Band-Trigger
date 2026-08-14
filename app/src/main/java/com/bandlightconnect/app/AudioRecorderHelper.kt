package com.bandlightconnect.app

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioRecorderHelper {
    private var mediaRecorder: MediaRecorder? = null
    var isRecording = false
        private set

    fun startRecording(context: Context, outputDir: File) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "BandTrigger_Audio_$timeStamp.m4a"
        val outputFile = File(outputDir, fileName)

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context) // Passando o Context corretamente para Android 12+
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile.absolutePath)

            try {
                prepare()
                start()
                isRecording = true
                Log.d("BandTrigger", "Recording started: ${outputFile.absolutePath}")
            } catch (e: Exception) {
                Log.e("BandTrigger", "Error starting media recorder", e)
            }
        }
    }

    fun stopRecording() {
        if (isRecording) {
            try {
                mediaRecorder?.stop()
                Log.d("BandTrigger", "Recording stopped")
            } catch (e: Exception) {
                Log.e("BandTrigger", "Error stopping media recorder", e)
            } finally {
                mediaRecorder?.release()
                mediaRecorder = null
                isRecording = false
            }
        }
    }
}
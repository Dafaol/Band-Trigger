package com.bandlightconnect.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val editUrlLigar = findViewById<EditText>(R.id.editUrlLigar)
        val editUrlDesligar = findViewById<EditText>(R.id.editUrlDesligar)
        val btnSalvar = findViewById<Button>(R.id.btnSalvar)

        // Acessa o "bloquinho de notas" (SharedPreferences) do app
        val memoria = getSharedPreferences("BandLightPrefs", Context.MODE_PRIVATE)

        // Se já tiver link salvo de antes, preenche a tela automaticamente
        editUrlLigar.setText(memoria.getString("URL_LIGAR", ""))
        editUrlDesligar.setText(memoria.getString("URL_DESLIGAR", ""))

        // O que acontece ao clicar em Salvar
        btnSalvar.setOnClickListener {
            val editor = memoria.edit()
            editor.putString("URL_LIGAR", editUrlLigar.text.toString())
            editor.putString("URL_DESLIGAR", editUrlDesligar.text.toString())
            editor.apply() // Confirma a gravação

            // Mostra um aviso rápido na tela
            Toast.makeText(this, "Links salvos com sucesso!", Toast.LENGTH_SHORT).show()
        }

        // Liga o serviço fantasma do relógio
        startService(Intent(this, MediaService::class.java))
    }
}
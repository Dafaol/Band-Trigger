package com.bandlightconnect.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private val automationList = mutableListOf<Automation>()
    private lateinit var adapter: AutomationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupRecyclerView()
        loadAutomations()

        // start background service for watch connection
        startService(Intent(this, MediaService::class.java))

        val fabAdd: FloatingActionButton = findViewById(R.id.fabAdicionar)
        fabAdd.setOnClickListener {
            showAddAutomationDialog()
        }
    }

    private fun setupRecyclerView() {
        val recyclerView: RecyclerView = findViewById(R.id.recyclerViewAutomacoes)

        adapter = AutomationAdapter(automationList) { automation, position ->
            showAutomationDetails(automation, position)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun showAutomationDetails(automation: Automation, position: Int) {
        val msg = "Turn ON:\n${automation.turnOnUrl}\n\nTurn OFF:\n${automation.turnOffUrl.ifEmpty { "N/A" }}"

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(automation.name)
            .setMessage(msg)
            .setPositiveButton("Close", null)
            .setNeutralButton("Edit") { _, _ ->
                showEditAutomationDialog(automation, position)
            }
            .setNegativeButton("Delete") { _, _ ->
                automationList.removeAt(position)
                adapter.notifyItemRemoved(position)
                saveAutomations()

                // sync changes with watch
                startService(Intent(this@MainActivity, MediaService::class.java))

                // FEEDBACK VISUAL
                android.widget.Toast.makeText(this@MainActivity, "Automation deleted", android.widget.Toast.LENGTH_SHORT).show()
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
            // highlight edit button in purple to stand out but not alert
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(Color.parseColor("#BB86FC"))
            // highlight delete button in red for safety
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor("#FF5252"))
        }

        dialog.show()
    }

    private fun showEditAutomationDialog(automation: Automation, position: Int) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_nova_automacao, null)

        // Altera o título da janela para refletir que é uma edição
        val textTitle = dialogView.findViewById<android.widget.TextView>(R.id.textDialogTitle)
        textTitle.text = "Edit Automation"

        val editName = dialogView.findViewById<EditText>(R.id.editNome)
        val editUrlOn = dialogView.findViewById<EditText>(R.id.editUrlLigar)
        val editUrlOff = dialogView.findViewById<EditText>(R.id.editUrlDesligar)

        // pre-fill current data
        editName.setText(automation.name)
        editUrlOn.setText(automation.turnOnUrl)
        editUrlOff.setText(automation.turnOffUrl)

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val name = editName.text.toString()
                val urlOn = editUrlOn.text.toString()
                val urlOff = editUrlOff.text.toString()

                if (name.isNotEmpty() && urlOn.isNotEmpty()) {
                    // update item at specific index
                    automationList[position] = Automation(name, urlOn, urlOff)
                    adapter.notifyItemChanged(position)
                    saveAutomations()

                    // sync changes with watch
                    startService(Intent(this@MainActivity, MediaService::class.java))

                    // FEEDBACK VISUAL
                    android.widget.Toast.makeText(this@MainActivity, "Automation updated", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
        }

        dialog.show()
    }

    private fun showAddAutomationDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_nova_automacao, null)
        val editName = dialogView.findViewById<EditText>(R.id.editNome)
        val editUrlOn = dialogView.findViewById<EditText>(R.id.editUrlLigar)
        val editUrlOff = dialogView.findViewById<EditText>(R.id.editUrlDesligar)

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = editName.text.toString()
                val urlOn = editUrlOn.text.toString()
                val urlOff = editUrlOff.text.toString()

                if (name.isNotEmpty() && urlOn.isNotEmpty()) {
                    automationList.add(Automation(name, urlOn, urlOff))
                    adapter.notifyItemInserted(automationList.size - 1)
                    saveAutomations()

                    // sync changes with watch
                    startService(Intent(this@MainActivity, MediaService::class.java))

                    // FEEDBACK VISUAL
                    android.widget.Toast.makeText(this@MainActivity, "Automation added", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
        }

        dialog.show()
    }

    private fun saveAutomations() {
        val sharedPrefs = getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray()

        for (auto in automationList) {
            val jsonObject = JSONObject()
            jsonObject.put("name", auto.name)
            jsonObject.put("turnOnUrl", auto.turnOnUrl)
            jsonObject.put("turnOffUrl", auto.turnOffUrl)
            jsonArray.put(jsonObject)
        }

        sharedPrefs.edit().putString("AUTOMATIONS_LIST", jsonArray.toString()).apply()
    }



    private fun loadAutomations() {
        val sharedPrefs = getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val jsonString = sharedPrefs.getString("AUTOMATIONS_LIST", "[]")
        val jsonArray = JSONArray(jsonString)

        automationList.clear()
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            val name = jsonObject.getString("name")
            val urlOn = jsonObject.getString("turnOnUrl")
            val urlOff = jsonObject.getString("turnOffUrl")
            automationList.add(Automation(name, urlOn, urlOff))
        }
        adapter.notifyDataSetChanged()
    }
}
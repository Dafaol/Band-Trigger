package com.bandlightconnect.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
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

        // setup custom toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        setupRecyclerView()
        loadAutomations()

        startService(Intent(this, MediaService::class.java))

        val fabAdd: FloatingActionButton = findViewById(R.id.fabAdicionar)
        fabAdd.setOnClickListener {
            showAddAutomationDialog()
        }

    }

    // Inflate the 3-dot menu on top right
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    // setup visually if it's checked or not when menu opens
    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val autoFocusItem = menu?.findItem(R.id.action_autofocus)
        val sharedPrefs = getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        autoFocusItem?.isChecked = sharedPrefs.getBoolean("AUTO_FOCUS_ENABLED", false)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_autofocus -> {
                val sharedPrefs = getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
                val isCurrentlyEnabled = sharedPrefs.getBoolean("AUTO_FOCUS_ENABLED", false)

                if (!isCurrentlyEnabled) {
                    // Tenta ativar
                    if (!isNotificationServiceEnabled()) {
                        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle("Permission Required")
                            .setMessage("To automatically take over the watch screen when other media is paused, the app needs Notification Access.\n\nNote: This may impact battery life.")
                            .setPositiveButton("Grant Access") { _, _ ->
                                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                            }
                            .setNegativeButton("Cancel", null)
                            .create()

                        // AQUI ESTÁ A CORREÇÃO DA COR:
                        dialog.setOnShowListener {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.WHITE)
                            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.WHITE)
                        }
                        dialog.show()
                    } else {
                        sharedPrefs.edit().putBoolean("AUTO_FOCUS_ENABLED", true).apply()
                        item.isChecked = true
                        android.widget.Toast.makeText(this, "Auto-Focus Enabled", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Desativa
                    sharedPrefs.edit().putBoolean("AUTO_FOCUS_ENABLED", false).apply()
                    item.isChecked = false
                    android.widget.Toast.makeText(this, "Auto-Focus Disabled", android.widget.Toast.LENGTH_SHORT).show()
                }
                return true
            }
            R.id.action_github -> {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Dafaol/Band-Trigger"))
                startActivity(browserIntent)
                return true
            }
            R.id.action_about -> {
                AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("About Band Trigger")
                    .setMessage("Version 1.1\n\nControl your smart home automations directly from your smartwatch media controls.")
                    .setPositiveButton("Close", null)
                    .show()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
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
                startService(Intent(this@MainActivity, MediaService::class.java))
                android.widget.Toast.makeText(this@MainActivity, "Automation deleted", android.widget.Toast.LENGTH_SHORT).show()
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(Color.parseColor("#BB86FC"))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor("#FF5252"))
        }

        dialog.show()
    }

    private fun showEditAutomationDialog(automation: Automation, position: Int) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_nova_automacao, null)

        val textTitle = dialogView.findViewById<android.widget.TextView>(R.id.textDialogTitle)
        textTitle.text = "Edit Automation"

        val editName = dialogView.findViewById<EditText>(R.id.editNome)
        val editUrlOn = dialogView.findViewById<EditText>(R.id.editUrlLigar)
        val editUrlOff = dialogView.findViewById<EditText>(R.id.editUrlDesligar)

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
                    automationList[position] = Automation(name, urlOn, urlOff)
                    adapter.notifyItemChanged(position)
                    saveAutomations()
                    startService(Intent(this@MainActivity, MediaService::class.java))
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
                    startService(Intent(this@MainActivity, MediaService::class.java))
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

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(pkgName) == true
    }
}
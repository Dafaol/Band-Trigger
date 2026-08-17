package com.bandlightconnect.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AutomationsFragment : Fragment() {

    private val automationList = mutableListOf<Automation>()
    private val foldersList = mutableListOf<Folder>()
    private val foldersMap = mutableMapOf<String, String>()
    private lateinit var adapter: AutomationAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_automations, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadAutomations()
        setupRecyclerView(view)

        requireActivity().startService(Intent(requireContext(), MediaService::class.java))

        val fabAdd: FloatingActionButton = view.findViewById(R.id.fabAdd)
        fabAdd.setOnClickListener {
            showAddOptionsDialog()
        }
    }

    private fun setupRecyclerView(view: View) {
        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerViewAutomations)
        adapter = AutomationAdapter(
            automationList,
            foldersMap,
            onItemClicked = { automation, position -> showAutomationDetails(automation, position) },
            onListChanged = {
                saveAutomations()
                requireActivity().startService(Intent(requireContext(), MediaService::class.java))
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        // Drag and Drop
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                adapter.onItemMove(viewHolder.adapterPosition, target.adapterPosition)
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun showAddOptionsDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_select_action, null)

        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(dialogView)
            .create()

        // Clique na opção de Nova Automação
        dialogView.findViewById<View>(R.id.optionAddAutomation).setOnClickListener {
            dialog.dismiss()
            showAddAutomationDialog()
        }

        // Clique na opção de Nova Pasta
        dialogView.findViewById<View>(R.id.optionCreateFolder).setOnClickListener {
            dialog.dismiss()
            showCreateFolderDialog()
        }

        dialog.show()
    }

    private fun showCreateFolderDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Folder Name (e.g. Smart Home)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("New Folder")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    foldersList.add(Folder(name = name))
                    saveFolders()
                    loadAutomations()
                    Toast.makeText(requireContext(), "Folder created!", Toast.LENGTH_SHORT).show()
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

    private fun showAutomationDetails(automation: Automation, position: Int) {
        val msg = "Turn ON:\n${automation.turnOnUrl}\n\nTurn OFF:\n${automation.turnOffUrl.ifEmpty { "N/A" }}"
        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
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
                requireActivity().startService(Intent(requireContext(), MediaService::class.java))
                Toast.makeText(requireContext(), "Automation deleted", Toast.LENGTH_SHORT).show()
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
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_new_automation, null)
        val textTitle = dialogView.findViewById<android.widget.TextView>(R.id.textDialogTitle)
        textTitle.text = "Edit Automation"

        val editName = dialogView.findViewById<EditText>(R.id.editName)
        val editUrlOn = dialogView.findViewById<EditText>(R.id.editUrlTurnOn)
        val editUrlOff = dialogView.findViewById<EditText>(R.id.editUrlTurnOff)
        val dropdownAction = dialogView.findViewById<AutoCompleteTextView>(R.id.dropdownAction)
        val dropdownFolder = dialogView.findViewById<AutoCompleteTextView>(R.id.dropdownFolder)
        val layoutUrls = dialogView.findViewById<View>(R.id.layoutUrls)

        editName.setText(automation.name)
        editUrlOn.setText(automation.turnOnUrl)
        editUrlOff.setText(automation.turnOffUrl)

        // Setup Actions
        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val isCameraEnabled = sharedPrefs.getBoolean("CAMERA_ENABLED", false)
        val isAudioEnabled = sharedPrefs.getBoolean("AUDIO_ENABLED", false)
        val actionOptions = arrayOf("HTTP Webhook", "Hidden Camera", "Audio Recorder")
        val enabledFlags = booleanArrayOf(true, isCameraEnabled, isAudioEnabled)

        val actAdapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_dropdown_item_1line, actionOptions) {
            override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(pos, convertView, parent) as android.widget.TextView
                v.setTextColor(if (enabledFlags[pos]) Color.WHITE else Color.GRAY)
                return v
            }
        }
        dropdownAction.setAdapter(actAdapter)
        dropdownAction.setText(actionOptions[0], false)
        dropdownAction.setOnItemClickListener { _, _, pos, _ ->
            if (!enabledFlags[pos]) {
                Toast.makeText(requireContext(), "Enable this feature in Settings!", Toast.LENGTH_LONG).show()
                dropdownAction.setText(actionOptions[0], false)
                layoutUrls.visibility = View.VISIBLE
            } else {
                layoutUrls.visibility = if (pos == 1 || pos == 2) View.GONE else View.VISIBLE
            }
        }

        // Setup Folders
        val folderNames = mutableListOf("Root (No Folder)").apply { addAll(foldersList.map { it.name }) }
        val folderAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, folderNames)
        dropdownFolder.setAdapter(folderAdapter)
        val currentFolderName = foldersMap[automation.folderId] ?: "Root (No Folder)"
        dropdownFolder.setText(currentFolderName, false)

        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val name = editName.text.toString()
                var urlOn = editUrlOn.text.toString()
                var urlOff = editUrlOff.text.toString()
                val selectedAction = dropdownAction.text.toString()
                val selectedFolder = dropdownFolder.text.toString()

                val folderId = foldersList.find { it.name == selectedFolder }?.id

                if (selectedAction == "Hidden Camera") { urlOn = "CAMERA"; urlOff = "" }
                else if (selectedAction == "Audio Recorder") { urlOn = "RECORD"; urlOff = "RECORD" }

                if (name.isNotEmpty() && urlOn.isNotEmpty()) {
                    automationList[position] = Automation(automation.id, name, urlOn, urlOff, automation.isCurrentlyOn, folderId)
                    adapter.notifyItemChanged(position)
                    saveAutomations()
                    requireActivity().startService(Intent(requireContext(), MediaService::class.java))
                    Toast.makeText(requireContext(), "Automation updated", Toast.LENGTH_SHORT).show()
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
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_new_automation, null)
        val editName = dialogView.findViewById<EditText>(R.id.editName)
        val editUrlOn = dialogView.findViewById<EditText>(R.id.editUrlTurnOn)
        val editUrlOff = dialogView.findViewById<EditText>(R.id.editUrlTurnOff)
        val layoutUrls = dialogView.findViewById<View>(R.id.layoutUrls)
        val dropdownAction = dialogView.findViewById<AutoCompleteTextView>(R.id.dropdownAction)
        val dropdownFolder = dialogView.findViewById<AutoCompleteTextView>(R.id.dropdownFolder)

        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val isCameraEnabled = sharedPrefs.getBoolean("CAMERA_ENABLED", false)
        val isAudioEnabled = sharedPrefs.getBoolean("AUDIO_ENABLED", false)
        val actionOptions = arrayOf("HTTP Webhook", "Hidden Camera", "Audio Recorder")
        val enabledFlags = booleanArrayOf(true, isCameraEnabled, isAudioEnabled)

        val actAdapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_dropdown_item_1line, actionOptions) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as android.widget.TextView
                view.setTextColor(if (enabledFlags[position]) Color.WHITE else Color.GRAY)
                return view
            }
        }
        dropdownAction.setAdapter(actAdapter)
        dropdownAction.setText(actionOptions[0], false)
        dropdownAction.setOnItemClickListener { _, _, position, _ ->
            if (!enabledFlags[position]) {
                Toast.makeText(requireContext(), "Enable this feature in Settings!", Toast.LENGTH_LONG).show()
                dropdownAction.setText(actionOptions[0], false)
                layoutUrls.visibility = View.VISIBLE
            } else {
                layoutUrls.visibility = if (position == 1 || position == 2) View.GONE else View.VISIBLE
            }
        }

        // Setup Folders
        val folderNames = mutableListOf("Root (No Folder)").apply { addAll(foldersList.map { it.name }) }
        val folderAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, folderNames)
        dropdownFolder.setAdapter(folderAdapter)
        dropdownFolder.setText(folderNames[0], false)

        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = editName.text.toString()
                var urlOn = editUrlOn.text.toString()
                var urlOff = editUrlOff.text.toString()
                val selectedAction = dropdownAction.text.toString()
                val selectedFolder = dropdownFolder.text.toString()

                val folderId = foldersList.find { it.name == selectedFolder }?.id

                if (selectedAction == "Hidden Camera") { urlOn = "CAMERA"; urlOff = "" }
                else if (selectedAction == "Audio Recorder") { urlOn = "RECORD"; urlOff = "RECORD" }

                if (name.isNotEmpty() && urlOn.isNotEmpty()) {
                    automationList.add(Automation(name = name, turnOnUrl = urlOn, turnOffUrl = urlOff, folderId = folderId))
                    adapter.notifyItemInserted(automationList.size - 1)
                    saveAutomations()
                    requireActivity().startService(Intent(requireContext(), MediaService::class.java))
                    Toast.makeText(requireContext(), "Automation added", Toast.LENGTH_SHORT).show()
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
        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (auto in automationList) {
            val jsonObject = JSONObject()
            jsonObject.put("id", auto.id)
            jsonObject.put("name", auto.name)
            jsonObject.put("turnOnUrl", auto.turnOnUrl)
            jsonObject.put("turnOffUrl", auto.turnOffUrl)
            jsonObject.put("isCurrentlyOn", auto.isCurrentlyOn)
            auto.folderId?.let { jsonObject.put("folderId", it) }
            jsonArray.put(jsonObject)
        }
        sharedPrefs.edit().putString("AUTOMATIONS_LIST", jsonArray.toString()).apply()
    }

    private fun saveFolders() {
        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (folder in foldersList) {
            val obj = JSONObject().apply { put("id", folder.id); put("name", folder.name) }
            jsonArray.put(obj)
        }
        sharedPrefs.edit().putString("FOLDERS_LIST", jsonArray.toString()).apply()
    }

    private fun loadAutomations() {
        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)

        foldersList.clear()
        foldersMap.clear()
        try {
            val fArray = JSONArray(sharedPrefs.getString("FOLDERS_LIST", "[]"))
            for (i in 0 until fArray.length()) {
                val obj = fArray.getJSONObject(i)
                val folder = Folder(id = obj.getString("id"), name = obj.getString("name"))
                foldersList.add(folder)
                foldersMap[folder.id] = folder.name
            }
        } catch (e: Exception) { e.printStackTrace() }

        automationList.clear()
        try {
            val jsonArray = JSONArray(sharedPrefs.getString("AUTOMATIONS_LIST", "[]"))
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val id = jsonObject.optString("id", UUID.randomUUID().toString())
                val name = jsonObject.getString("name")
                val urlOn = jsonObject.getString("turnOnUrl")
                val urlOff = jsonObject.getString("turnOffUrl")
                val isCurrentlyOn = jsonObject.optBoolean("isCurrentlyOn", false)
                val folderId = if (jsonObject.has("folderId") && !jsonObject.isNull("folderId")) jsonObject.getString("folderId") else null

                automationList.add(Automation(id, name, urlOn, urlOff, isCurrentlyOn, folderId))
            }
        } catch (e: Exception) { e.printStackTrace() }

        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }
}
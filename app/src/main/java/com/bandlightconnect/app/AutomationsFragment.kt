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

    private val automationsList = mutableListOf<Automation>()
    private val foldersList = mutableListOf<Folder>()
    private val foldersMap = mutableMapOf<String, String>()

    private lateinit var adapter: AutomationAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_automations, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadData()
        setupRecyclerView(view)

        requireActivity().startService(Intent(requireContext(), MediaService::class.java))

        view.findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            showAddOptionsDialog()
        }
    }

    private fun setupRecyclerView(view: View) {
        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerViewAutomations)
        adapter = AutomationAdapter(
            items = mutableListOf(),
            onFolderClicked = { folder -> openFolderDialog(folder) },
            onFolderEditClicked = { folder -> showFolderOptionsDialog(folder) },
            onAutomationClicked = { automation -> showAutomationDetails(automation) },
            onListReordered = { syncRootListsWithAdapter() }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return adapter.onItemMove(viewHolder.adapterPosition, target.adapterPosition)
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                syncRootListsWithAdapter()
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)
        rebuildRootUiList()
    }

    private fun saveRootOrder(items: List<UiItem>) {
        val jsonArray = JSONArray()
        items.forEach { item ->
            when (item) {
                is UiItem.FolderItem -> jsonArray.put("FOLDER_${item.folder.id}")
                is UiItem.AutomationItem -> jsonArray.put("AUTO_${item.automation.id}")
            }
        }
        requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
            .edit().putString("ROOT_UI_ORDER", jsonArray.toString()).apply()
    }

    private fun rebuildRootUiList() {
        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val orderArrayStr = sharedPrefs.getString("ROOT_UI_ORDER", "[]")
        val orderArray = JSONArray(orderArrayStr)

        val displayList = mutableListOf<UiItem>()
        val addedIds = mutableSetOf<String>()

        // 1. Carrega os itens na ordem misturada exata que o usuário salvou
        for (i in 0 until orderArray.length()) {
            val idStr = orderArray.getString(i)
            if (idStr.startsWith("FOLDER_")) {
                val fId = idStr.removePrefix("FOLDER_")
                val folder = foldersList.find { it.id == fId }
                if (folder != null) {
                    displayList.add(UiItem.FolderItem(folder))
                    addedIds.add(fId)
                }
            } else if (idStr.startsWith("AUTO_")) {
                val aId = idStr.removePrefix("AUTO_")
                val auto = automationsList.find { it.id == aId }
                if (auto != null && auto.folderId == null) {
                    displayList.add(UiItem.AutomationItem(auto))
                    addedIds.add(aId)
                }
            }
        }

        // 2. Adiciona novos itens que ainda não têm ordem no final da lista
        foldersList.forEach { if (!addedIds.contains(it.id)) displayList.add(UiItem.FolderItem(it)) }
        automationsList.filter { it.folderId == null }.forEach { if (!addedIds.contains(it.id)) displayList.add(UiItem.AutomationItem(it)) }

        adapter.updateData(displayList)
    }

    private fun syncRootListsWithAdapter() {
        val currentUiItems = adapter.getItems()

        saveRootOrder(currentUiItems)

        val newFolders = currentUiItems.filterIsInstance<UiItem.FolderItem>().map { it.folder }
        val newRootAutos = currentUiItems.filterIsInstance<UiItem.AutomationItem>().map { it.automation }

        foldersList.clear()
        foldersList.addAll(newFolders)

        val folderAutos = automationsList.filter { it.folderId != null }
        automationsList.clear()
        automationsList.addAll(newRootAutos)
        automationsList.addAll(folderAutos)

        saveFolders()
        saveAutomations()
        requireActivity().startService(Intent(requireContext(), MediaService::class.java))
    }

    private fun showFolderOptionsDialog(folder: Folder) {
        val dialog = android.app.Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_folder_options)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        dialog.findViewById<android.widget.TextView>(R.id.tvOptionsTitle).text = folder.name

        dialog.findViewById<View>(R.id.optionRenameFolder).setOnClickListener {
            dialog.dismiss()
            showRenameFolderDialog(folder)
        }
        dialog.findViewById<View>(R.id.optionDeleteFolder).setOnClickListener {
            dialog.dismiss()
            showDeleteFolderDialog(folder)
        }
        dialog.show()
    }

    private fun showRenameFolderDialog(folder: Folder) {
        val input = EditText(requireContext()).apply {
            setText(folder.name)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Rename Folder")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    folder.name = newName
                    foldersMap[folder.id] = newName
                    saveFolders()
                    rebuildRootUiList()
                    requireActivity().startService(Intent(requireContext(), MediaService::class.java))
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

    private fun showDeleteFolderDialog(folder: Folder) {
        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Delete Folder")
            .setMessage("Are you sure you want to delete '${folder.name}'?\n\nSafeguard: All automations inside it will be safely moved to the Root.")
            .setPositiveButton("Delete") { _, _ ->
                automationsList.filter { it.folderId == folder.id }.forEach { it.folderId = null }

                foldersList.removeIf { it.id == folder.id }
                foldersMap.remove(folder.id)

                saveFolders()
                saveAutomations()
                rebuildRootUiList()
                requireActivity().startService(Intent(requireContext(), MediaService::class.java))
                Toast.makeText(requireContext(), "Folder deleted and automations moved to root", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#FF5252"))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
        }
        dialog.show()
    }

    private fun openFolderDialog(folder: Folder) {
        val dialog = android.app.Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_folder_view)

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        val tvTitle = dialog.findViewById<android.widget.TextView>(R.id.tvFolderTitle)
        val btnBack = dialog.findViewById<android.widget.ImageButton>(R.id.btnFolderBack)
        val btnAdd = dialog.findViewById<View>(R.id.btnFolderAdd)
        val rvFolder = dialog.findViewById<RecyclerView>(R.id.rvFolderAutomations)

        tvTitle.text = folder.name
        btnBack.setOnClickListener { dialog.dismiss() }

        btnAdd.setOnClickListener {
            dialog.dismiss()
            showAddAutomationDialog(folder.id)
        }

        val folderAutos = automationsList.filter { it.folderId == folder.id }.map { UiItem.AutomationItem(it) }.toMutableList()

        lateinit var folderAdapter: AutomationAdapter

        folderAdapter = AutomationAdapter(
            items = folderAutos as MutableList<UiItem>,
            onFolderClicked = { },
            onFolderEditClicked = { },
            onAutomationClicked = { auto ->
                dialog.dismiss()
                showAutomationDetails(auto)
            },
            onListReordered = {
                val newOrder = folderAdapter.getItems().map { item -> (item as UiItem.AutomationItem).automation }
                val otherAutos = automationsList.filter { auto -> auto.folderId != folder.id }
                automationsList.clear()
                automationsList.addAll(otherAutos)
                automationsList.addAll(newOrder)

                saveAutomations()
                requireActivity().startService(Intent(requireContext(), MediaService::class.java))
            }
        )

        rvFolder.layoutManager = LinearLayoutManager(requireContext())
        rvFolder.adapter = folderAdapter

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return folderAdapter.onItemMove(viewHolder.adapterPosition, target.adapterPosition)
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val newOrder = folderAdapter.getItems().map { (it as UiItem.AutomationItem).automation }
                val otherAutos = automationsList.filter { auto -> auto.folderId != folder.id }
                automationsList.clear()
                automationsList.addAll(otherAutos)
                automationsList.addAll(newOrder)

                saveAutomations()
                requireActivity().startService(Intent(requireContext(), MediaService::class.java))
            }
        })
        itemTouchHelper.attachToRecyclerView(rvFolder)

        dialog.show()
    }

    private fun showAddOptionsDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_select_action, null)
        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(dialogView).create()

        dialogView.findViewById<View>(R.id.optionAddAutomation).setOnClickListener {
            dialog.dismiss()
            showAddAutomationDialog(null)
        }
        dialogView.findViewById<View>(R.id.optionCreateFolder).setOnClickListener {
            dialog.dismiss()
            showCreateFolderDialog()
        }
        dialog.show()
    }

    private fun showCreateFolderDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Folder Name"
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
                    loadData()
                    rebuildRootUiList()
                    requireActivity().startService(Intent(requireContext(), MediaService::class.java))
                }
            }.setNegativeButton("Cancel", null).create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
        }
        dialog.show()
    }

    private fun showAutomationDetails(automation: Automation) {
        val msg = "Turn ON:\n${automation.webhookUrlOn}\n\nTurn OFF:\n${automation.webhookUrlOff.ifEmpty { "N/A" }}"
        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(automation.name)
            .setMessage(msg)
            .setPositiveButton("Close", null)
            .setNeutralButton("Edit") { _, _ -> showEditAutomationDialog(automation) }
            .setNegativeButton("Delete") { _, _ ->
                automationsList.remove(automation)
                saveAutomations()
                rebuildRootUiList()
                requireActivity().startService(Intent(requireContext(), MediaService::class.java))
            }.create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(Color.parseColor("#BB86FC"))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor("#FF5252"))
        }
        dialog.show()
    }

    private fun showEditAutomationDialog(automation: Automation) {
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
        editUrlOn.setText(automation.webhookUrlOn)
        editUrlOff.setText(automation.webhookUrlOff)

        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val actionOptions = arrayOf("HTTP Webhook", "Hidden Camera", "Audio Recorder")
        val enabledFlags = booleanArrayOf(true, sharedPrefs.getBoolean("CAMERA_ENABLED", false), sharedPrefs.getBoolean("AUDIO_ENABLED", false))

        dropdownAction.setAdapter(object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_dropdown_item_1line, actionOptions) {
            override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getView(pos, convertView, parent) as android.widget.TextView).apply {
                    setTextColor(if (enabledFlags[pos]) Color.WHITE else Color.GRAY)
                }
            }
            override fun getDropDownView(pos: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getDropDownView(pos, convertView, parent) as android.widget.TextView).apply {
                    setTextColor(if (enabledFlags[pos]) Color.WHITE else Color.GRAY)
                }
            }
        })

        val typeMap = mapOf("WEBHOOK" to 0, "CAMERA" to 1, "AUDIO" to 2, "PC_MEDIA" to 0)
        val typeIndex = typeMap[automation.type] ?: 0
        dropdownAction.setText(actionOptions[typeIndex], false)
        layoutUrls.visibility = if (typeIndex == 1 || typeIndex == 2) View.GONE else View.VISIBLE

        dropdownAction.setOnItemClickListener { _, _, pos, _ ->
            if (!enabledFlags[pos]) {
                Toast.makeText(requireContext(), "Enable this feature in Settings!", Toast.LENGTH_LONG).show()
                dropdownAction.setText(actionOptions[0], false)
                layoutUrls.visibility = View.VISIBLE
            } else {
                layoutUrls.visibility = if (pos == 1 || pos == 2) View.GONE else View.VISIBLE
            }
        }

        val folderNames = mutableListOf("Root (No Folder)").apply { addAll(foldersList.map { it.name }) }
        dropdownFolder.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, folderNames))
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

                var autoType = "WEBHOOK"
                if (selectedAction == "Hidden Camera") { urlOn = "CAMERA"; urlOff = ""; autoType = "CAMERA" }
                else if (selectedAction == "Audio Recorder") { urlOn = "RECORD"; urlOff = "RECORD"; autoType = "AUDIO" }

                if (name.isNotEmpty() && urlOn.isNotEmpty()) {
                    automation.name = name
                    automation.type = autoType
                    automation.webhookUrlOn = urlOn
                    automation.webhookUrlOff = urlOff
                    automation.isToggle = urlOff.isNotEmpty()
                    automation.folderId = folderId

                    saveAutomations()
                    rebuildRootUiList()
                    requireActivity().startService(Intent(requireContext(), MediaService::class.java))
                }
            }.setNegativeButton("Cancel", null).create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
        }
        dialog.show()
    }

    private fun showAddAutomationDialog(preSelectedFolderId: String? = null) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_new_automation, null)
        val editName = dialogView.findViewById<EditText>(R.id.editName)
        val editUrlOn = dialogView.findViewById<EditText>(R.id.editUrlTurnOn)
        val editUrlOff = dialogView.findViewById<EditText>(R.id.editUrlTurnOff)
        val layoutUrls = dialogView.findViewById<View>(R.id.layoutUrls)
        val dropdownAction = dialogView.findViewById<AutoCompleteTextView>(R.id.dropdownAction)
        val dropdownFolder = dialogView.findViewById<AutoCompleteTextView>(R.id.dropdownFolder)

        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val actionOptions = arrayOf("HTTP Webhook", "Hidden Camera", "Audio Recorder")
        val enabledFlags = booleanArrayOf(true, sharedPrefs.getBoolean("CAMERA_ENABLED", false), sharedPrefs.getBoolean("AUDIO_ENABLED", false))

        dropdownAction.setAdapter(object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_dropdown_item_1line, actionOptions) {
            override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getView(pos, convertView, parent) as android.widget.TextView).apply {
                    setTextColor(if (enabledFlags[pos]) Color.WHITE else Color.GRAY)
                }
            }
            override fun getDropDownView(pos: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getDropDownView(pos, convertView, parent) as android.widget.TextView).apply {
                    setTextColor(if (enabledFlags[pos]) Color.WHITE else Color.GRAY)
                }
            }
        })
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

        val folderNames = mutableListOf("Root (No Folder)").apply { addAll(foldersList.map { it.name }) }
        dropdownFolder.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, folderNames))

        val defaultFolderName = if (preSelectedFolderId != null) foldersMap[preSelectedFolderId] else "Root (No Folder)"
        dropdownFolder.setText(defaultFolderName ?: "Root (No Folder)", false)

        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = editName.text.toString()
                var urlOn = editUrlOn.text.toString()
                var urlOff = editUrlOff.text.toString()
                val selectedAction = dropdownAction.text.toString()
                val selectedFolder = dropdownFolder.text.toString()

                val folderId = foldersList.find { it.name == selectedFolder }?.id

                var autoType = "WEBHOOK"
                if (selectedAction == "Hidden Camera") { urlOn = "CAMERA"; urlOff = ""; autoType = "CAMERA" }
                else if (selectedAction == "Audio Recorder") { urlOn = "RECORD"; urlOff = "RECORD"; autoType = "AUDIO" }

                if (name.isNotEmpty() && urlOn.isNotEmpty()) {
                    automationsList.add(Automation(
                        name = name,
                        type = autoType,
                        webhookUrlOn = urlOn,
                        webhookUrlOff = urlOff,
                        isToggle = urlOff.isNotEmpty(),
                        folderId = folderId
                    ))
                    saveAutomations()
                    rebuildRootUiList()
                    requireActivity().startService(Intent(requireContext(), MediaService::class.java))

                    // REABRE A PASTA AUTOMATICAMENTE APÓS SALVAR
                    if (preSelectedFolderId != null) {
                        val folderToReopen = foldersList.find { it.id == preSelectedFolderId }
                        if (folderToReopen != null) openFolderDialog(folderToReopen)
                    }
                }
            }.setNegativeButton("Cancel", null).create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
        }
        dialog.show()
    }

    private fun loadData() {
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

        automationsList.clear()
        try {
            val jsonArray = JSONArray(sharedPrefs.getString("AUTOMATIONS_LIST", "[]"))
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                automationsList.add(
                    Automation(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.getString("name"),
                        type = obj.optString("type", "WEBHOOK"),
                        webhookUrlOn = obj.optString("webhookUrlOn", obj.optString("turnOnUrl", "")),
                        webhookUrlOff = obj.optString("webhookUrlOff", obj.optString("turnOffUrl", "")),
                        isToggle = obj.optBoolean("isToggle", false),
                        currentState = obj.optBoolean("currentState", false),
                        folderId = if (obj.has("folderId") && !obj.isNull("folderId")) obj.getString("folderId") else null
                    )
                )
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun saveAutomations() {
        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (auto in automationsList) {
            jsonArray.put(JSONObject().apply {
                put("id", auto.id)
                put("name", auto.name)
                put("type", auto.type)
                put("webhookUrlOn", auto.webhookUrlOn)
                put("webhookUrlOff", auto.webhookUrlOff)
                put("isToggle", auto.isToggle)
                put("currentState", auto.currentState)
                put("folderId", auto.folderId)
            })
        }
        sharedPrefs.edit().putString("AUTOMATIONS_LIST", jsonArray.toString()).apply()
    }

    private fun saveFolders() {
        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (folder in foldersList) {
            jsonArray.put(JSONObject().apply { put("id", folder.id); put("name", folder.name) })
        }
        sharedPrefs.edit().putString("FOLDERS_LIST", jsonArray.toString()).apply()
    }
}
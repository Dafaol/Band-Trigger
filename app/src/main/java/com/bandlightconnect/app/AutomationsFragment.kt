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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
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

    override fun onResume() {
        super.onResume()
        loadData()
        rebuildRootUiList()
        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val isFocusEnabled = sharedPrefs.getBoolean("AUTO_FOCUS_ENABLED", false)
        view?.findViewById<View>(R.id.cardWarningFocus)?.visibility = if (isFocusEnabled) View.GONE else View.VISIBLE
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

        dialog.findViewById<TextView>(R.id.tvOptionsTitle).text = folder.name

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
        val dialog = android.app.Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_create_folder)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        dialog.findViewById<TextView>(R.id.tvFolderDialogTitle).text = "Rename Folder"
        val editName = dialog.findViewById<EditText>(R.id.editFolderName)
        editName.setText(folder.name)

        dialog.findViewById<View>(R.id.btnCancelFolder).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<View>(R.id.btnSaveFolder).setOnClickListener {
            val newName = editName.text.toString().trim()
            if (newName.isNotEmpty()) {
                folder.name = newName
                foldersMap[folder.id] = newName
                saveFolders()
                rebuildRootUiList()
                requireActivity().startService(Intent(requireContext(), MediaService::class.java))
                dialog.dismiss()
            }
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
            }.setNegativeButton("Cancel", null).create()

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

        dialog.findViewById<TextView>(R.id.tvFolderTitle).text = folder.name
        dialog.findViewById<View>(R.id.btnFolderBack).setOnClickListener { dialog.dismiss() }

        dialog.findViewById<View>(R.id.btnFolderAdd).setOnClickListener {
            dialog.dismiss()
            showAddEditAutomationDialog(null, folder.id)
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

        val rvFolder = dialog.findViewById<RecyclerView>(R.id.rvFolderAutomations)
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
        val dialog = android.app.Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_select_action)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        dialog.findViewById<View>(R.id.optionAddAutomation).setOnClickListener {
            dialog.dismiss()
            showAddEditAutomationDialog(null, null)
        }
        dialog.findViewById<View>(R.id.optionCreateFolder).setOnClickListener {
            dialog.dismiss()
            showCreateFolderDialog()
        }
        dialog.show()
    }

    private fun showCreateFolderDialog() {
        val dialog = android.app.Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_create_folder)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        val editName = dialog.findViewById<EditText>(R.id.editFolderName)

        dialog.findViewById<View>(R.id.btnCancelFolder).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<View>(R.id.btnSaveFolder).setOnClickListener {
            val name = editName.text.toString().trim()
            if (name.isNotEmpty()) {
                foldersList.add(Folder(name = name))
                saveFolders()
                loadData()
                rebuildRootUiList()
                requireActivity().startService(Intent(requireContext(), MediaService::class.java))
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showAutomationDetails(automation: Automation) {
        val msg = if (automation.type.equals("COUNTER", ignoreCase = true)) {
            "This is a Counter automation.\nYou can reset the current count to zero below."
        } else if (automation.type.equals("ALARM", ignoreCase = true)) {
            val repeatStr = if (automation.alarmDays.isNotEmpty()) "\nRepeat days: ${getDaysString(automation.alarmDays)}" else "\nRepeat: Never"
            "Smartband Alarm.\nTime: ${automation.webhookUrlOn}" + repeatStr
        } else {
            "Turn ON:\n${automation.webhookUrlOn}\n\nTurn OFF:\n${automation.webhookUrlOff.ifEmpty { "N/A" }}"
        }

        val dialogBuilder = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(automation.name)
            .setMessage(msg)
            .setNeutralButton("Edit") { _, _ -> showAddEditAutomationDialog(automation, null) }
            .setNegativeButton("Delete") { _, _ ->
                automationsList.remove(automation)
                saveAutomations()
                rebuildRootUiList()
                requireActivity().startService(Intent(requireContext(), MediaService::class.java))
            }

        if (automation.type.equals("COUNTER", ignoreCase = true)) {
            dialogBuilder.setPositiveButton("Reset") { _, _ ->
                val intent = Intent(requireContext(), MediaService::class.java).apply { action = "ACTION_RESET_COUNTER" }
                requireContext().startService(intent)
                Toast.makeText(requireContext(), "Counter reset to 0", Toast.LENGTH_SHORT).show()
            }
        } else {
            dialogBuilder.setPositiveButton("Close", null)
        }

        val dialog = dialogBuilder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(Color.parseColor("#BB86FC"))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor("#FF5252"))
        }
        dialog.show()
    }

    private fun showAddEditAutomationDialog(existingAutomation: Automation?, preSelectedFolderId: String?) {
        val dialog = android.app.Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_new_automation)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        dialog.findViewById<TextView>(R.id.textDialogTitle).text = if (existingAutomation == null) "New Automation" else "Edit Automation"

        val editName = dialog.findViewById<EditText>(R.id.editName)
        val editUrlOn = dialog.findViewById<EditText>(R.id.editUrlTurnOn)
        val editUrlOff = dialog.findViewById<EditText>(R.id.editUrlTurnOff)
        val layoutUrls = dialog.findViewById<View>(R.id.layoutUrls)
        val layoutAlarmConfig = dialog.findViewById<View>(R.id.layoutAlarmConfig)
        val btnSelectTime = dialog.findViewById<MaterialButton>(R.id.btnSelectTime)
        val btnSelectDays = dialog.findViewById<MaterialButton>(R.id.btnSelectDays)
        val dropdownAction = dialog.findViewById<AutoCompleteTextView>(R.id.dropdownAction)
        val dropdownFolder = dialog.findViewById<AutoCompleteTextView>(R.id.dropdownFolder)

        var selectedAlarmTime = ""
        val daysShort = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val selectedDays = BooleanArray(7)

        fun updateDaysText() {
            val picked = mutableListOf<String>()
            for (i in selectedDays.indices) {
                if (selectedDays[i]) picked.add(daysShort[i])
            }
            btnSelectDays.text = if (picked.isEmpty()) "Repeat: Never" else "Repeat: ${picked.joinToString(", ")}"
        }

        btnSelectDays.setOnClickListener {
            val daysDialog = android.app.Dialog(requireContext())
            daysDialog.setContentView(R.layout.dialog_select_days)
            daysDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            daysDialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

            val chks = arrayOf(
                daysDialog.findViewById<CheckBox>(R.id.chkDay0), daysDialog.findViewById<CheckBox>(R.id.chkDay1),
                daysDialog.findViewById<CheckBox>(R.id.chkDay2), daysDialog.findViewById<CheckBox>(R.id.chkDay3),
                daysDialog.findViewById<CheckBox>(R.id.chkDay4), daysDialog.findViewById<CheckBox>(R.id.chkDay5),
                daysDialog.findViewById<CheckBox>(R.id.chkDay6)
            )

            for (i in selectedDays.indices) {
                chks[i].isChecked = selectedDays[i]
            }

            daysDialog.findViewById<View>(R.id.btnCancelDays).setOnClickListener {
                daysDialog.dismiss()
            }
            daysDialog.findViewById<View>(R.id.btnSaveDays).setOnClickListener {
                for (i in selectedDays.indices) {
                    selectedDays[i] = chks[i].isChecked
                }
                updateDaysText()
                daysDialog.dismiss()
            }
            daysDialog.show()
        }

        btnSelectTime.setOnClickListener {
            val c = java.util.Calendar.getInstance()
            android.app.TimePickerDialog(requireContext(), { _, hour, minute ->
                selectedAlarmTime = String.format(java.util.Locale.getDefault(), "%02d:%02d", hour, minute)
                btnSelectTime.text = selectedAlarmTime
            }, c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE), true).show()
        }

        if (existingAutomation != null) {
            editName.setText(existingAutomation.name)
            if (existingAutomation.type == "ALARM") {
                selectedAlarmTime = existingAutomation.webhookUrlOn
                btnSelectTime.text = if (selectedAlarmTime.isNotEmpty()) selectedAlarmTime else "Tap to Set Time"
                val daysList = existingAutomation.alarmDays.split(",").filter { it.isNotEmpty() }
                daysList.forEach { dayStr ->
                    val dayInt = dayStr.toIntOrNull()
                    if (dayInt != null && dayInt in 1..7) selectedDays[dayInt - 1] = true
                }
                updateDaysText()
            } else {
                editUrlOn.setText(existingAutomation.webhookUrlOn)
                editUrlOff.setText(existingAutomation.webhookUrlOff)
            }
        }

        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val actionOptions = arrayOf("HTTP Webhook", "Hidden Camera", "Audio Recorder", "Wake on LAN (PC)", "Counter", "Alarm (Smartband)")
        val enabledFlags = booleanArrayOf(true, sharedPrefs.getBoolean("CAMERA_ENABLED", false), sharedPrefs.getBoolean("AUDIO_ENABLED", false), true, true, true)

        dropdownAction.setAdapter(object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_dropdown_item_1line, actionOptions) {
            override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getView(pos, convertView, parent) as TextView).apply { setTextColor(if (enabledFlags[pos]) Color.WHITE else Color.GRAY) }
            }
            override fun getDropDownView(pos: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getDropDownView(pos, convertView, parent) as TextView).apply { setTextColor(if (enabledFlags[pos]) Color.WHITE else Color.GRAY) }
            }
        })

        val typeMap = mapOf("WEBHOOK" to 0, "CAMERA" to 1, "AUDIO" to 2, "WOL" to 3, "PC_MEDIA" to 0, "COUNTER" to 4, "ALARM" to 5)
        val typeIndex = if (existingAutomation != null) typeMap[existingAutomation.type] ?: 0 else 0
        dropdownAction.setText(actionOptions[typeIndex], false)

        fun updateLayoutVisibility(pos: Int) {
            layoutAlarmConfig.visibility = if (pos == 5) View.VISIBLE else View.GONE
            layoutUrls.visibility = if (pos == 1 || pos == 2 || pos == 4 || pos == 5) View.GONE else View.VISIBLE
            if (pos == 3) {
                editUrlOn.hint = "PC MAC Address (e.g. 1A:2B:3C:4D:5E:6F)"
                editUrlOff.visibility = View.GONE
            } else if (pos == 0) {
                editUrlOn.hint = "Turn On URL (Webhook)"
                editUrlOff.visibility = View.VISIBLE
            }
        }
        updateLayoutVisibility(typeIndex)

        dropdownAction.setOnItemClickListener { _, _, pos, _ ->
            if (!enabledFlags[pos]) {
                Toast.makeText(requireContext(), "Enable this feature in Settings!", Toast.LENGTH_LONG).show()
                dropdownAction.setText(actionOptions[0], false)
                updateLayoutVisibility(0)
            } else {
                updateLayoutVisibility(pos)
            }
        }

        val folderNames = mutableListOf("Root (No Folder)").apply { addAll(foldersList.map { it.name }) }
        dropdownFolder.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, folderNames))
        val initialFolderId = existingAutomation?.folderId ?: preSelectedFolderId
        dropdownFolder.setText(foldersMap[initialFolderId] ?: "Root (No Folder)", false)

        dialog.findViewById<View>(R.id.btnCancelAuto).setOnClickListener { dialog.dismiss() }

        dialog.findViewById<View>(R.id.btnSaveAuto).setOnClickListener {
            val name = editName.text.toString()
            var urlOn = editUrlOn.text.toString()
            var urlOff = editUrlOff.text.toString()
            var savedAlarmDays = ""
            val selectedAction = dropdownAction.text.toString()
            val folderId = foldersList.find { it.name == dropdownFolder.text.toString() }?.id
            var autoType = "WEBHOOK"

            if (selectedAction == "Hidden Camera") { urlOn = "CAMERA"; urlOff = ""; autoType = "CAMERA" }
            else if (selectedAction == "Audio Recorder") { urlOn = "RECORD"; urlOff = "RECORD"; autoType = "AUDIO" }
            else if (selectedAction == "Wake on LAN (PC)") { autoType = "WOL" }
            else if (selectedAction == "Counter") { urlOn = "COUNT"; urlOff = ""; autoType = "COUNTER" }
            else if (selectedAction == "Alarm (Smartband)") {
                autoType = "ALARM"
                urlOn = selectedAlarmTime
                urlOff = ""
                val selectedDaysList = mutableListOf<Int>()
                for (i in selectedDays.indices) { if (selectedDays[i]) selectedDaysList.add(i + 1) }
                savedAlarmDays = selectedDaysList.joinToString(",")
            }

            if (name.isNotEmpty() && urlOn.isNotEmpty()) {
                if (existingAutomation == null) {
                    automationsList.add(Automation(
                        name = name, type = autoType, webhookUrlOn = urlOn, webhookUrlOff = urlOff,
                        isToggle = urlOff.isNotEmpty(), folderId = folderId, alarmDays = savedAlarmDays
                    ))
                } else {
                    existingAutomation.name = name; existingAutomation.type = autoType
                    existingAutomation.webhookUrlOn = urlOn; existingAutomation.webhookUrlOff = urlOff
                    existingAutomation.isToggle = urlOff.isNotEmpty(); existingAutomation.folderId = folderId
                    existingAutomation.alarmDays = savedAlarmDays
                }
                saveAutomations()
                rebuildRootUiList()
                requireActivity().startService(Intent(requireContext(), MediaService::class.java))

                if (existingAutomation == null && preSelectedFolderId != null) {
                    val folderToReopen = foldersList.find { it.id == preSelectedFolderId }
                    if (folderToReopen != null) openFolderDialog(folderToReopen)
                }
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Name and Time/URL cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun getDaysString(daysStr: String): String {
        val daysMap = mapOf("1" to "Sun", "2" to "Mon", "3" to "Tue", "4" to "Wed", "5" to "Thu", "6" to "Fri", "7" to "Sat")
        return daysStr.split(",").mapNotNull { daysMap[it] }.joinToString(", ")
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
                        folderId = if (obj.has("folderId") && !obj.isNull("folderId")) obj.getString("folderId") else null,
                        alarmDays = obj.optString("alarmDays", "")
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
                put("alarmDays", auto.alarmDays)
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
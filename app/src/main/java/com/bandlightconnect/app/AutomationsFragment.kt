package com.bandlightconnect.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONArray
import org.json.JSONObject

class AutomationsFragment : Fragment() {

    private val automationList = mutableListOf<Automation>()
    private lateinit var adapter: AutomationAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_automations, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView(view)
        loadAutomations()

        requireActivity().startService(Intent(requireContext(), MediaService::class.java))

        val fabAdd: FloatingActionButton = view.findViewById(R.id.fabAdd)
        fabAdd.setOnClickListener {
            showAddAutomationDialog()
        }
    }

    private fun setupRecyclerView(view: View) {
        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerViewAutomations)
        adapter = AutomationAdapter(automationList) { automation, position ->
            showAutomationDetails(automation, position)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
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

        editName.setText(automation.name)
        editUrlOn.setText(automation.turnOnUrl)
        editUrlOff.setText(automation.turnOffUrl)

        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val name = editName.text.toString()
                val urlOn = editUrlOn.text.toString()
                val urlOff = editUrlOff.text.toString()

                if (name.isNotEmpty() && urlOn.isNotEmpty()) {
                    automationList[position] = Automation(name, urlOn, urlOff)
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

        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = editName.text.toString()
                val urlOn = editUrlOn.text.toString()
                val urlOff = editUrlOff.text.toString()

                if (name.isNotEmpty() && urlOn.isNotEmpty()) {
                    automationList.add(Automation(name, urlOn, urlOff))
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
            jsonObject.put("name", auto.name)
            jsonObject.put("turnOnUrl", auto.turnOnUrl)
            jsonObject.put("turnOffUrl", auto.turnOffUrl)
            jsonArray.put(jsonObject)
        }
        sharedPrefs.edit().putString("AUTOMATIONS_LIST", jsonArray.toString()).apply()
    }

    private fun loadAutomations() {
        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
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
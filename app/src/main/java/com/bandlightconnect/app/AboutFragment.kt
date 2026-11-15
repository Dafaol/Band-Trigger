package com.bandlightconnect.app

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

class AboutFragment : Fragment() {

    private var secretClickCount = 0
    private var lastSecretClickTime = 0L
    private val uiHandler = Handler(Looper.getMainLooper())

    private val requestBackgroundLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        showFuelTrackerDialog()
    }

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(requireContext(), "Select 'Allow all the time' so GPS works with the screen locked!", Toast.LENGTH_LONG).show()
                requestBackgroundLocationPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                showFuelTrackerDialog()
            }
        } else {
            Toast.makeText(requireContext(), "GPS permission is required for Fuel Tracker", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val btnOpenFuelTracker = view.findViewById<MaterialButton>(R.id.btnOpenFuelTracker)

        val textVersion: TextView = view.findViewById(R.id.textVersion)
        try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            textVersion.text = "Version ${pInfo.versionName}"
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        updateFuelButtonVisibility(view)

        btnOpenFuelTracker.setOnClickListener {
            openFuelTrackerWithPermissionCheck()
        }

        view.findViewById<View>(R.id.cardCreatorBadge).setOnClickListener {
            val isAlreadyUnlocked = sharedPrefs.getBoolean("FUEL_UNLOCKED", false)
            if (isAlreadyUnlocked) {
                Toast.makeText(requireContext(), "No need, Fuel Tracker is already enabled!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val now = System.currentTimeMillis()
            if (now - lastSecretClickTime > 2000) {
                secretClickCount = 0
            }
            lastSecretClickTime = now
            secretClickCount++

            if (secretClickCount in 4..6) {
                val remaining = 7 - secretClickCount
                Toast.makeText(requireContext(), "You are now $remaining steps away from Fuel Tracker.", Toast.LENGTH_SHORT).show()
            } else if (secretClickCount >= 7) {
                secretClickCount = 0
                sharedPrefs.edit().putBoolean("FUEL_UNLOCKED", true).apply()
                ensureFuelAutomationExists(sharedPrefs)
                updateFuelButtonVisibility(view)
                Toast.makeText(requireContext(), "Fuel Tracker mode has been enabled!", Toast.LENGTH_SHORT).show()
                openFuelTrackerWithPermissionCheck()
            }
        }

        view.findViewById<MaterialButton>(R.id.btnGithub).setOnClickListener {
            openUrl("https://github.com/Dafaol/Band-Trigger")
        }

        view.findViewById<MaterialButton>(R.id.btnArtstation).setOnClickListener {
            openUrl("https://www.artstation.com/dafaol_creations")
        }

        view.findViewById<MaterialButton>(R.id.btnDonate).setOnClickListener {
            openUrl("https://buymeacoffee.com/dafaol")
        }
    }

    override fun onResume() {
        super.onResume()
        view?.let { updateFuelButtonVisibility(it) }
    }

    private fun updateFuelButtonVisibility(rootView: View) {
        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val isUnlocked = sharedPrefs.getBoolean("FUEL_UNLOCKED", false)
        rootView.findViewById<View>(R.id.btnOpenFuelTracker)?.visibility = if (isUnlocked) View.VISIBLE else View.GONE
    }

    private fun openFuelTrackerWithPermissionCheck() {
        val hasFine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine) {
            requestLocationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(requireContext(), "Select 'Allow all the time' so GPS works with the screen locked!", Toast.LENGTH_LONG).show()
            requestBackgroundLocationPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            return
        }

        showFuelTrackerDialog()
    }

    private fun ensureFuelAutomationExists(sharedPrefs: SharedPreferences) {
        try {
            val jsonArray = JSONArray(sharedPrefs.getString("AUTOMATIONS_LIST", "[]"))
            var hasFuel = false
            for (i in 0 until jsonArray.length()) {
                if (jsonArray.getJSONObject(i).optString("type") == "FUEL") {
                    hasFuel = true
                    break
                }
            }
            if (!hasFuel) {
                jsonArray.put(JSONObject().apply {
                    put("id", UUID.randomUUID().toString())
                    put("name", "Fuel Tracker")
                    put("type", "FUEL")
                    put("webhookUrlOn", "FUEL")
                    put("webhookUrlOff", "FUEL")
                    put("isToggle", true)
                    put("currentState", false)
                    put("folderId", JSONObject.NULL)
                    put("alarmDays", "")
                })
                sharedPrefs.edit().putString("AUTOMATIONS_LIST", jsonArray.toString()).apply()
                requireActivity().startService(Intent(requireContext(), MediaService::class.java))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeFuelAutomations(sharedPrefs: SharedPreferences) {
        try {
            val jsonArray = JSONArray(sharedPrefs.getString("AUTOMATIONS_LIST", "[]"))
            val newArray = JSONArray()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.optString("type") != "FUEL") {
                    newArray.put(obj)
                }
            }
            sharedPrefs.edit().putString("AUTOMATIONS_LIST", newArray.toString()).apply()
            requireActivity().startService(Intent(requireContext(), MediaService::class.java))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveFuelSnapshot(sharedPrefs: SharedPreferences) {
        val cap = sharedPrefs.getFloat("FUEL_CAPACITY", 14.0f)
        sharedPrefs.edit()
            .putBoolean("FUEL_HAS_BACKUP", true)
            .putFloat("PREV_FUEL_LITERS", sharedPrefs.getFloat("FUEL_CURRENT_LITERS", cap))
            .putFloat("PREV_FUEL_TRIP", sharedPrefs.getFloat("FUEL_TRIP_KM", 0.0f))
            .putFloat("PREV_FUEL_ODO", sharedPrefs.getFloat("FUEL_ODOMETER", 0.0f))
            .putFloat("PREV_LAST_REAL_KML", sharedPrefs.getFloat("LAST_REAL_KML", 0.0f))
            .putBoolean("PREV_FUEL_ALERTED", sharedPrefs.getBoolean("FUEL_RESERVE_ALERTED", false))
            .apply()
    }

    private fun restoreFuelSnapshot(sharedPrefs: SharedPreferences) {
        if (!sharedPrefs.getBoolean("FUEL_HAS_BACKUP", false)) return

        val cap = sharedPrefs.getFloat("FUEL_CAPACITY", 14.0f)
        val curLiters = sharedPrefs.getFloat("FUEL_CURRENT_LITERS", cap)
        val curTrip = sharedPrefs.getFloat("FUEL_TRIP_KM", 0.0f)
        val curOdo = sharedPrefs.getFloat("FUEL_ODOMETER", 0.0f)
        val curRealKml = sharedPrefs.getFloat("LAST_REAL_KML", 0.0f)
        val curAlert = sharedPrefs.getBoolean("FUEL_RESERVE_ALERTED", false)

        val prevLiters = sharedPrefs.getFloat("PREV_FUEL_LITERS", curLiters)
        val prevTrip = sharedPrefs.getFloat("PREV_FUEL_TRIP", curTrip)
        val prevOdo = sharedPrefs.getFloat("PREV_FUEL_ODO", curOdo)
        val prevRealKml = sharedPrefs.getFloat("PREV_LAST_REAL_KML", curRealKml)
        val prevAlert = sharedPrefs.getBoolean("PREV_FUEL_ALERTED", curAlert)

        sharedPrefs.edit()
            .putFloat("FUEL_CURRENT_LITERS", prevLiters)
            .putFloat("FUEL_TRIP_KM", prevTrip)
            .putFloat("FUEL_ODOMETER", prevOdo)
            .putFloat("LAST_REAL_KML", prevRealKml)
            .putBoolean("FUEL_RESERVE_ALERTED", prevAlert)
            .putFloat("PREV_FUEL_LITERS", curLiters)
            .putFloat("PREV_FUEL_TRIP", curTrip)
            .putFloat("PREV_FUEL_ODO", curOdo)
            .putFloat("PREV_LAST_REAL_KML", curRealKml)
            .putBoolean("PREV_FUEL_ALERTED", curAlert)
            .apply()
    }

    private fun showFuelTrackerDialog() {
        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_fuel_tracker)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        val switchActive = dialog.findViewById<SwitchMaterial>(R.id.switchFuelModuleActive)
        val tvFuelStatus = dialog.findViewById<TextView>(R.id.tvFuelStatus)
        val tvFuelRange = dialog.findViewById<TextView>(R.id.tvFuelRange)
        val btnToggleTrip = dialog.findViewById<MaterialButton>(R.id.btnToggleTrip)
        val btnFullTank = dialog.findViewById<MaterialButton>(R.id.btnFullTank)
        val btnHitReserve = dialog.findViewById<MaterialButton>(R.id.btnHitReserve)
        val btnUndoFuel = dialog.findViewById<MaterialButton>(R.id.btnUndoFuel)
        val btnSaveFuel = dialog.findViewById<MaterialButton>(R.id.btnSaveFuel)
        val editOdometer = dialog.findViewById<EditText>(R.id.editOdometer)
        val editCapacity = dialog.findViewById<EditText>(R.id.editTankCapacity)
        val editReserve = dialog.findViewById<EditText>(R.id.editReserveLiters)
        val editAverage = dialog.findViewById<EditText>(R.id.editFuelAverage)

        var isSaveArmed = false
        var isFullTankArmed = false
        var isReserveArmed = false

        fun resetSaveArmed() {
            isSaveArmed = false
            btnSaveFuel.text = "Save Config"
            btnSaveFuel.setTextColor(Color.parseColor("#BB86FC"))
        }

        fun resetFullTankArmed() {
            isFullTankArmed = false
            btnFullTank.text = "Full Tank"
        }

        fun resetReserveArmed() {
            isReserveArmed = false
            btnHitReserve.text = "Hit Reserve"
        }

        switchActive.isChecked = sharedPrefs.getBoolean("FUEL_UNLOCKED", true)

        switchActive.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                if (sharedPrefs.getBoolean("FUEL_TRACKING_ACTIVE", false)) {
                    requireContext().startService(Intent(requireContext(), MediaService::class.java).apply {
                        action = "ACTION_TOGGLE_FUEL_TRIP"
                    })
                }
                sharedPrefs.edit().putBoolean("FUEL_UNLOCKED", false).apply()
                removeFuelAutomations(sharedPrefs)
                view?.let { updateFuelButtonVisibility(it) }
                Toast.makeText(requireContext(), "Fuel Tracker disabled and hidden.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        val capacity = sharedPrefs.getFloat("FUEL_CAPACITY", 14.0f)
        val reserve = sharedPrefs.getFloat("FUEL_RESERVE", 2.0f)
        val avgKmL = sharedPrefs.getFloat("FUEL_AVG_KML", 35.0f)
        val savedOdo = sharedPrefs.getFloat("FUEL_ODOMETER", 0.0f)

        if (savedOdo > 0f) {
            editOdometer.setText(String.format(Locale.US, "%.1f", savedOdo))
        }
        editCapacity.setText(String.format(Locale.US, "%.1f", capacity))
        editReserve.setText(String.format(Locale.US, "%.1f", reserve))
        editAverage.setText(String.format(Locale.US, "%.1f", avgKmL))

        fun refreshDialogUi() {
            val currentCap = sharedPrefs.getFloat("FUEL_CAPACITY", 14.0f)
            val currentLiters = sharedPrefs.getFloat("FUEL_CURRENT_LITERS", currentCap)
            val currentAvg = sharedPrefs.getFloat("FUEL_AVG_KML", 35.0f)
            val tripKm = sharedPrefs.getFloat("FUEL_TRIP_KM", 0.0f)
            val currentOdo = sharedPrefs.getFloat("FUEL_ODOMETER", 0.0f)
            val lastRealKml = sharedPrefs.getFloat("LAST_REAL_KML", 0.0f)
            val isTracking = sharedPrefs.getBoolean("FUEL_TRACKING_ACTIVE", false)
            val currentRes = sharedPrefs.getFloat("FUEL_RESERVE", 2.0f)
            val hasBackup = sharedPrefs.getBoolean("FUEL_HAS_BACKUP", false)

            val estRangeKm = (currentLiters * currentAvg).toInt()
            tvFuelStatus.text = String.format(Locale.US, "%.2f L", currentLiters)
            tvFuelStatus.setTextColor(if (currentLiters <= currentRes) Color.parseColor("#FF5252") else Color.parseColor("#BB86FC"))

            val baseRangeText = String.format(Locale.US, "Range: ~%d km | Trip: %.1f km | Odo: %.1f km", estRangeKm, tripKm, currentOdo)
            tvFuelRange.text = if (lastRealKml > 0f) {
                baseRangeText + String.format(Locale.US, "\nLast Real Tank Avg: %.2f km/L", lastRealKml)
            } else {
                baseRangeText
            }

            if (currentOdo > 0f) {
                editOdometer.setText(String.format(Locale.US, "%.1f", currentOdo))
            }

            if (hasBackup) {
                val prevLiters = sharedPrefs.getFloat("PREV_FUEL_LITERS", currentLiters)
                val prevOdo = sharedPrefs.getFloat("PREV_FUEL_ODO", currentOdo)
                btnUndoFuel.visibility = View.VISIBLE
                btnUndoFuel.text = String.format(Locale.US, "↺ Undo (Restore %.2f L | %.1f km)", prevLiters, prevOdo)
            } else {
                btnUndoFuel.visibility = View.GONE
            }

            if (isTracking) {
                btnToggleTrip.text = "Stop Trip (Tracking ON)"
                btnToggleTrip.setBackgroundColor(Color.parseColor("#FF5252"))
                btnToggleTrip.setTextColor(Color.WHITE)
            } else {
                btnToggleTrip.text = "Start Trip (GPS)"
                btnToggleTrip.setBackgroundColor(Color.parseColor("#BB86FC"))
                btnToggleTrip.setTextColor(Color.parseColor("#121212"))
            }
        }

        refreshDialogUi()

        btnToggleTrip.setOnClickListener {
            val intent = Intent(requireContext(), MediaService::class.java).apply {
                action = "ACTION_TOGGLE_FUEL_TRIP"
            }
            requireContext().startService(intent)
            view?.postDelayed({ refreshDialogUi() }, 150)
        }

        btnFullTank.setOnClickListener {
            if (!isFullTankArmed) {
                isFullTankArmed = true
                btnFullTank.text = "Tap Again..."
                uiHandler.postDelayed({ if (dialog.isShowing) resetFullTankArmed() }, 3000)
                return@setOnClickListener
            }
            resetFullTankArmed()

            val oldOdo = sharedPrefs.getFloat("FUEL_ODOMETER", 0.0f)
            val currentTrip = sharedPrefs.getFloat("FUEL_TRIP_KM", 0.0f)
            val preTypedOdo = editOdometer.text.toString().replace(",", ".").toFloatOrNull() ?: oldOdo
            val cap = editCapacity.text.toString().replace(",", ".").toFloatOrNull() ?: sharedPrefs.getFloat("FUEL_CAPACITY", 14.0f)

            val container = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(60, 24, 60, 8)
            }

            val labelOdo = TextView(requireContext()).apply {
                text = String.format(Locale.US, "Odometer at Pump (km) — Current: %.1f km", oldOdo)
                setTextColor(Color.parseColor("#AAAAAA"))
                textSize = 12f
            }

            val inputPumpOdo = EditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setTextColor(Color.WHITE)
                setHintTextColor(Color.parseColor("#666666"))
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#BB86FC"))
                hint = if (oldOdo > 0f) String.format(Locale.US, "%.1f", oldOdo) else "e.g. 15800.0"
                if (preTypedOdo > 0f) {
                    setText(String.format(Locale.US, "%.1f", preTypedOdo))
                    setSelection(text.length)
                }
            }

            val labelLiters = TextView(requireContext()).apply {
                text = "Liters Filled at Pump (Optional — for Real km/L)"
                setTextColor(Color.parseColor("#AAAAAA"))
                textSize = 12f
                setPadding(0, 24, 0, 0)
            }

            val inputPumpLiters = EditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setTextColor(Color.WHITE)
                setHintTextColor(Color.parseColor("#666666"))
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#BB86FC"))
                hint = "e.g. 10.45"
            }

            val tvLiveAverage = TextView(requireContext()).apply {
                setTextColor(Color.parseColor("#BB86FC"))
                textSize = 13f
                setPadding(0, 16, 0, 0)
            }

            fun updateLiveCalculation() {
                val typedPumpOdo = inputPumpOdo.text.toString().replace(",", ".").toFloatOrNull() ?: oldOdo
                val typedPumpLiters = inputPumpLiters.text.toString().replace(",", ".").toFloatOrNull() ?: 0.0f
                val deltaFromOld = if (oldOdo > 0f && typedPumpOdo >= oldOdo) (typedPumpOdo - oldOdo) else 0.0f
                val totalTankKm = currentTrip + deltaFromOld

                if (oldOdo > 0f && typedPumpOdo < oldOdo) {
                    tvLiveAverage.setTextColor(Color.parseColor("#FF5252"))
                    tvLiveAverage.text = "⚠️ Odometer cannot be lower than current!"
                } else if (typedPumpLiters > 0f && totalTankKm > 0f) {
                    val realAvg = totalTankKm / typedPumpLiters
                    tvLiveAverage.setTextColor(Color.parseColor("#BB86FC"))
                    tvLiveAverage.text = String.format(
                        Locale.US,
                        "📊 Real Tank Avg: %.2f km/L (%.1f km / %.2f L)",
                        realAvg, totalTankKm, typedPumpLiters
                    )
                } else if (totalTankKm > 0f) {
                    tvLiveAverage.setTextColor(Color.parseColor("#AAAAAA"))
                    tvLiveAverage.text = String.format(
                        Locale.US,
                        "📏 Tank Distance: %.1f km (enter liters for real km/L)",
                        totalTankKm
                    )
                } else {
                    tvLiveAverage.text = ""
                }
            }

            val watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    updateLiveCalculation()
                }
                override fun afterTextChanged(s: Editable?) {}
            }

            inputPumpOdo.addTextChangedListener(watcher)
            inputPumpLiters.addTextChangedListener(watcher)
            updateLiveCalculation()

            container.addView(labelOdo)
            container.addView(inputPumpOdo)
            container.addView(labelLiters)
            container.addView(inputPumpLiters)
            container.addView(tvLiveAverage)

            val confirmDialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Confirm Full Tank")
                .setMessage(String.format(Locale.US, "Reset tank to %.1f L and zero Trip?", cap))
                .setView(container)
                .setPositiveButton("Confirm", null)
                .setNegativeButton("Cancel", null)
                .create()

            confirmDialog.setOnShowListener {
                val positiveBtn = confirmDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                val negativeBtn = confirmDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                positiveBtn?.setTextColor(Color.parseColor("#BB86FC"))
                negativeBtn?.setTextColor(Color.WHITE)

                positiveBtn?.setOnClickListener {
                    val pumpOdo = inputPumpOdo.text.toString().replace(",", ".").toFloatOrNull() ?: oldOdo
                    if (oldOdo > 0f && pumpOdo < oldOdo) {
                        Toast.makeText(
                            requireContext(),
                            String.format(Locale.US, "Blocked: New odometer (%.1f km) cannot be lower than current (%.1f km)!", pumpOdo, oldOdo),
                            Toast.LENGTH_LONG
                        ).show()
                        return@setOnClickListener
                    }

                    val pumpLiters = inputPumpLiters.text.toString().replace(",", ".").toFloatOrNull() ?: 0.0f
                    val deltaFromOld = if (oldOdo > 0f) (pumpOdo - oldOdo) else 0.0f
                    val totalTankKm = currentTrip + deltaFromOld

                    saveFuelSnapshot(sharedPrefs)
                    val editor = sharedPrefs.edit()
                        .putFloat("FUEL_CURRENT_LITERS", cap)
                        .putFloat("FUEL_TRIP_KM", 0.0f)
                        .putFloat("FUEL_ODOMETER", pumpOdo)
                        .putBoolean("FUEL_RESERVE_ALERTED", false)

                    if (pumpLiters > 0f && totalTankKm > 0f) {
                        val realKml = totalTankKm / pumpLiters
                        editor.putFloat("LAST_REAL_KML", realKml)
                        Toast.makeText(
                            requireContext(),
                            String.format(Locale.US, "Full Tank! Real Avg: %.2f km/L", realKml),
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(requireContext(), "Tank reset to Full!", Toast.LENGTH_SHORT).show()
                    }

                    editor.apply()
                    requireContext().startService(Intent(requireContext(), MediaService::class.java).apply { action = "ACTION_REFRESH_FUEL" })
                    refreshDialogUi()
                    confirmDialog.dismiss()
                }
            }
            confirmDialog.show()
        }

        btnHitReserve.setOnClickListener {
            if (!isReserveArmed) {
                isReserveArmed = true
                btnHitReserve.text = "Tap Again..."
                uiHandler.postDelayed({ if (dialog.isShowing) resetReserveArmed() }, 3000)
                return@setOnClickListener
            }
            resetReserveArmed()

            val res = editReserve.text.toString().replace(",", ".").toFloatOrNull() ?: sharedPrefs.getFloat("FUEL_RESERVE", 2.0f)

            val confirmDialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Confirm Reserve")
                .setMessage(String.format(Locale.US, "Calibrate remaining fuel directly to Reserve level (%.1f L)?", res))
                .setPositiveButton("Calibrate") { _, _ ->
                    saveFuelSnapshot(sharedPrefs)
                    sharedPrefs.edit()
                        .putFloat("FUEL_CURRENT_LITERS", res)
                        .putBoolean("FUEL_RESERVE_ALERTED", true)
                        .apply()
                    requireContext().startService(Intent(requireContext(), MediaService::class.java).apply { action = "ACTION_REFRESH_FUEL" })
                    refreshDialogUi()
                    Toast.makeText(requireContext(), "Calibrated to Reserve level!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .create()

            confirmDialog.setOnShowListener {
                confirmDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#FF5252"))
                confirmDialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
            }
            confirmDialog.show()
        }

        btnUndoFuel.setOnClickListener {
            restoreFuelSnapshot(sharedPrefs)
            requireContext().startService(Intent(requireContext(), MediaService::class.java).apply { action = "ACTION_REFRESH_FUEL" })
            refreshDialogUi()
            Toast.makeText(requireContext(), "Previous values restored!", Toast.LENGTH_SHORT).show()
        }

        dialog.findViewById<View>(R.id.btnCloseFuel).setOnClickListener { dialog.dismiss() }

        btnSaveFuel.setOnClickListener {
            val newCap = editCapacity.text.toString().replace(",", ".").toFloatOrNull() ?: 14.0f
            val newRes = editReserve.text.toString().replace(",", ".").toFloatOrNull() ?: 2.0f
            val newAvg = (editAverage.text.toString().replace(",", ".").toFloatOrNull() ?: 35.0f).coerceAtLeast(1.0f)

            val oldOdo = sharedPrefs.getFloat("FUEL_ODOMETER", 0.0f)
            val newOdo = editOdometer.text.toString().replace(",", ".").toFloatOrNull() ?: oldOdo

            if (oldOdo > 0f && newOdo < oldOdo) {
                editOdometer.setText(String.format(Locale.US, "%.1f", oldOdo))
                resetSaveArmed()
                Toast.makeText(
                    requireContext(),
                    String.format(Locale.US, "Blocked: New odometer (%.1f km) cannot be lower than current (%.1f km)!", newOdo, oldOdo),
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            if (!isSaveArmed) {
                isSaveArmed = true
                btnSaveFuel.text = "Tap Again to Save"
                btnSaveFuel.setTextColor(Color.parseColor("#FFB74D"))
                uiHandler.postDelayed({ if (dialog.isShowing) resetSaveArmed() }, 3000)
                return@setOnClickListener
            }
            resetSaveArmed()

            val oldLiters = sharedPrefs.getFloat("FUEL_CURRENT_LITERS", newCap)
            var previewLiters = oldLiters
            var previewTrip = sharedPrefs.getFloat("FUEL_TRIP_KM", 0.0f)
            val deltaKm = if (oldOdo > 0f && abs(newOdo - oldOdo) >= 0.1f) (newOdo - oldOdo) else 0.0f

            if (deltaKm > 0f) {
                val litersDelta = deltaKm / newAvg
                previewLiters = (previewLiters - litersDelta).coerceIn(0.0f, newCap)
                previewTrip = (previewTrip + deltaKm).coerceAtLeast(0.0f)
            } else {
                previewLiters = previewLiters.coerceIn(0.0f, newCap)
            }

            val summaryMsg = String.format(
                Locale.US,
                "Apply the following changes?\n\n" +
                        "• Odometer: %.1f km → %.1f km (+%.1f km)\n" +
                        "• Tank Fuel: %.2f L → %.2f L\n" +
                        "• Config: %.1fL Tank | %.1fL Res | %.1f km/L",
                oldOdo, newOdo, deltaKm, oldLiters, previewLiters, newCap, newRes, newAvg
            )

            val confirmSaveDialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Confirm Fuel Update")
                .setMessage(summaryMsg)
                .setPositiveButton("Confirm Save") { _, _ ->
                    saveFuelSnapshot(sharedPrefs)
                    sharedPrefs.edit()
                        .putFloat("FUEL_CAPACITY", newCap)
                        .putFloat("FUEL_RESERVE", newRes)
                        .putFloat("FUEL_AVG_KML", newAvg)
                        .putFloat("FUEL_ODOMETER", newOdo)
                        .putFloat("FUEL_CURRENT_LITERS", previewLiters)
                        .putFloat("FUEL_TRIP_KM", previewTrip)
                        .putBoolean("FUEL_RESERVE_ALERTED", previewLiters <= newRes)
                        .apply()

                    requireContext().startService(Intent(requireContext(), MediaService::class.java).apply { action = "ACTION_REFRESH_FUEL" })
                    Toast.makeText(requireContext(), "Fuel & Odometer synced!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .create()

            confirmSaveDialog.setOnShowListener {
                confirmSaveDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#BB86FC"))
                confirmSaveDialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
            }
            confirmSaveDialog.show()
        }

        dialog.show()
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }
}
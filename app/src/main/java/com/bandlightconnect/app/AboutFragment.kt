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
            .putBoolean("LAST_ACTION_WAS_GPS_TRIP", false)
            .putFloat("PREV_FUEL_LITERS", sharedPrefs.getFloat("FUEL_CURRENT_LITERS", cap))
            .putFloat("PREV_FUEL_TRIP", sharedPrefs.getFloat("FUEL_TRIP_KM", 0.0f))
            .putFloat("PREV_FUEL_ODO", sharedPrefs.getFloat("FUEL_ODOMETER", 0.0f))
            .putFloat("PREV_LAST_REAL_KML", sharedPrefs.getFloat("LAST_REAL_KML", 0.0f))
            .putBoolean("PREV_FUEL_ALERTED", sharedPrefs.getBoolean("FUEL_RESERVE_ALERTED", false))
            .putFloat("PREV_OIL_LAST_ODO", sharedPrefs.getFloat("FUEL_OIL_LAST_ODO", 0.0f))
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
        val curOilLast = sharedPrefs.getFloat("FUEL_OIL_LAST_ODO", 0.0f)

        val prevLiters = sharedPrefs.getFloat("PREV_FUEL_LITERS", curLiters)
        val prevTrip = sharedPrefs.getFloat("PREV_FUEL_TRIP", curTrip)
        val prevOdo = sharedPrefs.getFloat("PREV_FUEL_ODO", curOdo)
        val prevRealKml = sharedPrefs.getFloat("PREV_LAST_REAL_KML", curRealKml)
        val prevAlert = sharedPrefs.getBoolean("PREV_FUEL_ALERTED", curAlert)
        val prevOilLast = sharedPrefs.getFloat("PREV_OIL_LAST_ODO", curOilLast)

        sharedPrefs.edit()
            .putFloat("FUEL_CURRENT_LITERS", prevLiters)
            .putFloat("FUEL_TRIP_KM", prevTrip)
            .putFloat("FUEL_ODOMETER", prevOdo)
            .putFloat("LAST_REAL_KML", prevRealKml)
            .putBoolean("FUEL_RESERVE_ALERTED", prevAlert)
            .putFloat("FUEL_OIL_LAST_ODO", prevOilLast)
            .putFloat("PREV_FUEL_LITERS", curLiters)
            .putFloat("PREV_FUEL_TRIP", curTrip)
            .putFloat("PREV_FUEL_ODO", curOdo)
            .putFloat("PREV_LAST_REAL_KML", curRealKml)
            .putBoolean("PREV_FUEL_ALERTED", curAlert)
            .putFloat("PREV_OIL_LAST_ODO", curOilLast)
            .apply()
    }

    private fun showFuelTrackerDialog() {
        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_fuel_tracker)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width = (resources.displayMetrics.widthPixels * 0.92).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.findViewById<View>(R.id.btnFuelStats).setOnClickListener {
            showFuelStatsDialog()
        }




        val switchActive = dialog.findViewById<SwitchMaterial>(R.id.switchFuelModuleActive)
        val tvFuelStatus = dialog.findViewById<TextView>(R.id.tvFuelStatus)
        val tvFuelRange = dialog.findViewById<TextView>(R.id.tvFuelRange)
        val tvOilStatus = dialog.findViewById<TextView>(R.id.tvOilStatus)
        val btnToggleTrip = dialog.findViewById<MaterialButton>(R.id.btnToggleTrip)
        val btnFullTank = dialog.findViewById<MaterialButton>(R.id.btnFullTank)

        val btnHitReserve = dialog.findViewById<MaterialButton>(R.id.btnHitReserve)
        val btnResetOil = dialog.findViewById<MaterialButton>(R.id.btnResetOil)
        val btnUndoFuel = dialog.findViewById<MaterialButton>(R.id.btnUndoFuel)
        val btnSaveFuel = dialog.findViewById<MaterialButton>(R.id.btnSaveFuel)
        val editOdometer = dialog.findViewById<EditText>(R.id.editOdometer)
        val editCapacity = dialog.findViewById<EditText>(R.id.editTankCapacity)
        val editReserve = dialog.findViewById<EditText>(R.id.editReserveLiters)
        val editAverage = dialog.findViewById<EditText>(R.id.editFuelAverage)
        val editOilLastOdo = dialog.findViewById<EditText>(R.id.editOilLastOdo)
        val editOilInterval = dialog.findViewById<EditText>(R.id.editOilInterval)
        val editMinTripKm = dialog.findViewById<EditText>(R.id.editMinTripKm)
        val editMinPeakSpeed = dialog.findViewById<EditText>(R.id.editMinPeakSpeed)

        var isSaveArmed = false
        var isFullTankArmed = false
        var isReserveArmed = false
        var isResetOilArmed = false
        var isUndoArmed = false

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

        fun resetOilArmed() {
            isResetOilArmed = false
            btnResetOil.text = "Reset Oil"
        }



        fun resetUndoArmed() {
            isUndoArmed = false
            val currentCap = sharedPrefs.getFloat("FUEL_CAPACITY", 14.0f)
            val currentLiters = sharedPrefs.getFloat("FUEL_CURRENT_LITERS", currentCap)
            val currentOdo = sharedPrefs.getFloat("FUEL_ODOMETER", 0.0f)
            val prevLiters = sharedPrefs.getFloat("PREV_FUEL_LITERS", currentLiters)
            val prevOdo = sharedPrefs.getFloat("PREV_FUEL_ODO", currentOdo)
            btnUndoFuel.text = String.format(Locale.US, "↺ Undo (Restore %.2f L | %.1f km)", prevLiters, prevOdo)
            btnUndoFuel.setTextColor(Color.parseColor("#FFB74D"))
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
        val savedOilLast = sharedPrefs.getFloat("FUEL_OIL_LAST_ODO", 0.0f)
        val savedOilInterval = sharedPrefs.getFloat("FUEL_OIL_INTERVAL", 1000.0f)
        val savedMinTrip = sharedPrefs.getFloat("FUEL_MIN_TRIP_KM", 1.0f)
        val savedMinSpeed = sharedPrefs.getFloat("FUEL_MIN_PEAK_SPEED", 15.0f)

        if (savedOdo > 0f) editOdometer.setText(String.format(Locale.US, "%.1f", savedOdo))
        editCapacity.setText(String.format(Locale.US, "%.1f", capacity))
        editReserve.setText(String.format(Locale.US, "%.1f", reserve))
        editAverage.setText(String.format(Locale.US, "%.1f", avgKmL))
        if (savedOilLast > 0f) editOilLastOdo.setText(String.format(Locale.US, "%.1f", savedOilLast))
        editOilInterval.setText(String.format(Locale.US, "%.0f", savedOilInterval))
        editMinTripKm.setText(String.format(Locale.US, "%.1f", savedMinTrip))
        editMinPeakSpeed.setText(String.format(Locale.US, "%.0f", savedMinSpeed))

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
            val oilLast = sharedPrefs.getFloat("FUEL_OIL_LAST_ODO", 0.0f)
            val oilInt = sharedPrefs.getFloat("FUEL_OIL_INTERVAL", 1000.0f).coerceAtLeast(100.0f)

            val estRangeKm = (currentLiters * currentAvg).toInt()
            tvFuelStatus.text = String.format(Locale.US, "%.2f L", currentLiters)
            tvFuelStatus.setTextColor(if (currentLiters <= currentRes) Color.parseColor("#FF5252") else Color.parseColor("#BB86FC"))

            val baseRangeText = String.format(Locale.US, "Range: ~%d km | Trip: %.1f km | Odo: %.1f km", estRangeKm, tripKm, currentOdo)
            tvFuelRange.text = if (lastRealKml > 0f) {
                baseRangeText + String.format(Locale.US, "\nLast Real Tank Avg: %.2f km/L", lastRealKml)
            } else {
                baseRangeText
            }

            // Status da Troca de Óleo
            if (oilLast > 0f && currentOdo > 0f) {
                val nextOilOdo = oilLast + oilInt
                val kmLeft = nextOilOdo - currentOdo
                when {
                    kmLeft <= 0f -> {
                        tvOilStatus.text = String.format(Locale.US, "🛢️ Oil OVERDUE by %.0f km (Target: %.0f km)", abs(kmLeft), nextOilOdo)
                        tvOilStatus.setTextColor(Color.parseColor("#FF5252"))
                    }
                    kmLeft <= 100f -> {
                        tvOilStatus.text = String.format(Locale.US, "🛢️ Oil change soon: %.0f km left (Next: %.0f km)", kmLeft, nextOilOdo)
                        tvOilStatus.setTextColor(Color.parseColor("#FFB74D"))
                    }
                    else -> {
                        tvOilStatus.text = String.format(Locale.US, "🛢️ Oil: %.0f km left (Next: %.0f km)", kmLeft, nextOilOdo)
                        tvOilStatus.setTextColor(Color.parseColor("#81C784"))
                    }
                }
            } else {
                tvOilStatus.text = "🛢️ Oil: Set last change km or tap Reset Oil"
                tvOilStatus.setTextColor(Color.parseColor("#888888"))
            }

            if (currentOdo > 0f) editOdometer.setText(String.format(Locale.US, "%.1f", currentOdo))
            if (oilLast > 0f) editOilLastOdo.setText(String.format(Locale.US, "%.1f", oilLast))

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
            view?.postDelayed({ refreshDialogUi() }, 200)
        }

        dialog.findViewById<View>(R.id.btnTripHistory).setOnClickListener {
            TripHistoryDialogHelper.show(requireContext()) {
                refreshDialogUi()
            }
        }

        // Botão Reset Oil (2 cliques + confirmação)
        btnResetOil.setOnClickListener {
            if (!isResetOilArmed) {
                isResetOilArmed = true
                btnResetOil.text = "Tap Again..."
                uiHandler.postDelayed({ if (dialog.isShowing) resetOilArmed() }, 3000)
                return@setOnClickListener
            }
            resetOilArmed()

            val currentOdo = editOdometer.text.toString().replace(",", ".").toFloatOrNull()
                ?: sharedPrefs.getFloat("FUEL_ODOMETER", 0.0f)
            val oilInt = editOilInterval.text.toString().replace(",", ".").toFloatOrNull()?.coerceAtLeast(100f)
                ?: sharedPrefs.getFloat("FUEL_OIL_INTERVAL", 1000.0f)

            val confirmOilDialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Confirm Oil Change")
                .setMessage(
                    String.format(
                        Locale.US,
                        "Mark oil as changed at %.1f km?\nNext change reminder will be at %.1f km.",
                        currentOdo, currentOdo + oilInt
                    )
                )
                .setPositiveButton("Confirm") { _, _ ->
                    saveFuelSnapshot(sharedPrefs)
                    sharedPrefs.edit()
                        .putFloat("FUEL_OIL_LAST_ODO", currentOdo)
                        .putFloat("FUEL_OIL_INTERVAL", oilInt)
                        .apply()
                    refreshDialogUi()
                    Toast.makeText(requireContext(), "Oil change logged!", Toast.LENGTH_SHORT).show()
                    FuelStatsHelper.recordOilChange(requireContext(), currentOdo)
                }
                .setNegativeButton("Cancel", null)
                .create()

            confirmOilDialog.setOnShowListener {
                confirmOilDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#FFB74D"))
                confirmOilDialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
            }
            confirmOilDialog.show()
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
            val lastPrice = sharedPrefs.getFloat("FUEL_PRICE_PER_LITER", 6.00f)
            val preTypedOdo = editOdometer.text.toString().replace(",", ".").toFloatOrNull() ?: oldOdo
            val cap = editCapacity.text.toString().replace(",", ".").toFloatOrNull() ?: sharedPrefs.getFloat("FUEL_CAPACITY", 14.0f)

            val fullTankDialog = Dialog(requireContext())
            fullTankDialog.setContentView(R.layout.dialog_full_tank)
            fullTankDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            fullTankDialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

            val tvCurrentOdoHint = fullTankDialog.findViewById<TextView>(R.id.tvFullTankCurrentOdo)
            val inputPumpOdo = fullTankDialog.findViewById<EditText>(R.id.editFullTankOdo)
            val inputPumpLiters = fullTankDialog.findViewById<EditText>(R.id.editFullTankLiters)
            val inputPumpPrice = fullTankDialog.findViewById<EditText>(R.id.editFullTankPrice)
            val inputPumpTotal = fullTankDialog.findViewById<EditText>(R.id.editFullTankTotal)
            val tvWarning = fullTankDialog.findViewById<TextView>(R.id.tvFullTankWarning)
            val tvPreviewDist = fullTankDialog.findViewById<TextView>(R.id.tvFullTankPreviewDistance)
            val tvPreviewAvg = fullTankDialog.findViewById<TextView>(R.id.tvFullTankPreviewRealAvg)
            val tvPreviewPaid = fullTankDialog.findViewById<TextView>(R.id.tvFullTankPreviewTotalPaid)
            val switchUpdateAvg = fullTankDialog.findViewById<SwitchMaterial>(R.id.switchUpdateAvgKml)

            val switchFullTankLogManual = fullTankDialog.findViewById<SwitchMaterial>(R.id.switchFullTankLogManual)
            var manualSwitchTouched = false
            switchFullTankLogManual.setOnCheckedChangeListener { _, _ -> manualSwitchTouched = true }

            tvCurrentOdoHint.text = String.format(
                Locale.US,
                "Resets tank to %.1f L and zeros Trip (Current Odo: %.1f km).",
                cap, oldOdo
            )
            if (preTypedOdo > 0f) {
                inputPumpOdo.setText(String.format(Locale.US, "%.1f", preTypedOdo))
            }
            if (lastPrice > 0f) {
                inputPumpPrice.setText(String.format(Locale.US, "%.2f", lastPrice))
            }

            var isAutoUpdating = false
            var litersEnteredManually = false
            var lastMoneyField = "PRICE"

            fun parseVal(et: EditText): Float =
                et.text.toString().replace(",", ".").toFloatOrNull() ?: 0.0f

            fun updateLivePreview() {
                val pumpOdo = parseVal(inputPumpOdo).takeIf { it > 0f } ?: oldOdo
                val liters = parseVal(inputPumpLiters)
                val price = parseVal(inputPumpPrice)
                val total = parseVal(inputPumpTotal)

                if (oldOdo > 0f && pumpOdo < oldOdo) {
                    tvWarning.visibility = View.VISIBLE
                    tvWarning.text = String.format(Locale.US, "⚠️ Odometer cannot be lower than %.1f km!", oldOdo)
                } else {
                    tvWarning.visibility = View.GONE
                }

                val deltaFromOld = if (oldOdo > 0f && pumpOdo >= oldOdo) (pumpOdo - oldOdo) else 0.0f
                val totalTankKm = currentTrip + deltaFromOld
                tvPreviewDist.text = String.format(Locale.US, "%.1f km", totalTankKm)

                if (deltaFromOld >= 0.05f) {
                    switchFullTankLogManual.visibility = View.VISIBLE
                    switchFullTankLogManual.text = String.format(Locale.US, "Log +%.1f km adjustment to Trip History (Manual)", deltaFromOld)
                    if (!manualSwitchTouched) {
                        switchFullTankLogManual.isChecked = deltaFromOld >= 1.0f
                    }
                } else {
                    switchFullTankLogManual.visibility = View.GONE
                }

                if (liters > 0f && totalTankKm > 0f) {
                    val realAvg = totalTankKm / liters
                    tvPreviewAvg.text = String.format(Locale.US, "%.2f km/L", realAvg)
                    switchUpdateAvg.isEnabled = true
                } else {
                    tvPreviewAvg.text = "-- km/L"
                    switchUpdateAvg.isEnabled = false
                    switchUpdateAvg.isChecked = false
                }

                val effectiveTotal = if (total > 0f) total else (liters * price)
                tvPreviewPaid.text = if (effectiveTotal > 0f) FuelStatsHelper.formatCurrency(effectiveTotal) else "--"
            }

            inputPumpOdo.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    updateLivePreview()
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            inputPumpLiters.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (isAutoUpdating) return
                    litersEnteredManually = !s.isNullOrBlank()
                    val liters = parseVal(inputPumpLiters)
                    val price = parseVal(inputPumpPrice)
                    val total = parseVal(inputPumpTotal)
                    if (liters > 0f) {
                        isAutoUpdating = true
                        if (price > 0f && lastMoneyField != "TOTAL") {
                            inputPumpTotal.setText(String.format(Locale.US, "%.2f", liters * price))
                        } else if (total > 0f) {
                            inputPumpPrice.setText(String.format(Locale.US, "%.2f", total / liters))
                        }
                        isAutoUpdating = false
                    }
                    updateLivePreview()
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            inputPumpPrice.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (isAutoUpdating) return
                    lastMoneyField = "PRICE"
                    val price = parseVal(inputPumpPrice)
                    val liters = parseVal(inputPumpLiters)
                    val total = parseVal(inputPumpTotal)
                    if (price > 0f) {
                        isAutoUpdating = true
                        if (litersEnteredManually && liters > 0f) {
                            inputPumpTotal.setText(String.format(Locale.US, "%.2f", liters * price))
                        } else if (total > 0f) {
                            inputPumpLiters.setText(String.format(Locale.US, "%.2f", total / price))
                        }
                        isAutoUpdating = false
                    }
                    updateLivePreview()
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            inputPumpTotal.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (isAutoUpdating) return
                    lastMoneyField = "TOTAL"
                    val total = parseVal(inputPumpTotal)
                    val liters = parseVal(inputPumpLiters)
                    val price = parseVal(inputPumpPrice)
                    if (total > 0f) {
                        isAutoUpdating = true
                        if (litersEnteredManually && liters > 0f) {
                            inputPumpPrice.setText(String.format(Locale.US, "%.2f", total / liters))
                        } else if (price > 0f) {
                            inputPumpLiters.setText(String.format(Locale.US, "%.2f", total / price))
                        }
                        isAutoUpdating = false
                    }
                    updateLivePreview()
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            updateLivePreview()

            fullTankDialog.findViewById<View>(R.id.btnCancelFullTank).setOnClickListener {
                fullTankDialog.dismiss()
            }

            fullTankDialog.findViewById<View>(R.id.btnConfirmFullTank).setOnClickListener {
                val pumpOdo = parseVal(inputPumpOdo).takeIf { it > 0f } ?: oldOdo
                if (oldOdo > 0f && pumpOdo < oldOdo) {
                    Toast.makeText(
                        requireContext(),
                        String.format(Locale.US, "Blocked: New odometer (%.1f km) cannot be lower than current (%.1f km)!", pumpOdo, oldOdo),
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }

                var pumpLiters = parseVal(inputPumpLiters)
                var pumpPrice = parseVal(inputPumpPrice)
                var pumpTotal = parseVal(inputPumpTotal)

                if (pumpTotal <= 0f && pumpLiters > 0f && pumpPrice > 0f) {
                    pumpTotal = pumpLiters * pumpPrice
                } else if (pumpPrice <= 0f && pumpLiters > 0f && pumpTotal > 0f) {
                    pumpPrice = pumpTotal / pumpLiters
                } else if (pumpLiters <= 0f && pumpPrice > 0f && pumpTotal > 0f) {
                    pumpLiters = pumpTotal / pumpPrice
                }

                val deltaFromOld = if (oldOdo > 0f) (pumpOdo - oldOdo) else 0.0f
                val totalTankKm = currentTrip + deltaFromOld
                val realKml = if (pumpLiters > 0f && totalTankKm > 0f) (totalTankKm / pumpLiters) else 0.0f

                saveFuelSnapshot(sharedPrefs)
                val editor = sharedPrefs.edit()
                    .putFloat("FUEL_CURRENT_LITERS", cap)
                    .putFloat("FUEL_TRIP_KM", 0.0f)
                    .putFloat("FUEL_ODOMETER", pumpOdo)
                    .putBoolean("FUEL_RESERVE_ALERTED", false)

                if (pumpPrice > 0f) {
                    editor.putFloat("FUEL_PRICE_PER_LITER", pumpPrice)
                }

                if (realKml > 0f) {
                    editor.putFloat("LAST_REAL_KML", realKml)
                    if (switchUpdateAvg.isChecked) {
                        editor.putFloat("FUEL_AVG_KML", realKml)
                        editAverage.setText(String.format(Locale.US, "%.1f", realKml))
                    }
                    Toast.makeText(
                        requireContext(),
                        String.format(Locale.US, "Full Tank! Real Avg: %.2f km/L", realKml),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(requireContext(), "Tank reset to Full!", Toast.LENGTH_SHORT).show()
                }

                editor.apply()

                if (deltaFromOld >= 0.05f) {
                    FuelStatsHelper.recordDistance(requireContext(), deltaFromOld)
                }
                if (switchFullTankLogManual.visibility == View.VISIBLE && switchFullTankLogManual.isChecked) {
                    TripHistoryManager.recordManualTrip(requireContext(), deltaFromOld, note = "Added manually at Full Tank")
                    sharedPrefs.edit().putBoolean("LAST_ACTION_WAS_GPS_TRIP", true).apply()
                }
                if (pumpLiters > 0f || pumpTotal > 0f) {
                    FuelStatsHelper.recordRefuel(
                        requireContext(),
                        liters = pumpLiters,
                        pricePerLiter = pumpPrice,
                        totalPaid = pumpTotal,
                        tankKm = totalTankKm,
                        realKml = realKml,
                        odoKm = pumpOdo
                    )
                }
                requireContext().startService(Intent(requireContext(), MediaService::class.java).apply { action = "ACTION_REFRESH_FUEL" })
                refreshDialogUi()
                fullTankDialog.dismiss()
            }

            fullTankDialog.show()
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
            if (!isUndoArmed) {
                isUndoArmed = true
                btnUndoFuel.text = "Tap Again to Undo..."
                btnUndoFuel.setTextColor(Color.parseColor("#FF5252"))
                uiHandler.postDelayed({ if (dialog.isShowing && isUndoArmed) resetUndoArmed() }, 3000)
                return@setOnClickListener
            }
            resetUndoArmed()

            val currentCap = sharedPrefs.getFloat("FUEL_CAPACITY", 14.0f)
            val curLiters = sharedPrefs.getFloat("FUEL_CURRENT_LITERS", currentCap)
            val curOdo = sharedPrefs.getFloat("FUEL_ODOMETER", 0.0f)
            val prevLiters = sharedPrefs.getFloat("PREV_FUEL_LITERS", curLiters)
            val prevOdo = sharedPrefs.getFloat("PREV_FUEL_ODO", curOdo)

            val confirmUndoDialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Confirm Undo")
                .setMessage(
                    String.format(
                        Locale.US,
                        "Restore previous fuel & odometer snapshot?\n\n" +
                                "• Tank Fuel: %.2f L → %.2f L\n" +
                                "• Odometer: %.1f km → %.1f km",
                        curLiters, prevLiters, curOdo, prevOdo
                    )
                )
                .setPositiveButton("Restore") { _, _ ->
                    restoreFuelSnapshot(sharedPrefs)
                    FuelStatsHelper.toggleUndoLastDistance(requireContext())
                    if (sharedPrefs.getBoolean("LAST_ACTION_WAS_GPS_TRIP", false)) {
                        TripHistoryManager.toggleUndoLastTrip(requireContext())
                    }
                    requireContext().startService(Intent(requireContext(), MediaService::class.java).apply { action = "ACTION_REFRESH_FUEL" })
                    refreshDialogUi()
                    Toast.makeText(requireContext(), "Previous values restored!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .create()

            confirmUndoDialog.setOnShowListener {
                confirmUndoDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#FFB74D"))
                confirmUndoDialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
            }
            confirmUndoDialog.show()
        }

        dialog.findViewById<View>(R.id.btnCloseFuel).setOnClickListener { dialog.dismiss() }

        // Save Config com interface visual polida (dialog_confirm_fuel_save.xml)
        btnSaveFuel.setOnClickListener {
            val newCap = editCapacity.text.toString().replace(",", ".").toFloatOrNull() ?: 14.0f
            val newRes = editReserve.text.toString().replace(",", ".").toFloatOrNull() ?: 2.0f
            val newAvg = (editAverage.text.toString().replace(",", ".").toFloatOrNull() ?: 35.0f).coerceAtLeast(1.0f)
            val newOilLast = editOilLastOdo.text.toString().replace(",", ".").toFloatOrNull()
                ?: sharedPrefs.getFloat("FUEL_OIL_LAST_ODO", 0.0f)
            val newOilInterval = (editOilInterval.text.toString().replace(",", ".").toFloatOrNull() ?: 1000.0f).coerceAtLeast(100.0f)
            val newMinTrip = (editMinTripKm.text.toString().replace(",", ".").toFloatOrNull() ?: 1.0f).coerceAtLeast(0.0f)
            val newMinSpeed = (editMinPeakSpeed.text.toString().replace(",", ".").toFloatOrNull() ?: 15.0f).coerceAtLeast(0.0f)

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

            val confirmDialog = Dialog(requireContext())
            confirmDialog.setContentView(R.layout.dialog_confirm_fuel_save)
            confirmDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            confirmDialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

            confirmDialog.findViewById<TextView>(R.id.tvConfirmOdoValues).text =
                String.format(Locale.US, "%.1f km → %.1f km", oldOdo, newOdo)
            confirmDialog.findViewById<TextView>(R.id.tvConfirmOdoDelta).text =
                if (deltaKm > 0f) String.format(Locale.US, "+%.1f km", deltaKm) else "No change"
            confirmDialog.findViewById<TextView>(R.id.tvConfirmFuelValues).text =
                String.format(Locale.US, "%.2f L → %.2f L", oldLiters, previewLiters)
            confirmDialog.findViewById<TextView>(R.id.tvConfirmTankConfig).text =
                String.format(Locale.US, "%.1f L Tank  •  %.1f L Reserve  •  %.1f km/L", newCap, newRes, newAvg)
            confirmDialog.findViewById<TextView>(R.id.tvConfirmOilConfig).text =
                if (newOilLast > 0f) {
                    String.format(Locale.US, "Last: %.1f km  •  Every %.0f km", newOilLast, newOilInterval)
                } else {
                    String.format(Locale.US, "Not set yet  •  Interval: %.0f km", newOilInterval)
                }
            confirmDialog.findViewById<TextView>(R.id.tvConfirmGpsFilters).text =
                String.format(Locale.US, "Min Distance: %.1f km  •  Min Peak: %.0f km/h", newMinTrip, newMinSpeed)

            confirmDialog.findViewById<View>(R.id.btnCancelConfirmSave).setOnClickListener {
                confirmDialog.dismiss()
            }

            val switchLogManual = confirmDialog.findViewById<SwitchMaterial>(R.id.switchLogManualTrip)
            if (deltaKm >= 0.05f) {
                switchLogManual.visibility = View.VISIBLE
                switchLogManual.text = String.format(Locale.US, "Log +%.1f km to Trip History (Manual)", deltaKm)
                switchLogManual.isChecked = deltaKm >= 1.0f
            } else {
                switchLogManual.visibility = View.GONE
            }

            confirmDialog.findViewById<View>(R.id.btnApplyConfirmSave).setOnClickListener {
                saveFuelSnapshot(sharedPrefs)
                sharedPrefs.edit()
                    .putFloat("FUEL_CAPACITY", newCap)
                    .putFloat("FUEL_RESERVE", newRes)
                    .putFloat("FUEL_AVG_KML", newAvg)
                    .putFloat("FUEL_ODOMETER", newOdo)
                    .putFloat("FUEL_CURRENT_LITERS", previewLiters)
                    .putFloat("FUEL_TRIP_KM", previewTrip)
                    .putBoolean("FUEL_RESERVE_ALERTED", previewLiters <= newRes)
                    .putFloat("FUEL_OIL_LAST_ODO", newOilLast)
                    .putFloat("FUEL_OIL_INTERVAL", newOilInterval)
                    .putFloat("FUEL_MIN_TRIP_KM", newMinTrip)
                    .putFloat("FUEL_MIN_PEAK_SPEED", newMinSpeed)
                    .apply()

                if (deltaKm >= 0.05f) FuelStatsHelper.recordDistance(requireContext(), deltaKm)

                if (switchLogManual.visibility == View.VISIBLE && switchLogManual.isChecked) {
                    TripHistoryManager.recordManualTrip(requireContext(), deltaKm, note = "Added manually via Odometer Sync")
                    sharedPrefs.edit().putBoolean("LAST_ACTION_WAS_GPS_TRIP", true).apply()
                }

                requireContext().startService(Intent(requireContext(), MediaService::class.java).apply { action = "ACTION_REFRESH_FUEL" })
                Toast.makeText(requireContext(), "Fuel, Oil & Filters synced!", Toast.LENGTH_SHORT).show()
                confirmDialog.dismiss()
                dialog.dismiss()
            }

            confirmDialog.show()
        }

        dialog.show()
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun showFuelStatsDialog() {
        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val statsDialog = Dialog(requireContext())
        statsDialog.setContentView(R.layout.dialog_fuel_stats)
        statsDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        statsDialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        val chart = statsDialog.findViewById<FuelBarChartView>(R.id.fuelBarChart)
        val tvSelectedInfo = statsDialog.findViewById<TextView>(R.id.tvStatsSelectedInfo)
        val tvTotalKm = statsDialog.findViewById<TextView>(R.id.tvStatsTotalKm)
        val tvEstLiters = statsDialog.findViewById<TextView>(R.id.tvStatsEstLiters)
        val tvEstCost = statsDialog.findViewById<TextView>(R.id.tvStatsEstCost)
        val tvPumpSummary = statsDialog.findViewById<TextView>(R.id.tvStatsPumpSummary)
        val btnEditPrice = statsDialog.findViewById<TextView>(R.id.btnEditFuelPrice)
        val tvOilAvgDays = statsDialog.findViewById<TextView>(R.id.tvStatsOilAvgDays)
        val tvOilForecast = statsDialog.findViewById<TextView>(R.id.tvStatsOilForecast)
        val tvRecentTrips = statsDialog.findViewById<TextView>(R.id.tvStatsRecentTrips)

        fun refreshRecentTripsCard() {
            val recentTrips = TripHistoryManager.getRecentTrips(requireContext(), limit = 5)
            if (recentTrips.isEmpty()) {
                tvRecentTrips.text = "No trips recorded yet"
            } else {
                val dateFmt = java.text.SimpleDateFormat("MMM dd", Locale.US)
                tvRecentTrips.text = recentTrips.joinToString("\n") { trip ->
                    val dayStr = dateFmt.format(java.util.Date(trip.endTs))
                    val tagPart = if (trip.tag.isNotBlank()) " [${trip.tag}]" else ""
                    if (trip.isManual) {
                        String.format(Locale.US, "• %s  |  +%.2f km (Manual)%s", dayStr, trip.km, tagPart)
                    } else {
                        val durStr = FuelStatsHelper.formatDuration(trip.durationMs)
                        val endClock = FuelStatsHelper.formatClockTime(trip.endTs)
                        String.format(Locale.US, "• %s  |  +%.2f km • %s • %s%s", dayStr, trip.km, durStr, endClock, tagPart)
                    }
                }
            }
        }
        refreshRecentTripsCard()



        val recentTrips = FuelStatsHelper.getRecentTrips(requireContext(), limit = 5)
        if (recentTrips.isEmpty()) {
            tvRecentTrips.text = "No GPS trips recorded yet"
        } else {
            val dateFmt = java.text.SimpleDateFormat("MMM dd", Locale.US)
            tvRecentTrips.text = recentTrips.joinToString("\n") { trip ->
                val dayStr = dateFmt.format(java.util.Date(trip.endTs))
                val durStr = FuelStatsHelper.formatDuration(trip.durationMs)
                val endClock = FuelStatsHelper.formatClockTime(trip.endTs)
                String.format(
                    Locale.US,
                    "• %s  |  +%.2f km  •  %s  •  Ended %s",
                    dayStr, trip.km, durStr, endClock
                )
            }
        }

        val layoutPeriodFilters = statsDialog.findViewById<View>(R.id.layoutPeriodFilters)
        val tvRealKmlHint = statsDialog.findViewById<View>(R.id.tvRealKmlPeriodHint)

        val btnMetricKm = statsDialog.findViewById<MaterialButton>(R.id.btnMetricKm)
        val btnMetricRealKml = statsDialog.findViewById<MaterialButton>(R.id.btnMetricRealKml)
        val btnMetricPumpSpend = statsDialog.findViewById<MaterialButton>(R.id.btnMetricPumpSpend)
        val metricButtons = listOf(
            StatsMetric.DISTANCE to btnMetricKm,
            StatsMetric.REAL_KML to btnMetricRealKml,
            StatsMetric.PUMP_SPEND to btnMetricPumpSpend
        )

        val btnDay = statsDialog.findViewById<MaterialButton>(R.id.btnFilterDay)
        val btnWeek = statsDialog.findViewById<MaterialButton>(R.id.btnFilterWeek)
        val btnMonth = statsDialog.findViewById<MaterialButton>(R.id.btnFilterMonth)
        val btnYear = statsDialog.findViewById<MaterialButton>(R.id.btnFilterYear)
        val filterButtons = listOf(
            StatsPeriod.DAY to btnDay,
            StatsPeriod.WEEK to btnWeek,
            StatsPeriod.MONTH to btnMonth,
            StatsPeriod.YEAR to btnYear
        )

        var currentPeriod = StatsPeriod.DAY
        var currentMetric = StatsMetric.DISTANCE

        fun styleToggleGroup(buttons: List<Pair<Any, MaterialButton>>, selectedKey: Any, activeHex: String) {
            for ((key, btn) in buttons) {
                if (key == selectedKey) {
                    btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor(activeHex))
                    btn.setTextColor(Color.parseColor("#121212"))
                    btn.strokeWidth = 0
                } else {
                    btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                    btn.setTextColor(Color.parseColor("#CCCCCC"))
                    btn.strokeWidth = 2
                    btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#444444"))
                }
            }
        }

        fun refreshSummary(period: StatsPeriod) {
            val distBars = FuelStatsHelper.getChartData(requireContext(), period)
            val currentPrice = sharedPrefs.getFloat("FUEL_PRICE_PER_LITER", 6.00f).coerceAtLeast(0.01f)
            val totalKm = distBars.sumOf { it.km.toDouble() }.toFloat()
            val estLiters = distBars.sumOf { it.liters.toDouble() }.toFloat()
            val estCost = distBars.sumOf { it.cost.toDouble() }.toFloat()

            btnEditPrice.text = "Current: ${FuelStatsHelper.formatCurrency(currentPrice)}/L ✎"
            tvTotalKm.text = String.format(Locale.US, "%.1f km", totalKm)
            tvEstLiters.text = String.format(Locale.US, "%.2f L", estLiters)
            tvEstCost.text = FuelStatsHelper.formatCurrency(estCost)

            val summaryPeriod = if (currentMetric == StatsMetric.REAL_KML) StatsPeriod.YEAR else period
            val pumpStats = FuelStatsHelper.getPeriodPumpStats(requireContext(), summaryPeriod)
            if (pumpStats.refuelCount > 0) {
                tvPumpSummary.visibility = View.VISIBLE
                val avgKmlPart = if (pumpStats.realAvgKmL > 0f) {
                    String.format(Locale.US, "\n📊 Real Avg: %.2f km/L", pumpStats.realAvgKmL)
                } else {
                    ""
                }
                val avgPricePart = if (pumpStats.weightedAvgPrice > 0f) {
                    " (avg ${FuelStatsHelper.formatCurrency(pumpStats.weightedAvgPrice)}/L)"
                } else {
                    ""
                }
                tvPumpSummary.text = String.format(
                    Locale.US,
                    "⛽ Pump refuels (%d): %s • %.2f L%s%s",
                    pumpStats.refuelCount,
                    FuelStatsHelper.formatCurrency(pumpStats.totalPaid),
                    pumpStats.totalLiters,
                    avgPricePart,
                    avgKmlPart
                )
            } else {
                tvPumpSummary.visibility = View.GONE
            }
        }

        chart.onBarSelected = { bar ->
            tvSelectedInfo.text = bar.detailText.ifEmpty {
                String.format(Locale.US, "%s: %.1f", bar.fullLabel, bar.displayValue)
            }
        }

        fun refreshView() {
            val activeAccent = when (currentMetric) {
                StatsMetric.DISTANCE -> "#BB86FC"
                StatsMetric.REAL_KML -> "#FFB74D"
                StatsMetric.PUMP_SPEND -> "#81C784"
            }
            styleToggleGroup(metricButtons, currentMetric, activeAccent)
            styleToggleGroup(filterButtons, currentPeriod, activeAccent)

            val isRealKmlMode = currentMetric == StatsMetric.REAL_KML
            layoutPeriodFilters.visibility = if (isRealKmlMode) View.GONE else View.VISIBLE
            tvRealKmlHint.visibility = if (isRealKmlMode) View.VISIBLE else View.GONE

            val bars = when (currentMetric) {
                StatsMetric.DISTANCE -> {
                    chart.setStyle("#8E62D6", "#BB86FC", "No km recorded in this period")
                    FuelStatsHelper.getChartData(requireContext(), currentPeriod)
                }
                StatsMetric.REAL_KML -> {
                    chart.setStyle("#C8872C", "#FFB74D", "Log a Full Tank with liters to chart Real km/L")
                    FuelStatsHelper.getRealKmlChartData(requireContext())
                }
                StatsMetric.PUMP_SPEND -> {
                    chart.setStyle("#4CAF50", "#81C784", "No pump refuels logged in this period")
                    FuelStatsHelper.getPumpSpendChartData(requireContext(), currentPeriod)
                }
            }

            if (bars.isEmpty()) {
                tvSelectedInfo.text = "No full-tank records yet"
            }
            chart.setData(bars)
            refreshSummary(currentPeriod)
        }

        btnMetricKm.setOnClickListener {
            currentMetric = StatsMetric.DISTANCE
            refreshView()
        }
        btnMetricRealKml.setOnClickListener {
            currentMetric = StatsMetric.REAL_KML
            refreshView()
        }
        btnMetricPumpSpend.setOnClickListener {
            currentMetric = StatsMetric.PUMP_SPEND
            refreshView()
        }

        btnDay.setOnClickListener { currentPeriod = StatsPeriod.DAY; refreshView() }
        btnWeek.setOnClickListener { currentPeriod = StatsPeriod.WEEK; refreshView() }
        btnMonth.setOnClickListener { currentPeriod = StatsPeriod.MONTH; refreshView() }
        btnYear.setOnClickListener { currentPeriod = StatsPeriod.YEAR; refreshView() }

        btnEditPrice.setOnClickListener {
            val currentPrice = sharedPrefs.getFloat("FUEL_PRICE_PER_LITER", 6.00f)
            val input = EditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setTextColor(Color.WHITE)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#BB86FC"))
                setText(String.format(Locale.US, "%.2f", currentPrice))
                setSelection(text.length)
            }
            val wrap = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(60, 16, 60, 8)
                addView(TextView(requireContext()).apply {
                    text = "Applies to future trips only (past costs remain locked)."
                    setTextColor(Color.parseColor("#AAAAAA"))
                    textSize = 12f
                    setPadding(0, 0, 0, 12)
                })
                addView(input)
            }
            val priceDialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Current Fuel Price (per Liter)")
                .setView(wrap)
                .setPositiveButton("Save") { _, _ ->
                    val newPrice = input.text.toString().replace(",", ".").toFloatOrNull()
                    if (newPrice != null && newPrice > 0f) {
                        sharedPrefs.edit().putFloat("FUEL_PRICE_PER_LITER", newPrice).apply()
                        refreshView()
                    }
                }
                .setNegativeButton("Cancel", null)
                .create()
            priceDialog.setOnShowListener {
                priceDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#BB86FC"))
                priceDialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
            }
            priceDialog.show()
        }

        val oilStats = FuelStatsHelper.getOilStats(requireContext())
        tvOilAvgDays.text = when {
            oilStats.avgDaysBetweenChanges != null ->
                "Avg. interval: every ${oilStats.avgDaysBetweenChanges} days (${oilStats.changeCount} changes)"
            oilStats.changeCount == 1 ->
                "1 change logged (waiting for 2nd to calculate avg. days)"
            else ->
                "No oil changes logged via 'Reset Oil' yet"
        }

        tvOilForecast.text = if (oilStats.estimatedNextDateStr != null) {
            String.format(
                Locale.US,
                "Next change forecast: %s (avg %.1f km/day)",
                oilStats.estimatedNextDateStr, oilStats.avgKmPerDay30d
            )
        } else {
            "Forecast: ride a few days with GPS active to estimate date"
        }

        statsDialog.findViewById<View>(R.id.btnCloseFuelStats).setOnClickListener {
            statsDialog.dismiss()
        }

        refreshView()
        statsDialog.show()

        statsDialog.findViewById<View>(R.id.btnOpenFullHistory).setOnClickListener {
            TripHistoryDialogHelper.show(requireContext()) {
                refreshView()
                refreshRecentTripsCard()
            }
        }
    }
}
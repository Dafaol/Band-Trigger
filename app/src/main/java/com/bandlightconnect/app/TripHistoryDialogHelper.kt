package com.bandlightconnect.app

import android.app.AlertDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

object TripHistoryDialogHelper {

    fun show(context: Context, onDataChanged: () -> Unit = {}) {
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.dialog_trip_history)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val dm = context.resources.displayMetrics
        dialog.window?.setLayout((dm.widthPixels * 0.94).toInt(), (dm.heightPixels * 0.85).toInt())

        val btnPrevMonth = dialog.findViewById<MaterialButton>(R.id.btnPrevMonth)
        val btnNextMonth = dialog.findViewById<MaterialButton>(R.id.btnNextMonth)
        val tvCurrentMonth = dialog.findViewById<TextView>(R.id.tvCurrentMonth)
        val tvMonthSummaryMain = dialog.findViewById<TextView>(R.id.tvMonthSummaryMain)
        val tvMonthSummarySub = dialog.findViewById<TextView>(R.id.tvMonthSummarySub)
        val layoutTagChips = dialog.findViewById<LinearLayout>(R.id.layoutTagChips)
        val layoutTripsContainer = dialog.findViewById<LinearLayout>(R.id.layoutTripsContainer)

        val nowCal = Calendar.getInstance()
        val maxYear = nowCal.get(Calendar.YEAR)
        val maxMonth = nowCal.get(Calendar.MONTH)

        var selectedYear = maxYear
        var selectedMonth = maxMonth
        // null = All, "" = Untagged, "Work" = filtro pela etiqueta Work
        var selectedTagFilter: String? = null

        val monthTitleFmt = SimpleDateFormat("MMMM yyyy", Locale.US)
        val dayKeyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayHeaderFmt = SimpleDateFormat("EEE, MMM dd", Locale.US)

        fun dp(v: Int): Int = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), dm
        ).toInt()

        lateinit var renderMonth: () -> Unit

        renderMonth = {
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, selectedYear)
                set(Calendar.MONTH, selectedMonth)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            tvCurrentMonth.text = monthTitleFmt.format(cal.time)

            val isCurrentMonth = (selectedYear == maxYear && selectedMonth == maxMonth)
            btnNextMonth.isEnabled = !isCurrentMonth
            btnNextMonth.alpha = if (isCurrentMonth) 0.35f else 1.0f

            val allMonthTrips = TripHistoryManager.getTripsForMonth(context, selectedYear, selectedMonth)
            val tagSummaries = TripHistoryManager.getTagSummaries(allMonthTrips)

            // Se o filtro ativo não existir mais no mês escolhido, volta para All
            if (selectedTagFilter != null && allMonthTrips.none { it.tag.trim() == selectedTagFilter }) {
                selectedTagFilter = null
            }

            // Monta os chips clicáveis de resumo por etiqueta
            layoutTagChips.removeAllViews()
            fun addChip(label: String, filterValue: String?, accentHex: String = "#BB86FC") {
                val isSelected = selectedTagFilter == filterValue
                val chip = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = label
                    isAllCaps = false
                    textSize = 11f
                    minHeight = 0
                    minWidth = 0
                    minimumHeight = dp(32)
                    setPadding(dp(10), dp(4), dp(10), dp(4))
                    cornerRadius = dp(10)
                    insetTop = 0
                    insetBottom = 0
                    if (isSelected) {
                        backgroundTintList = ColorStateList.valueOf(Color.parseColor(accentHex))
                        setTextColor(Color.parseColor("#121212"))
                        strokeWidth = 0
                    } else {
                        backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                        setTextColor(Color.parseColor("#DDDDDD"))
                        strokeWidth = dp(1)
                        strokeColor = ColorStateList.valueOf(Color.parseColor("#444444"))
                    }
                    setOnClickListener {
                        selectedTagFilter = filterValue
                        renderMonth()
                    }
                }
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)).apply {
                    marginEnd = dp(6)
                }
                layoutTagChips.addView(chip, lp)
            }

            addChip("All (${allMonthTrips.size})", null, "#BB86FC")
            for (summary in tagSummaries) {
                val chipTitle = if (summary.tag.isEmpty()) {
                    "Untagged (${summary.tripCount}) • ${FuelStatsHelper.formatCurrency(summary.totalCost)}"
                } else {
                    "🏷️ ${summary.tag} • ${String.format(Locale.US, "%.1f km", summary.totalKm)} • ${FuelStatsHelper.formatCurrency(summary.totalCost)}"
                }
                val accent = if (summary.tag.isEmpty()) "#9E9E9E" else "#81C784"
                addChip(chipTitle, summary.tag, accent)
            }

            val visibleTrips = if (selectedTagFilter == null) {
                allMonthTrips
            } else {
                allMonthTrips.filter { it.tag.trim() == selectedTagFilter }
            }

            // Resumo no card do topo
            val totalKm = visibleTrips.sumOf { it.km.toDouble() }.toFloat()
            val totalCost = visibleTrips.sumOf { it.cost.toDouble() }.toFloat()
            val totalDurationMs = visibleTrips.sumOf { it.durationMs }
            val totalLiters = visibleTrips.sumOf { it.liters.toDouble() }.toFloat()
            val filterPrefix = when (selectedTagFilter) {
                null -> ""
                "" -> "Untagged • "
                else -> "🏷️ $selectedTagFilter • "
            }

            tvMonthSummaryMain.text = String.format(
                Locale.US,
                "%s%d trips • %.1f km • %s",
                filterPrefix,
                visibleTrips.size,
                totalKm,
                FuelStatsHelper.formatCurrency(totalCost)
            )
            tvMonthSummarySub.text = if (visibleTrips.isEmpty()) {
                "No trips recorded in this month"
            } else {
                val durPart = if (totalDurationMs > 0L) "Riding time: ${FuelStatsHelper.formatDuration(totalDurationMs)} • " else ""
                String.format(Locale.US, "%sEst. %.2f L • Tap any trip to edit tag or delete", durPart, totalLiters)
            }

            // Renderiza a lista agrupada por dia (ordem decrescente)
            layoutTripsContainer.removeAllViews()
            if (visibleTrips.isEmpty()) {
                val emptyTv = TextView(context).apply {
                    text = "No trips recorded for this selection."
                    setTextColor(Color.parseColor("#777777"))
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setPadding(0, dp(40), 0, dp(40))
                }
                layoutTripsContainer.addView(emptyTv)
            } else {
                val groupedByDay = visibleTrips.groupBy { dayKeyFmt.format(Date(it.endTs)) }
                val sortedDays = groupedByDay.keys.sortedDescending()

                for (dayKey in sortedDays) {
                    val dayTrips = groupedByDay[dayKey] ?: continue
                    val dayKm = dayTrips.sumOf { it.km.toDouble() }.toFloat()
                    val dayCost = dayTrips.sumOf { it.cost.toDouble() }.toFloat()
                    val sampleDate = Date(dayTrips.first().endTs)

                    // Cabeçalho do dia
                    val dayHeaderRow = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(2), dp(12), dp(2), dp(6))
                    }
                    val tvDayName = TextView(context).apply {
                        text = dayHeaderFmt.format(sampleDate).uppercase(Locale.US)
                        setTextColor(Color.parseColor("#BB86FC"))
                        textSize = 11f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                    }
                    val tvDayTotals = TextView(context).apply {
                        text = String.format(
                            Locale.US,
                            "%.1f km • %s",
                            dayKm,
                            FuelStatsHelper.formatCurrency(dayCost)
                        )
                        setTextColor(Color.parseColor("#9E9E9E"))
                        textSize = 11f
                        gravity = Gravity.END
                    }
                    dayHeaderRow.addView(tvDayName, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    dayHeaderRow.addView(tvDayTotals, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                    layoutTripsContainer.addView(dayHeaderRow)

                    // Cartões de cada viagem do dia
                    for (trip in dayTrips) {
                        val card = buildTripCard(context, trip, dp(12), dp(8)) {
                            showEditTripDialog(
                                context = context,
                                year = selectedYear,
                                monthZeroBased = selectedMonth,
                                trip = trip,
                                allMonthTrips = allMonthTrips
                            ) {
                                renderMonth()
                                onDataChanged()
                            }
                        }
                        layoutTripsContainer.addView(card)
                    }
                }
            }
        }

        btnPrevMonth.setOnClickListener {
            selectedMonth--
            if (selectedMonth < 0) {
                selectedMonth = 11
                selectedYear--
            }
            renderMonth()
        }

        btnNextMonth.setOnClickListener {
            if (selectedYear < maxYear || (selectedYear == maxYear && selectedMonth < maxMonth)) {
                selectedMonth++
                if (selectedMonth > 11) {
                    selectedMonth = 0
                    selectedYear++
                }
                renderMonth()
            }
        }

        dialog.findViewById<View>(R.id.btnOpenTagRules).setOnClickListener {
            showAutoTagRulesDialog(context) {
                renderMonth()
            }
        }

        dialog.findViewById<View>(R.id.btnCloseTripHistory).setOnClickListener {
            dialog.dismiss()
        }

        renderMonth()
        dialog.show()
    }

    private fun buildTripCard(
        context: Context,
        trip: TripEntry,
        padPx: Int,
        marginBottomPx: Int,
        onClick: () -> Unit
    ): MaterialCardView {
        val card = MaterialCardView(context).apply {
            setCardBackgroundColor(Color.parseColor("#222222"))
            radius = padPx.toFloat()
            strokeColor = if (trip.isManual) Color.parseColor("#4D3E28") else Color.parseColor("#323232")
            strokeWidth = 2
            cardElevation = 0f
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        val cardLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = marginBottomPx
        }
        card.layoutParams = cardLp

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padPx, (padPx * 0.85f).toInt(), padPx, (padPx * 0.85f).toInt())
        }

        // Linha 1: Horário (ou Manual) + Tag à esquerda | Distância à direita
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val timeText = if (trip.isManual) {
            "✍️ Manual • ${FuelStatsHelper.formatClockTime(trip.endTs)}"
        } else {
            val startClock = FuelStatsHelper.formatClockTime(trip.startTs)
            val endClock = FuelStatsHelper.formatClockTime(trip.endTs)
            "🕒 $startClock → $endClock"
        }

        val tagSuffix = if (trip.tag.isNotBlank()) "  •  🏷️ ${trip.tag}" else ""
        val tvTitle = TextView(context).apply {
            text = "$timeText$tagSuffix"
            setTextColor(if (trip.isManual) Color.parseColor("#FFB74D") else Color.WHITE)
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val tvKm = TextView(context).apply {
            text = String.format(Locale.US, "+%.2f km", trip.km)
            setTextColor(Color.parseColor("#BB86FC"))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.END
        }

        topRow.addView(tvTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        topRow.addView(tvKm, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        body.addView(topRow)

        // Linha 2: Duração / Velocidade + Custo estimado
        val details = mutableListOf<String>()
        if (!trip.isManual) {
            details.add(FuelStatsHelper.formatDuration(trip.durationMs))
            if (trip.durationMs >= 10_000L && trip.km > 0f) {
                val hours = trip.durationMs / 3_600_000.0
                val avgSpd = (trip.km / hours).toFloat()
                if (avgSpd in 1f..200f) {
                    details.add(String.format(Locale.US, "Avg %.0f km/h", avgSpd))
                }
            }
            if (trip.maxSpeed > 0f) {
                details.add(String.format(Locale.US, "Peak %.0f km/h", trip.maxSpeed))
            }
        }
        details.add(String.format(Locale.US, "%.2f L", trip.liters))
        details.add(FuelStatsHelper.formatCurrency(trip.cost))

        val tvMeta = TextView(context).apply {
            text = details.joinToString(" • ")
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 11f
            setPadding(0, (padPx * 0.35f).toInt(), 0, 0)
        }
        body.addView(tvMeta)

        // Linha 3: Nota (se manual ou escrita pelo usuário)
        val noteToShow = when {
            trip.note.isNotBlank() -> trip.note
            trip.isManual -> "Added manually via odometer sync"
            else -> ""
        }
        if (noteToShow.isNotBlank()) {
            val tvNote = TextView(context).apply {
                text = "📝 $noteToShow"
                setTextColor(if (trip.isManual) Color.parseColor("#E0B87A") else Color.parseColor("#888888"))
                textSize = 11f
                setPadding(0, (padPx * 0.25f).toInt(), 0, 0)
            }
            body.addView(tvNote)
        }

        card.addView(body)
        return card
    }

    private fun showEditTripDialog(
        context: Context,
        year: Int,
        monthZeroBased: Int,
        trip: TripEntry,
        allMonthTrips: List<TripEntry>,
        onChanged: () -> Unit
    ) {
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.dialog_edit_trip)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((context.resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        val tvHeader = dialog.findViewById<TextView>(R.id.tvEditTripHeader)
        val editTag = dialog.findViewById<EditText>(R.id.editTripTag)
        val editNote = dialog.findViewById<EditText>(R.id.editTripNote)
        val layoutQuickChips = dialog.findViewById<LinearLayout>(R.id.layoutQuickTagChips)

        val dateFmt = SimpleDateFormat("MMM dd", Locale.US)
        val modeText = if (trip.isManual) "Manual adjustment" else "${FuelStatsHelper.formatClockTime(trip.startTs)} → ${FuelStatsHelper.formatClockTime(trip.endTs)}"
        tvHeader.text = String.format(
            Locale.US,
            "%s • %s • +%.2f km • %s",
            dateFmt.format(Date(trip.endTs)),
            modeText,
            trip.km,
            FuelStatsHelper.formatCurrency(trip.cost)
        )

        editTag.setText(trip.tag)
        editNote.setText(trip.note)

        val dm = context.resources.displayMetrics
        fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), dm).toInt()

        val tags = TripHistoryManager.getKnownTags(context, allMonthTrips)
        for (t in tags + listOf("Clear")) {
            val chip = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = t
                isAllCaps = false
                textSize = 11f
                minWidth = 0
                minHeight = 0
                setPadding(dp(10), dp(2), dp(10), dp(2))
                cornerRadius = dp(8)
                insetTop = 0
                insetBottom = 0
                setTextColor(if (t == "Clear") Color.parseColor("#FF5252") else Color.parseColor("#DDDDDD"))
                strokeColor = ColorStateList.valueOf(Color.parseColor("#444444"))
                setOnClickListener {
                    editTag.setText(if (t == "Clear") "" else t)
                    editTag.setSelection(editTag.text.length)
                }
            }
            layoutQuickChips.addView(
                chip,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)).apply { marginEnd = dp(6) }
            )
        }

        dialog.findViewById<View>(R.id.btnCreateRuleFromTrip).setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = trip.endTs }
            val endMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val startWin = (endMins - 30 + 1440) % 1440
            val endWin = (endMins + 30) % 1440
            val suggestedTag = editTag.text.toString().trim().ifEmpty { "Work" }

            val draftRule = AutoTagRule(
                id = UUID.randomUUID().toString(),
                tagName = suggestedTag,
                enabled = true,
                targetKm = String.format(Locale.US, "%.1f", trip.km).toFloatOrNull() ?: trip.km,
                kmMargin = 1.5f,
                startMinutes = startWin,
                endMinutes = endWin,
                daysOfWeek = ""
            )
            showEditRuleDialog(context, draftRule, isNew = true) {
                Toast.makeText(context, "Auto-Tag Rule created for '$suggestedTag'!", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.findViewById<View>(R.id.btnDeleteTrip).setOnClickListener {
            val delDialog = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Delete Trip")
                .setMessage(
                    String.format(
                        Locale.US,
                        "Remove this +%.2f km trip from history?\n\nYou can remove it only from the log, or also subtract it from odometer, tank, and charts.",
                        trip.km
                    )
                )
                .setPositiveButton("Delete & Subtract km") { _, _ ->
                    TripHistoryManager.deleteTrip(context, year, monthZeroBased, trip.id)
                    revertTripFromOdometerAndStats(context, trip)
                    Toast.makeText(context, "Trip deleted and km subtracted.", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    onChanged()
                }
                .setNeutralButton("List Only") { _, _ ->
                    TripHistoryManager.deleteTrip(context, year, monthZeroBased, trip.id)
                    Toast.makeText(context, "Removed from trip history.", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    onChanged()
                }
                .setNegativeButton("Cancel", null)
                .create()

            delDialog.setOnShowListener {
                delDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#FF5252"))
                delDialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(Color.parseColor("#FFB74D"))
                delDialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
            }
            delDialog.show()
        }

        dialog.findViewById<View>(R.id.btnCancelEditTrip).setOnClickListener { dialog.dismiss() }

        dialog.findViewById<View>(R.id.btnSaveEditTrip).setOnClickListener {
            val updated = trip.copy(
                tag = editTag.text.toString().trim(),
                note = editNote.text.toString().trim()
            )
            TripHistoryManager.updateTrip(context, year, monthZeroBased, updated)
            dialog.dismiss()
            onChanged()
        }

        dialog.show()
    }

    private fun revertTripFromOdometerAndStats(context: Context, trip: TripEntry) {
        val prefs = context.getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val cap = prefs.getFloat("FUEL_CAPACITY", 14.0f)
        val res = prefs.getFloat("FUEL_RESERVE", 2.0f)
        val curOdo = prefs.getFloat("FUEL_ODOMETER", 0.0f)
        val curTrip = prefs.getFloat("FUEL_TRIP_KM", 0.0f)
        val curLiters = prefs.getFloat("FUEL_CURRENT_LITERS", cap)

        val newOdo = (curOdo - trip.km).coerceAtLeast(0f)
        val newTrip = (curTrip - trip.km).coerceAtLeast(0f)
        val newLiters = (curLiters + trip.liters).coerceIn(0f, cap)

        prefs.edit()
            .putFloat("FUEL_ODOMETER", newOdo)
            .putFloat("FUEL_TRIP_KM", newTrip)
            .putFloat("FUEL_CURRENT_LITERS", newLiters)
            .putBoolean("FUEL_RESERVE_ALERTED", newLiters <= res)
            .apply()

        FuelStatsHelper.adjustDailyDistance(context, trip.endTs, -trip.km, -trip.liters, -trip.cost)
        context.startService(Intent(context, MediaService::class.java).apply { action = "ACTION_REFRESH_FUEL" })
    }

    // --- Gerenciamento de Regras de Auto-Etiqueta ---

    fun showAutoTagRulesDialog(context: Context, onChanged: () -> Unit = {}) {
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.dialog_auto_tag_rules)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((context.resources.displayMetrics.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        val container = dialog.findViewById<LinearLayout>(R.id.layoutRulesContainer)
        val dm = context.resources.displayMetrics
        fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), dm).toInt()

        lateinit var renderRules: () -> Unit
        renderRules = {
            container.removeAllViews()
            val rules = TripHistoryManager.getRules(context)
            if (rules.isEmpty()) {
                val tvEmpty = TextView(context).apply {
                    text = "No auto-tag rules yet.\nTap '+ New' to automatically tag routes like Work."
                    setTextColor(Color.parseColor("#777777"))
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setPadding(dp(12), dp(40), dp(12), dp(40))
                }
                container.addView(tvEmpty)
            } else {
                for (rule in rules) {
                    val card = MaterialCardView(context).apply {
                        setCardBackgroundColor(Color.parseColor("#222222"))
                        radius = dp(12).toFloat()
                        strokeColor = Color.parseColor("#333333")
                        strokeWidth = 2
                        cardElevation = 0f
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            showEditRuleDialog(context, rule, isNew = false) {
                                renderRules()
                                onChanged()
                            }
                        }
                    }
                    card.layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(8) }

                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(12), dp(10), dp(10), dp(10))
                    }

                    val textCol = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                    }
                    val tvName = TextView(context).apply {
                        text = "🏷️ ${rule.tagName}"
                        setTextColor(if (rule.enabled) Color.WHITE else Color.parseColor("#777777"))
                        textSize = 14f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                    }

                    val parts = mutableListOf<String>()
                    if (rule.targetKm > 0f) {
                        parts.add(String.format(Locale.US, "%.1f ± %.1f km", rule.targetKm, rule.kmMargin))
                    }
                    if (rule.startMinutes >= 0 && rule.endMinutes >= 0) {
                        parts.add("${TripHistoryManager.formatMinutesOfDay(rule.startMinutes)}–${TripHistoryManager.formatMinutesOfDay(rule.endMinutes)}")
                    }
                    parts.add(TripHistoryManager.formatDaysSummary(rule.daysOfWeek))

                    val tvSummary = TextView(context).apply {
                        text = parts.joinToString(" • ")
                        setTextColor(Color.parseColor("#AAAAAA"))
                        textSize = 11f
                        setPadding(0, dp(3), 0, 0)
                    }

                    textCol.addView(tvName)
                    textCol.addView(tvSummary)

                    val sw = SwitchMaterial(context).apply {
                        isChecked = rule.enabled
                        setOnCheckedChangeListener { _, checked ->
                            TripHistoryManager.upsertRule(context, rule.copy(enabled = checked))
                            tvName.setTextColor(if (checked) Color.WHITE else Color.parseColor("#777777"))
                            onChanged()
                        }
                    }

                    row.addView(textCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    row.addView(sw)
                    card.addView(row)
                    container.addView(card)
                }
            }
        }

        dialog.findViewById<View>(R.id.btnAddAutoTagRule).setOnClickListener {
            showEditRuleDialog(context, null, isNew = true) {
                renderRules()
                onChanged()
            }
        }

        dialog.findViewById<View>(R.id.btnCloseAutoTagRules).setOnClickListener {
            dialog.dismiss()
        }

        renderRules()
        dialog.show()
    }

    private fun showEditRuleDialog(
        context: Context,
        existingRule: AutoTagRule?,
        isNew: Boolean,
        onSaved: () -> Unit
    ) {
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.dialog_edit_rule)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((context.resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        val tvTitle = dialog.findViewById<TextView>(R.id.tvEditRuleTitle)
        val editTagName = dialog.findViewById<EditText>(R.id.editRuleTagName)
        val editTargetKm = dialog.findViewById<EditText>(R.id.editRuleTargetKm)
        val editMargin = dialog.findViewById<EditText>(R.id.editRuleKmMargin)
        val btnStartTime = dialog.findViewById<MaterialButton>(R.id.btnRuleStartTime)
        val btnEndTime = dialog.findViewById<MaterialButton>(R.id.btnRuleEndTime)
        val btnClearTime = dialog.findViewById<View>(R.id.btnClearRuleTime)
        val btnDelete = dialog.findViewById<View>(R.id.btnDeleteRule)

        tvTitle.text = if (isNew) "New Auto-Tag Rule" else "Edit Auto-Tag Rule"
        btnDelete.visibility = if (isNew) View.GONE else View.VISIBLE

        var startMinutes = existingRule?.startMinutes ?: -1
        var endMinutes = existingRule?.endMinutes ?: -1
        val selectedDays = mutableSetOf<Int>()

        if (existingRule != null) {
            editTagName.setText(existingRule.tagName)
            if (existingRule.targetKm > 0f) {
                editTargetKm.setText(String.format(Locale.US, "%.1f", existingRule.targetKm))
            }
            editMargin.setText(String.format(Locale.US, "%.1f", existingRule.kmMargin))
            existingRule.daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }.forEach {
                selectedDays.add(it)
            }
        } else {
            editMargin.setText("2.0")
        }

        fun refreshTimeButtons() {
            btnStartTime.text = if (startMinutes >= 0) "Start: ${TripHistoryManager.formatMinutesOfDay(startMinutes)}" else "Start: Any"
            btnEndTime.text = if (endMinutes >= 0) "End: ${TripHistoryManager.formatMinutesOfDay(endMinutes)}" else "End: Any"
        }
        refreshTimeButtons()

        btnStartTime.setOnClickListener {
            val initH = if (startMinutes >= 0) startMinutes / 60 else 21
            val initM = if (startMinutes >= 0) startMinutes % 60 else 0
            TimePickerDialog(context, android.R.style.Theme_DeviceDefault_Dialog_Alert, { _, h, m ->
                startMinutes = h * 60 + m
                if (endMinutes < 0) endMinutes = (startMinutes + 60) % 1440
                refreshTimeButtons()
            }, initH, initM, true).show()
        }

        btnEndTime.setOnClickListener {
            val initH = if (endMinutes >= 0) endMinutes / 60 else 22
            val initM = if (endMinutes >= 0) endMinutes % 60 else 0
            TimePickerDialog(context, android.R.style.Theme_DeviceDefault_Dialog_Alert, { _, h, m ->
                endMinutes = h * 60 + m
                if (startMinutes < 0) startMinutes = (endMinutes - 60 + 1440) % 1440
                refreshTimeButtons()
            }, initH, initM, true).show()
        }

        btnClearTime.setOnClickListener {
            startMinutes = -1
            endMinutes = -1
            refreshTimeButtons()
        }

        val dayButtons = listOf(
            Calendar.SUNDAY to dialog.findViewById<MaterialButton>(R.id.btnDaySun),
            Calendar.MONDAY to dialog.findViewById<MaterialButton>(R.id.btnDayMon),
            Calendar.TUESDAY to dialog.findViewById<MaterialButton>(R.id.btnDayTue),
            Calendar.WEDNESDAY to dialog.findViewById<MaterialButton>(R.id.btnDayWed),
            Calendar.THURSDAY to dialog.findViewById<MaterialButton>(R.id.btnDayThu),
            Calendar.FRIDAY to dialog.findViewById<MaterialButton>(R.id.btnDayFri),
            Calendar.SATURDAY to dialog.findViewById<MaterialButton>(R.id.btnDaySat)
        )

        fun refreshDayButtons() {
            for ((dayConst, btn) in dayButtons) {
                val active = selectedDays.contains(dayConst)
                if (active) {
                    btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#BB86FC"))
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
        refreshDayButtons()

        for ((dayConst, btn) in dayButtons) {
            btn.setOnClickListener {
                if (selectedDays.contains(dayConst)) selectedDays.remove(dayConst) else selectedDays.add(dayConst)
                refreshDayButtons()
            }
        }

        btnDelete.setOnClickListener {
            if (existingRule != null) {
                TripHistoryManager.deleteRule(context, existingRule.id)
                dialog.dismiss()
                onSaved()
            }
        }

        dialog.findViewById<View>(R.id.btnCancelRule).setOnClickListener { dialog.dismiss() }

        dialog.findViewById<View>(R.id.btnSaveRule).setOnClickListener {
            val tagName = editTagName.text.toString().trim()
            if (tagName.isEmpty()) {
                Toast.makeText(context, "Enter a Tag Name (e.g. Work)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val targetKm = editTargetKm.text.toString().replace(",", ".").toFloatOrNull() ?: 0f
            val margin = (editMargin.text.toString().replace(",", ".").toFloatOrNull() ?: 2.0f).coerceAtLeast(0.1f)
            val daysStr = selectedDays.sorted().joinToString(",")

            if (targetKm <= 0f && (startMinutes < 0 || endMinutes < 0) && daysStr.isEmpty()) {
                Toast.makeText(context, "Set at least one condition (distance, time, or day).", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val rule = AutoTagRule(
                id = existingRule?.id ?: UUID.randomUUID().toString(),
                tagName = tagName,
                enabled = existingRule?.enabled ?: true,
                targetKm = targetKm,
                kmMargin = margin,
                startMinutes = startMinutes,
                endMinutes = endMinutes,
                daysOfWeek = daysStr
            )
            TripHistoryManager.upsertRule(context, rule)
            dialog.dismiss()
            onSaved()
        }

        dialog.show()
    }
}
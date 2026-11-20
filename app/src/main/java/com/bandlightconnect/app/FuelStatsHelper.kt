package com.bandlightconnect.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class StatsPeriod { DAY, WEEK, MONTH, YEAR }
enum class StatsMetric { DISTANCE, REAL_KML, PUMP_SPEND }

data class ChartBarData(
    val label: String,
    val fullLabel: String,
    val km: Float,
    val liters: Float,
    val cost: Float,
    val displayValue: Float = km,
    val detailText: String = ""
)

data class PeriodPumpStats(
    val refuelCount: Int,
    val totalLiters: Float,
    val totalPaid: Float,
    val weightedAvgPrice: Float,
    val realAvgKmL: Float
)

data class OilStats(
    val avgDaysBetweenChanges: Int?,
    val changeCount: Int,
    val avgKmPerDay30d: Float,
    val estimatedDaysUntilNext: Int?,
    val estimatedNextDateStr: String?
)

data class TripRecord(
    val startTs: Long,
    val endTs: Long,
    val durationMs: Long,
    val km: Float,
    val maxSpeed: Float
)

object FuelStatsHelper {

    private const val PREFS_NAME = "BandTriggerPrefs"
    private const val KEY_DAILY_KM_LOG = "FUEL_DAILY_KM_LOG"
    private const val KEY_DAILY_LITERS_LOG = "FUEL_DAILY_LITERS_LOG"
    private const val KEY_DAILY_COST_LOG = "FUEL_DAILY_COST_LOG"
    private const val KEY_OIL_HISTORY = "FUEL_OIL_HISTORY"
    private const val KEY_REFUEL_HISTORY = "FUEL_REFUEL_HISTORY"

    private const val KEY_LAST_LOG_DATE = "FUEL_LAST_LOG_DATE"
    private const val KEY_LAST_LOG_KM = "FUEL_LAST_LOG_KM"
    private const val KEY_LAST_LOG_LITERS = "FUEL_LAST_LOG_LITERS"
    private const val KEY_LAST_LOG_COST = "FUEL_LAST_LOG_COST"
    private const val KEY_LAST_LOG_REVERTED = "FUEL_LAST_LOG_REVERTED"

    private const val KEY_TRIP_HISTORY = "FUEL_TRIP_HISTORY"
    private const val KEY_UNDONE_TRIP_BACKUP = "FUEL_UNDONE_TRIP_BACKUP"
    private const val KEY_LAST_TRIP_REVERTED = "FUEL_LAST_TRIP_REVERTED"

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun formatCurrency(amount: Float): String {
        return NumberFormat.getCurrencyInstance(Locale.getDefault()).format(amount.toDouble())
    }

    fun adjustDailyDistance(
        context: Context,
        timestampMs: Long,
        deltaKm: Float,
        deltaLiters: Float,
        deltaCost: Float
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dateKey = isoFormat.format(Date(timestampMs))

        val kmMap = loadFloatMap(prefs.getString(KEY_DAILY_KM_LOG, "{}"))
        val litersMap = loadFloatMap(prefs.getString(KEY_DAILY_LITERS_LOG, "{}"))
        val costMap = loadFloatMap(prefs.getString(KEY_DAILY_COST_LOG, "{}"))

        kmMap[dateKey] = ((kmMap[dateKey] ?: 0f) + deltaKm).coerceAtLeast(0f)
        litersMap[dateKey] = ((litersMap[dateKey] ?: 0f) + deltaLiters).coerceAtLeast(0f)
        costMap[dateKey] = ((costMap[dateKey] ?: 0f) + deltaCost).coerceAtLeast(0f)

        prefs.edit()
            .putString(KEY_DAILY_KM_LOG, saveFloatMap(kmMap))
            .putString(KEY_DAILY_LITERS_LOG, saveFloatMap(litersMap))
            .putString(KEY_DAILY_COST_LOG, saveFloatMap(costMap))
            .apply()
    }

    fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return when {
            hours > 0L -> String.format(Locale.US, "%dh %02dm", hours, minutes)
            minutes > 0L -> String.format(Locale.US, "%dm %02ds", minutes, seconds)
            else -> String.format(Locale.US, "%ds", seconds)
        }
    }

    fun formatClockTime(timestampMs: Long): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMs))
    }

    fun recordTrip(
        context: Context,
        startTs: Long,
        endTs: Long,
        durationMs: Long,
        km: Float,
        maxSpeed: Float
    ) {
        if (km < 0.01f) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = try {
            JSONArray(prefs.getString(KEY_TRIP_HISTORY, "[]"))
        } catch (_: Exception) {
            JSONArray()
        }

        arr.put(JSONObject().apply {
            put("startTs", startTs)
            put("endTs", endTs)
            put("durationMs", durationMs)
            put("km", km.toDouble())
            put("maxSpeed", maxSpeed.toDouble())
        })

        // Mantém no máximo as 50 viagens mais recentes para não pesar o SharedPreferences
        val trimmed = JSONArray()
        val startIdx = (arr.length() - 50).coerceAtLeast(0)
        for (i in startIdx until arr.length()) {
            trimmed.put(arr.getJSONObject(i))
        }

        prefs.edit()
            .putString(KEY_TRIP_HISTORY, trimmed.toString())
            .remove(KEY_UNDONE_TRIP_BACKUP)
            .putBoolean(KEY_LAST_TRIP_REVERTED, false)
            .apply()
    }

    fun toggleUndoLastTrip(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wasReverted = prefs.getBoolean(KEY_LAST_TRIP_REVERTED, false)
        val arr = try {
            JSONArray(prefs.getString(KEY_TRIP_HISTORY, "[]"))
        } catch (_: Exception) {
            JSONArray()
        }

        if (!wasReverted) {
            if (arr.length() == 0) return
            val lastObj = arr.getJSONObject(arr.length() - 1)
            val updated = JSONArray()
            for (i in 0 until arr.length() - 1) {
                updated.put(arr.getJSONObject(i))
            }
            prefs.edit()
                .putString(KEY_TRIP_HISTORY, updated.toString())
                .putString(KEY_UNDONE_TRIP_BACKUP, lastObj.toString())
                .putBoolean(KEY_LAST_TRIP_REVERTED, true)
                .apply()
        } else {
            val backupStr = prefs.getString(KEY_UNDONE_TRIP_BACKUP, null) ?: return
            try {
                arr.put(JSONObject(backupStr))
                prefs.edit()
                    .putString(KEY_TRIP_HISTORY, arr.toString())
                    .putBoolean(KEY_LAST_TRIP_REVERTED, false)
                    .apply()
            } catch (_: Exception) { }
        }
    }

    fun getRecentTrips(context: Context, limit: Int = 5): List<TripRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val list = mutableListOf<TripRecord>()
        try {
            val arr = JSONArray(prefs.getString(KEY_TRIP_HISTORY, "[]"))
            for (i in arr.length() - 1 downTo 0) {
                val obj = arr.getJSONObject(i)
                list.add(
                    TripRecord(
                        startTs = obj.optLong("startTs", 0L),
                        endTs = obj.optLong("endTs", 0L),
                        durationMs = obj.optLong("durationMs", 0L),
                        km = obj.optDouble("km", 0.0).toFloat(),
                        maxSpeed = obj.optDouble("maxSpeed", 0.0).toFloat()
                    )
                )
                if (list.size >= limit) break
            }
        } catch (_: Exception) { }
        return list
    }

    fun recordDistance(context: Context, deltaKm: Float) {
        if (deltaKm < 0.01f) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val avgKmL = prefs.getFloat("FUEL_AVG_KML", 35.0f).coerceAtLeast(1.0f)
        val activePrice = prefs.getFloat("FUEL_PRICE_PER_LITER", 6.00f).coerceAtLeast(0.01f)

        val deltaLiters = deltaKm / avgKmL
        val deltaCost = deltaLiters * activePrice
        val todayKey = isoFormat.format(Date())

        val kmMap = loadFloatMap(prefs.getString(KEY_DAILY_KM_LOG, "{}"))
        val litersMap = loadFloatMap(prefs.getString(KEY_DAILY_LITERS_LOG, "{}"))
        val costMap = loadFloatMap(prefs.getString(KEY_DAILY_COST_LOG, "{}"))

        kmMap[todayKey] = (kmMap[todayKey] ?: 0f) + deltaKm
        litersMap[todayKey] = (litersMap[todayKey] ?: 0f) + deltaLiters
        costMap[todayKey] = (costMap[todayKey] ?: 0f) + deltaCost

        prefs.edit()
            .putString(KEY_DAILY_KM_LOG, saveFloatMap(kmMap))
            .putString(KEY_DAILY_LITERS_LOG, saveFloatMap(litersMap))
            .putString(KEY_DAILY_COST_LOG, saveFloatMap(costMap))
            .putString(KEY_LAST_LOG_DATE, todayKey)
            .putFloat(KEY_LAST_LOG_KM, deltaKm)
            .putFloat(KEY_LAST_LOG_LITERS, deltaLiters)
            .putFloat(KEY_LAST_LOG_COST, deltaCost)
            .putBoolean(KEY_LAST_LOG_REVERTED, false)
            .apply()
    }

    fun toggleUndoLastDistance(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastDate = prefs.getString(KEY_LAST_LOG_DATE, null) ?: return
        val lastKm = prefs.getFloat(KEY_LAST_LOG_KM, 0f)
        if (lastKm < 0.01f) return

        val lastLiters = prefs.getFloat(KEY_LAST_LOG_LITERS, 0f)
        val lastCost = prefs.getFloat(KEY_LAST_LOG_COST, 0f)
        val wasReverted = prefs.getBoolean(KEY_LAST_LOG_REVERTED, false)

        val kmMap = loadFloatMap(prefs.getString(KEY_DAILY_KM_LOG, "{}"))
        val litersMap = loadFloatMap(prefs.getString(KEY_DAILY_LITERS_LOG, "{}"))
        val costMap = loadFloatMap(prefs.getString(KEY_DAILY_COST_LOG, "{}"))

        if (!wasReverted) {
            kmMap[lastDate] = ((kmMap[lastDate] ?: 0f) - lastKm).coerceAtLeast(0f)
            litersMap[lastDate] = ((litersMap[lastDate] ?: 0f) - lastLiters).coerceAtLeast(0f)
            costMap[lastDate] = ((costMap[lastDate] ?: 0f) - lastCost).coerceAtLeast(0f)
        } else {
            kmMap[lastDate] = (kmMap[lastDate] ?: 0f) + lastKm
            litersMap[lastDate] = (litersMap[lastDate] ?: 0f) + lastLiters
            costMap[lastDate] = (costMap[lastDate] ?: 0f) + lastCost
        }

        prefs.edit()
            .putString(KEY_DAILY_KM_LOG, saveFloatMap(kmMap))
            .putString(KEY_DAILY_LITERS_LOG, saveFloatMap(litersMap))
            .putString(KEY_DAILY_COST_LOG, saveFloatMap(costMap))
            .putBoolean(KEY_LAST_LOG_REVERTED, !wasReverted)
            .apply()
    }

    fun recordRefuel(
        context: Context,
        liters: Float,
        pricePerLiter: Float,
        totalPaid: Float,
        tankKm: Float,
        realKml: Float,
        odoKm: Float
    ) {
        if (liters <= 0f && totalPaid <= 0f) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = try {
            JSONArray(prefs.getString(KEY_REFUEL_HISTORY, "[]"))
        } catch (_: Exception) {
            JSONArray()
        }
        val finalTotal = if (totalPaid > 0f) totalPaid else (liters * pricePerLiter)
        val finalPrice = when {
            pricePerLiter > 0f -> pricePerLiter
            liters > 0f && finalTotal > 0f -> finalTotal / liters
            else -> 0f
        }
        arr.put(JSONObject().apply {
            put("ts", System.currentTimeMillis())
            put("liters", liters.toDouble())
            put("price", finalPrice.toDouble())
            put("total", finalTotal.toDouble())
            put("tankKm", tankKm.toDouble())
            put("realKml", realKml.toDouble())
            put("odo", odoKm.toDouble())
        })
        prefs.edit().putString(KEY_REFUEL_HISTORY, arr.toString()).apply()
    }

    fun getRealKmlChartData(context: Context): List<ChartBarData> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val entries = mutableListOf<JSONObject>()
        try {
            val arr = JSONArray(prefs.getString(KEY_REFUEL_HISTORY, "[]"))
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.optDouble("realKml", 0.0) > 0.0) {
                    entries.add(obj)
                }
            }
        } catch (_: Exception) { }

        val recent = entries.takeLast(8)
        val shortFmt = SimpleDateFormat("MM/dd", Locale.US)
        val fullFmt = SimpleDateFormat("MMM dd", Locale.US)

        return recent.map { obj ->
            val ts = obj.optLong("ts", System.currentTimeMillis())
            val date = Date(ts)
            val tankKm = obj.optDouble("tankKm", 0.0).toFloat()
            val liters = obj.optDouble("liters", 0.0).toFloat()
            val total = obj.optDouble("total", 0.0).toFloat()
            val realKml = obj.optDouble("realKml", 0.0).toFloat()
            val detail = buildString {
                append("${fullFmt.format(date)}: ")
                append(String.format(Locale.US, "%.2f km/L (%.1f km / %.2f L)", realKml, tankKm, liters))
                if (total > 0f) append(" • ${formatCurrency(total)}")
            }
            ChartBarData(
                label = shortFmt.format(date),
                fullLabel = fullFmt.format(date),
                km = tankKm,
                liters = liters,
                cost = total,
                displayValue = realKml,
                detailText = detail
            )
        }
    }

    fun getPumpSpendChartData(context: Context, period: StatsPeriod): List<ChartBarData> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dailyPaid = mutableMapOf<String, Float>()
        val dailyLiters = mutableMapOf<String, Float>()
        val dailyTankKm = mutableMapOf<String, Float>()

        try {
            val arr = JSONArray(prefs.getString(KEY_REFUEL_HISTORY, "[]"))
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val ts = obj.optLong("ts", 0L)
                if (ts <= 0L) continue
                val key = isoFormat.format(Date(ts))
                dailyPaid[key] = (dailyPaid[key] ?: 0f) + obj.optDouble("total", 0.0).toFloat()
                dailyLiters[key] = (dailyLiters[key] ?: 0f) + obj.optDouble("liters", 0.0).toFloat()
                dailyTankKm[key] = (dailyTankKm[key] ?: 0f) + obj.optDouble("tankKm", 0.0).toFloat()
            }
        } catch (_: Exception) { }

        fun buildBar(label: String, full: String, km: Float, liters: Float, paid: Float): ChartBarData {
            val detail = if (paid > 0f) {
                val avgP = if (liters > 0f) " • ${formatCurrency(paid / liters)}/L" else ""
                String.format(Locale.US, "%s: %s (%.2f L)%s", full, formatCurrency(paid), liters, avgP)
            } else {
                "$full: No pump refuels"
            }
            return ChartBarData(label, full, km, liters, paid, displayValue = paid, detailText = detail)
        }

        val result = mutableListOf<ChartBarData>()
        when (period) {
            StatsPeriod.DAY -> {
                val dayNames = arrayOf("", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                val shortDate = SimpleDateFormat("MMM dd", Locale.US)
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -6)
                for (i in 0 until 7) {
                    val key = isoFormat.format(cal.time)
                    val dow = cal.get(Calendar.DAY_OF_WEEK)
                    val label = dayNames[dow]
                    val full = "${shortDate.format(cal.time)} ($label)"
                    result.add(buildBar(label, full, dailyTankKm[key] ?: 0f, dailyLiters[key] ?: 0f, dailyPaid[key] ?: 0f))
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            StatsPeriod.WEEK -> {
                val shortDate = SimpleDateFormat("MM/dd", Locale.US)
                val baseCal = Calendar.getInstance().apply {
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                }
                baseCal.add(Calendar.WEEK_OF_YEAR, -5)
                for (w in 0 until 6) {
                    val weekStart = baseCal.clone() as Calendar
                    var sumKm = 0f
                    var sumL = 0f
                    var sumP = 0f
                    val iter = weekStart.clone() as Calendar
                    for (d in 0 until 7) {
                        val key = isoFormat.format(iter.time)
                        sumKm += dailyTankKm[key] ?: 0f
                        sumL += dailyLiters[key] ?: 0f
                        sumP += dailyPaid[key] ?: 0f
                        iter.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    val startStr = shortDate.format(weekStart.time)
                    result.add(buildBar(startStr, "Week of $startStr", sumKm, sumL, sumP))
                    baseCal.add(Calendar.WEEK_OF_YEAR, 1)
                }
            }
            StatsPeriod.MONTH -> {
                val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dec")
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val kmTotals = FloatArray(12)
                val lTotals = FloatArray(12)
                val pTotals = FloatArray(12)
                for (dateKey in dailyPaid.keys) {
                    val parts = dateKey.split("-")
                    if (parts.size == 3 && parts[0].toIntOrNull() == currentYear) {
                        val mIdx = (parts[1].toIntOrNull() ?: 1) - 1
                        if (mIdx in 0..11) {
                            kmTotals[mIdx] += dailyTankKm[dateKey] ?: 0f
                            lTotals[mIdx] += dailyLiters[dateKey] ?: 0f
                            pTotals[mIdx] += dailyPaid[dateKey] ?: 0f
                        }
                    }
                }
                for (m in 0 until 12) {
                    val mName = if (m == 7) "Aug" else months[m]
                    result.add(buildBar(mName, "$mName $currentYear", kmTotals[m], lTotals[m], pTotals[m]))
                }
            }
            StatsPeriod.YEAR -> {
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val yearKm = mutableMapOf<Int, Float>()
                val yearL = mutableMapOf<Int, Float>()
                val yearP = mutableMapOf<Int, Float>()
                for (dateKey in dailyPaid.keys) {
                    val y = dateKey.take(4).toIntOrNull() ?: continue
                    yearKm[y] = (yearKm[y] ?: 0f) + (dailyTankKm[dateKey] ?: 0f)
                    yearL[y] = (yearL[y] ?: 0f) + (dailyLiters[dateKey] ?: 0f)
                    yearP[y] = (yearP[y] ?: 0f) + (dailyPaid[dateKey] ?: 0f)
                }
                val startYear = minOf(currentYear - 3, yearP.keys.minOrNull() ?: (currentYear - 3))
                for (y in startYear..currentYear) {
                    result.add(buildBar(y.toString(), "Year $y", yearKm[y] ?: 0f, yearL[y] ?: 0f, yearP[y] ?: 0f))
                }
            }
        }
        return result
    }

    fun getPeriodPumpStats(context: Context, period: StatsPeriod): PeriodPumpStats {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val startCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        when (period) {
            StatsPeriod.DAY -> startCal.add(Calendar.DAY_OF_YEAR, -6)
            StatsPeriod.WEEK -> {
                startCal.firstDayOfWeek = Calendar.MONDAY
                startCal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                startCal.add(Calendar.WEEK_OF_YEAR, -5)
            }
            StatsPeriod.MONTH -> {
                startCal.set(Calendar.MONTH, Calendar.JANUARY)
                startCal.set(Calendar.DAY_OF_MONTH, 1)
            }
            StatsPeriod.YEAR -> {
                startCal.add(Calendar.YEAR, -3)
                startCal.set(Calendar.MONTH, Calendar.JANUARY)
                startCal.set(Calendar.DAY_OF_MONTH, 1)
            }
        }
        val minTs = startCal.timeInMillis

        var count = 0
        var sumLiters = 0f
        var sumPaid = 0f
        var sumKmForReal = 0f
        var sumLitersForReal = 0f
        try {
            val arr = JSONArray(prefs.getString(KEY_REFUEL_HISTORY, "[]"))
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val ts = obj.optLong("ts", 0L)
                if (ts >= minTs) {
                    val l = obj.optDouble("liters", 0.0).toFloat()
                    val t = obj.optDouble("total", 0.0).toFloat()
                    val tk = obj.optDouble("tankKm", 0.0).toFloat()
                    if (l > 0f || t > 0f) {
                        count++
                        sumLiters += l
                        sumPaid += t
                        if (tk > 0f && l > 0f) {
                            sumKmForReal += tk
                            sumLitersForReal += l
                        }
                    }
                }
            }
        } catch (_: Exception) { }

        val weightedAvgPrice = if (sumLiters > 0f && sumPaid > 0f) sumPaid / sumLiters else 0f
        val realAvgKmL = if (sumLitersForReal > 0f && sumKmForReal > 0f) sumKmForReal / sumLitersForReal else 0f
        return PeriodPumpStats(count, sumLiters, sumPaid, weightedAvgPrice, realAvgKmL)
    }

    fun recordOilChange(context: Context, odoKm: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = try {
            JSONArray(prefs.getString(KEY_OIL_HISTORY, "[]"))
        } catch (_: Exception) {
            JSONArray()
        }
        arr.put(JSONObject().apply {
            put("ts", System.currentTimeMillis())
            put("odo", odoKm.toDouble())
        })
        prefs.edit().putString(KEY_OIL_HISTORY, arr.toString()).apply()
    }

    fun getChartData(context: Context, period: StatsPeriod): List<ChartBarData> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val kmMap = loadFloatMap(prefs.getString(KEY_DAILY_KM_LOG, "{}"))
        val litersMap = loadFloatMap(prefs.getString(KEY_DAILY_LITERS_LOG, "{}"))
        val costMap = loadFloatMap(prefs.getString(KEY_DAILY_COST_LOG, "{}"))

        val fallbackAvg = prefs.getFloat("FUEL_AVG_KML", 35.0f).coerceAtLeast(1.0f)
        val fallbackPrice = prefs.getFloat("FUEL_PRICE_PER_LITER", 6.00f).coerceAtLeast(0.01f)

        fun dayValues(key: String): Triple<Float, Float, Float> {
            val km = kmMap[key] ?: 0f
            if (km <= 0f) return Triple(0f, 0f, 0f)
            val liters = litersMap[key] ?: (km / fallbackAvg)
            val cost = costMap[key] ?: (liters * fallbackPrice)
            return Triple(km, liters, cost)
        }

        fun buildDistBar(label: String, full: String, km: Float, liters: Float, cost: Float): ChartBarData {
            val detail = String.format(Locale.US, "%s: %.1f km • %.2f L • %s", full, km, liters, formatCurrency(cost))
            return ChartBarData(label, full, km, liters, cost, displayValue = km, detailText = detail)
        }

        val result = mutableListOf<ChartBarData>()
        when (period) {
            StatsPeriod.DAY -> {
                val dayNames = arrayOf("", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                val shortDate = SimpleDateFormat("MMM dd", Locale.US)
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -6)
                for (i in 0 until 7) {
                    val key = isoFormat.format(cal.time)
                    val dow = cal.get(Calendar.DAY_OF_WEEK)
                    val label = dayNames[dow]
                    val full = "${shortDate.format(cal.time)} ($label)"
                    val (km, liters, cost) = dayValues(key)
                    result.add(buildDistBar(label, full, km, liters, cost))
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            StatsPeriod.WEEK -> {
                val shortDate = SimpleDateFormat("MM/dd", Locale.US)
                val baseCal = Calendar.getInstance().apply {
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                }
                baseCal.add(Calendar.WEEK_OF_YEAR, -5)
                for (w in 0 until 6) {
                    val weekStart = baseCal.clone() as Calendar
                    var sumKm = 0f
                    var sumLiters = 0f
                    var sumCost = 0f
                    val iter = weekStart.clone() as Calendar
                    for (d in 0 until 7) {
                        val (km, liters, cost) = dayValues(isoFormat.format(iter.time))
                        sumKm += km
                        sumLiters += liters
                        sumCost += cost
                        iter.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    val startStr = shortDate.format(weekStart.time)
                    result.add(buildDistBar(startStr, "Week of $startStr", sumKm, sumLiters, sumCost))
                    baseCal.add(Calendar.WEEK_OF_YEAR, 1)
                }
            }
            StatsPeriod.MONTH -> {
                val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val kmTotals = FloatArray(12)
                val literTotals = FloatArray(12)
                val costTotals = FloatArray(12)
                for (dateKey in kmMap.keys) {
                    val parts = dateKey.split("-")
                    if (parts.size == 3 && parts[0].toIntOrNull() == currentYear) {
                        val mIdx = (parts[1].toIntOrNull() ?: 1) - 1
                        if (mIdx in 0..11) {
                            val (km, liters, cost) = dayValues(dateKey)
                            kmTotals[mIdx] += km
                            literTotals[mIdx] += liters
                            costTotals[mIdx] += cost
                        }
                    }
                }
                for (m in 0 until 12) {
                    result.add(buildDistBar(months[m], "${months[m]} $currentYear", kmTotals[m], literTotals[m], costTotals[m]))
                }
            }
            StatsPeriod.YEAR -> {
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val yearKm = mutableMapOf<Int, Float>()
                val yearLiters = mutableMapOf<Int, Float>()
                val yearCost = mutableMapOf<Int, Float>()
                for (dateKey in kmMap.keys) {
                    val y = dateKey.take(4).toIntOrNull() ?: continue
                    val (km, liters, cost) = dayValues(dateKey)
                    yearKm[y] = (yearKm[y] ?: 0f) + km
                    yearLiters[y] = (yearLiters[y] ?: 0f) + liters
                    yearCost[y] = (yearCost[y] ?: 0f) + cost
                }
                val startYear = minOf(currentYear - 3, yearKm.keys.minOrNull() ?: (currentYear - 3))
                for (y in startYear..currentYear) {
                    result.add(buildDistBar(y.toString(), "Year $y", yearKm[y] ?: 0f, yearLiters[y] ?: 0f, yearCost[y] ?: 0f))
                }
            }
        }
        return result
    }

    fun getOilStats(context: Context): OilStats {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val timestamps = mutableListOf<Long>()
        try {
            val arr = JSONArray(prefs.getString(KEY_OIL_HISTORY, "[]"))
            for (i in 0 until arr.length()) {
                val ts = arr.getJSONObject(i).optLong("ts", 0L)
                if (ts > 0L) timestamps.add(ts)
            }
        } catch (_: Exception) { }

        timestamps.sort()
        val avgDays = if (timestamps.size >= 2) {
            var totalDiffMs = 0L
            for (i in 1 until timestamps.size) {
                totalDiffMs += (timestamps[i] - timestamps[i - 1])
            }
            val avgMs = totalDiffMs / (timestamps.size - 1)
            (avgMs / (24.0 * 60 * 60 * 1000)).roundToInt().coerceAtLeast(1)
        } else {
            null
        }

        val dailyMap = loadFloatMap(prefs.getString(KEY_DAILY_KM_LOG, "{}"))
        val cal = Calendar.getInstance()
        var km30d = 0f
        var oldestRecordedOffset = -1
        for (i in 0 until 30) {
            val key = isoFormat.format(cal.time)
            val v = dailyMap[key] ?: 0f
            if (v > 0f) {
                km30d += v
                oldestRecordedOffset = i
            }
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        val activeDaysSpan = if (oldestRecordedOffset >= 0) (oldestRecordedOffset + 1).coerceAtLeast(1) else 0
        val avgKmPerDay = if (activeDaysSpan > 0 && km30d > 0f) km30d / activeDaysSpan else 0f

        val currentOdo = prefs.getFloat("FUEL_ODOMETER", 0f)
        val oilLastOdo = prefs.getFloat("FUEL_OIL_LAST_ODO", 0f)
        val oilInterval = prefs.getFloat("FUEL_OIL_INTERVAL", 1000f).coerceAtLeast(100f)

        var estDaysLeft: Int? = null
        var estDateStr: String? = null
        if (oilLastOdo > 0f && currentOdo > 0f && avgKmPerDay >= 1f) {
            val kmLeft = oilInterval - (currentOdo - oilLastOdo)
            if (kmLeft <= 0f) {
                estDaysLeft = 0
                estDateStr = "Now (overdue)"
            } else {
                val days = (kmLeft / avgKmPerDay).roundToInt().coerceAtLeast(1)
                estDaysLeft = days
                val futureCal = Calendar.getInstance()
                futureCal.add(Calendar.DAY_OF_YEAR, days)
                val fmt = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                estDateStr = "~${fmt.format(futureCal.time)} (in $days days)"
            }
        }

        return OilStats(
            avgDaysBetweenChanges = avgDays,
            changeCount = timestamps.size,
            avgKmPerDay30d = avgKmPerDay,
            estimatedDaysUntilNext = estDaysLeft,
            estimatedNextDateStr = estDateStr
        )
    }

    private fun loadFloatMap(jsonStr: String?): MutableMap<String, Float> {
        val map = mutableMapOf<String, Float>()
        try {
            val obj = JSONObject(jsonStr ?: "{}")
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.optDouble(k, 0.0).toFloat()
            }
        } catch (_: Exception) { }
        return map
    }

    private fun saveFloatMap(map: Map<String, Float>): String {
        val obj = JSONObject()
        for ((k, v) in map) {
            if (v > 0f) obj.put(k, v.toDouble())
        }
        return obj.toString()
    }
}
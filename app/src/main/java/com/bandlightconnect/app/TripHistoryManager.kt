package com.bandlightconnect.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

data class TripEntry(
    val id: String,
    val startTs: Long,
    val endTs: Long,
    val durationMs: Long,
    val km: Float,
    val maxSpeed: Float,
    val liters: Float,
    val cost: Float,
    val tag: String = "",
    val isManual: Boolean = false,
    val note: String = ""
)

data class AutoTagRule(
    val id: String,
    val tagName: String,
    val enabled: Boolean = true,
    val targetKm: Float = 0f,
    val kmMargin: Float = 2.0f,
    val startMinutes: Int = -1,
    val endMinutes: Int = -1,
    val daysOfWeek: String = "" // Calendar.DAY_OF_WEEK separado por vírgula ("2,3,4,5,6"), vazio = todos
)

data class TagSummary(
    val tag: String, // "" representa Untagged
    val tripCount: Int,
    val totalKm: Float,
    val totalCost: Float
)

object TripHistoryManager {

    private const val PREFS_NAME = "BandTriggerPrefs"
    private const val KEY_MIGRATED = "TRIPS_MIGRATED_TO_FILES_V1"
    private const val KEY_LEGACY_TRIPS = "FUEL_TRIP_HISTORY"
    private const val KEY_RULES = "FUEL_AUTO_TAG_RULES"

    private const val KEY_LAST_TRIP_ID = "LAST_LOGGED_TRIP_ID"
    private const val KEY_LAST_TRIP_MONTH = "LAST_LOGGED_TRIP_MONTH"
    private const val KEY_LAST_TRIP_BACKUP = "LAST_LOGGED_TRIP_BACKUP"
    private const val KEY_LAST_TRIP_REVERTED = "LAST_LOGGED_TRIP_REVERTED"

    private val monthFileFmt = SimpleDateFormat("yyyy_MM", Locale.US)

    fun monthKey(year: Int, monthZeroBased: Int): String =
        String.format(Locale.US, "%04d_%02d", year, monthZeroBased + 1)

    fun monthKeyFromTs(timestampMs: Long): String =
        monthFileFmt.format(Date(timestampMs))

    private fun getMonthFile(context: Context, key: String): File =
        File(context.filesDir, "trips_$key.json")

    fun ensureMigrated(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIGRATED, false)) return

        val legacyStr = prefs.getString(KEY_LEGACY_TRIPS, null)
        if (!legacyStr.isNullOrBlank()) {
            val avgKmL = prefs.getFloat("FUEL_AVG_KML", 35.0f).coerceAtLeast(1.0f)
            val price = prefs.getFloat("FUEL_PRICE_PER_LITER", 6.00f).coerceAtLeast(0.01f)
            val byMonth = mutableMapOf<String, MutableList<TripEntry>>()

            try {
                val arr = JSONArray(legacyStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val endTs = obj.optLong("endTs", System.currentTimeMillis())
                    val km = obj.optDouble("km", 0.0).toFloat()
                    if (km < 0.01f) continue
                    val liters = km / avgKmL
                    val cost = liters * price
                    val entry = TripEntry(
                        id = UUID.randomUUID().toString(),
                        startTs = obj.optLong("startTs", endTs),
                        endTs = endTs,
                        durationMs = obj.optLong("durationMs", 0L),
                        km = km,
                        maxSpeed = obj.optDouble("maxSpeed", 0.0).toFloat(),
                        liters = liters,
                        cost = cost,
                        tag = "",
                        isManual = false,
                        note = ""
                    )
                    val key = monthKeyFromTs(endTs)
                    byMonth.getOrPut(key) { mutableListOf() }.add(entry)
                }
                for ((key, entries) in byMonth) {
                    val existing = loadTripsByKey(context, key).toMutableList()
                    existing.addAll(entries)
                    saveTripsByKey(context, key, existing)
                }
            } catch (_: Exception) { }
        }
        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

    fun recordGpsTrip(
        context: Context,
        startTs: Long,
        endTs: Long,
        durationMs: Long,
        km: Float,
        maxSpeed: Float
    ): String {
        if (km < 0.01f) return ""
        ensureMigrated(context)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val avgKmL = prefs.getFloat("FUEL_AVG_KML", 35.0f).coerceAtLeast(1.0f)
        val price = prefs.getFloat("FUEL_PRICE_PER_LITER", 6.00f).coerceAtLeast(0.01f)
        val liters = km / avgKmL
        val cost = liters * price
        val autoTag = matchAutoTagRule(context, km, endTs)

        val entry = TripEntry(
            id = UUID.randomUUID().toString(),
            startTs = startTs,
            endTs = endTs,
            durationMs = durationMs,
            km = km,
            maxSpeed = maxSpeed,
            liters = liters,
            cost = cost,
            tag = autoTag,
            isManual = false,
            note = ""
        )

        val key = monthKeyFromTs(endTs)
        val list = loadTripsByKey(context, key).toMutableList()
        list.add(entry)
        saveTripsByKey(context, key, list)

        prefs.edit()
            .putString(KEY_LAST_TRIP_ID, entry.id)
            .putString(KEY_LAST_TRIP_MONTH, key)
            .remove(KEY_LAST_TRIP_BACKUP)
            .putBoolean(KEY_LAST_TRIP_REVERTED, false)
            .apply()

        return autoTag
    }

    fun recordManualTrip(
        context: Context,
        km: Float,
        note: String = "Added manually",
        explicitTag: String? = null
    ): TripEntry? {
        if (km < 0.01f) return null
        ensureMigrated(context)

        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val avgKmL = prefs.getFloat("FUEL_AVG_KML", 35.0f).coerceAtLeast(1.0f)
        val price = prefs.getFloat("FUEL_PRICE_PER_LITER", 6.00f).coerceAtLeast(0.01f)
        val liters = km / avgKmL
        val cost = liters * price
        val tag = explicitTag ?: matchAutoTagRule(context, km, now)

        val entry = TripEntry(
            id = UUID.randomUUID().toString(),
            startTs = now,
            endTs = now,
            durationMs = 0L,
            km = km,
            maxSpeed = 0f,
            liters = liters,
            cost = cost,
            tag = tag,
            isManual = true,
            note = note
        )

        val key = monthKeyFromTs(now)
        val list = loadTripsByKey(context, key).toMutableList()
        list.add(entry)
        saveTripsByKey(context, key, list)

        prefs.edit()
            .putString(KEY_LAST_TRIP_ID, entry.id)
            .putString(KEY_LAST_TRIP_MONTH, key)
            .remove(KEY_LAST_TRIP_BACKUP)
            .putBoolean(KEY_LAST_TRIP_REVERTED, false)
            .apply()

        return entry
    }

    fun toggleUndoLastTrip(context: Context) {
        ensureMigrated(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val tripId = prefs.getString(KEY_LAST_TRIP_ID, null) ?: return
        val mKey = prefs.getString(KEY_LAST_TRIP_MONTH, null) ?: return
        val wasReverted = prefs.getBoolean(KEY_LAST_TRIP_REVERTED, false)

        val list = loadTripsByKey(context, mKey).toMutableList()
        if (!wasReverted) {
            val target = list.find { it.id == tripId } ?: return
            list.removeAll { it.id == tripId }
            saveTripsByKey(context, mKey, list)
            prefs.edit()
                .putString(KEY_LAST_TRIP_BACKUP, entryToJson(target).toString())
                .putBoolean(KEY_LAST_TRIP_REVERTED, true)
                .apply()
        } else {
            val backupStr = prefs.getString(KEY_LAST_TRIP_BACKUP, null) ?: return
            try {
                val restored = jsonToEntry(JSONObject(backupStr))
                if (list.none { it.id == restored.id }) {
                    list.add(restored)
                    saveTripsByKey(context, mKey, list)
                }
                prefs.edit().putBoolean(KEY_LAST_TRIP_REVERTED, false).apply()
            } catch (_: Exception) { }
        }
    }

    fun getTripsForMonth(context: Context, year: Int, monthZeroBased: Int): List<TripEntry> {
        ensureMigrated(context)
        return loadTripsByKey(context, monthKey(year, monthZeroBased))
            .sortedByDescending { it.endTs }
    }

    fun getRecentTrips(context: Context, limit: Int = 5): List<TripEntry> {
        ensureMigrated(context)
        val files = context.filesDir.listFiles { f ->
            f.name.startsWith("trips_") && f.name.endsWith(".json")
        }?.sortedByDescending { it.name } ?: return emptyList()

        val result = mutableListOf<TripEntry>()
        for (file in files) {
            val key = file.name.removePrefix("trips_").removeSuffix(".json")
            val entries = loadTripsByKey(context, key).sortedByDescending { it.endTs }
            for (e in entries) {
                result.add(e)
                if (result.size >= limit) return result
            }
        }
        return result
    }

    fun updateTrip(context: Context, year: Int, monthZeroBased: Int, updated: TripEntry) {
        ensureMigrated(context)
        val key = monthKey(year, monthZeroBased)
        val list = loadTripsByKey(context, key).toMutableList()
        val idx = list.indexOfFirst { it.id == updated.id }
        if (idx >= 0) {
            list[idx] = updated
            saveTripsByKey(context, key, list)
        }
    }

    fun deleteTrip(context: Context, year: Int, monthZeroBased: Int, tripId: String): TripEntry? {
        ensureMigrated(context)
        val key = monthKey(year, monthZeroBased)
        val list = loadTripsByKey(context, key).toMutableList()
        val removed = list.find { it.id == tripId } ?: return null
        list.removeAll { it.id == tripId }
        saveTripsByKey(context, key, list)
        return removed
    }

    fun getTagSummaries(trips: List<TripEntry>): List<TagSummary> {
        val grouped = trips.groupBy { it.tag.trim() }
        return grouped.map { (tag, items) ->
            TagSummary(
                tag = tag,
                tripCount = items.size,
                totalKm = items.sumOf { it.km.toDouble() }.toFloat(),
                totalCost = items.sumOf { it.cost.toDouble() }.toFloat()
            )
        }.sortedWith(compareBy<TagSummary> { it.tag.isEmpty() }.thenByDescending { it.totalCost })
    }

    // --- Regras Personalizáveis de Auto-Etiqueta ---

    fun getRules(context: Context): MutableList<AutoTagRule> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val list = mutableListOf<AutoTagRule>()
        try {
            val arr = JSONArray(prefs.getString(KEY_RULES, "[]"))
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    AutoTagRule(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        tagName = obj.optString("tagName", "").trim(),
                        enabled = obj.optBoolean("enabled", true),
                        targetKm = obj.optDouble("targetKm", 0.0).toFloat(),
                        kmMargin = obj.optDouble("kmMargin", 2.0).toFloat(),
                        startMinutes = obj.optInt("startMinutes", -1),
                        endMinutes = obj.optInt("endMinutes", -1),
                        daysOfWeek = obj.optString("daysOfWeek", "")
                    )
                )
            }
        } catch (_: Exception) { }
        return list
    }

    fun saveRules(context: Context, rules: List<AutoTagRule>) {
        val arr = JSONArray()
        for (r in rules) {
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("tagName", r.tagName.trim())
                put("enabled", r.enabled)
                put("targetKm", r.targetKm.toDouble())
                put("kmMargin", r.kmMargin.toDouble())
                put("startMinutes", r.startMinutes)
                put("endMinutes", r.endMinutes)
                put("daysOfWeek", r.daysOfWeek)
            })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RULES, arr.toString())
            .apply()
    }

    fun upsertRule(context: Context, rule: AutoTagRule) {
        val rules = getRules(context)
        val idx = rules.indexOfFirst { it.id == rule.id }
        if (idx >= 0) rules[idx] = rule else rules.add(rule)
        saveRules(context, rules)
    }

    fun deleteRule(context: Context, ruleId: String) {
        val rules = getRules(context).filterNot { it.id == ruleId }
        saveRules(context, rules)
    }

    fun matchAutoTagRule(context: Context, km: Float, endTs: Long): String {
        val rules = getRules(context).filter { it.enabled && it.tagName.isNotBlank() }
        if (rules.isEmpty()) return ""

        val cal = Calendar.getInstance().apply { timeInMillis = endTs }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val tripMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        val candidates = mutableListOf<Pair<AutoTagRule, Float>>()
        for (rule in rules) {
            val hasDist = rule.targetKm > 0f
            val hasTime = rule.startMinutes in 0..1439 && rule.endMinutes in 0..1439
            val hasDays = rule.daysOfWeek.isNotBlank()
            if (!hasDist && !hasTime && !hasDays) continue

            if (hasDays) {
                val allowedDays = rule.daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
                if (allowedDays.isNotEmpty() && !allowedDays.contains(dayOfWeek)) continue
            }

            if (hasTime) {
                val inWindow = if (rule.startMinutes <= rule.endMinutes) {
                    tripMinutes in rule.startMinutes..rule.endMinutes
                } else {
                    // Suporta faixas que cruzam a meia-noite (ex: 23:30 às 00:30)
                    tripMinutes >= rule.startMinutes || tripMinutes <= rule.endMinutes
                }
                if (!inWindow) continue
            }

            val diffKm = if (hasDist) {
                val margin = rule.kmMargin.coerceAtLeast(0.1f)
                val diff = abs(km - rule.targetKm)
                if (diff > margin) continue
                diff
            } else {
                Float.MAX_VALUE
            }

            candidates.add(rule to diffKm)
        }

        if (candidates.isEmpty()) return ""
        // Desempate pela regra com distância alvo mais próxima
        return candidates.minByOrNull { it.second }?.first?.tagName ?: ""
    }

    fun formatMinutesOfDay(mins: Int): String {
        if (mins !in 0..1439) return "--:--"
        return String.format(Locale.US, "%02d:%02d", mins / 60, mins % 60)
    }

    fun formatDaysSummary(daysStr: String): String {
        if (daysStr.isBlank()) return "Every day"
        val map = mapOf(
            Calendar.SUNDAY to "Sun",
            Calendar.MONDAY to "Mon",
            Calendar.TUESDAY to "Tue",
            Calendar.WEDNESDAY to "Wed",
            Calendar.THURSDAY to "Thu",
            Calendar.FRIDAY to "Fri",
            Calendar.SATURDAY to "Sat"
        )
        val days = daysStr.split(",").mapNotNull { it.trim().toIntOrNull() }.distinct()
        if (days.size >= 7) return "Every day"
        return days.mapNotNull { map[it] }.joinToString(", ")
    }

    fun getKnownTags(context: Context, extraTrips: List<TripEntry> = emptyList()): List<String> {
        val set = linkedSetOf("Work", "Home", "Errands", "Leisure")
        getRules(context).forEach { if (it.tagName.isNotBlank()) set.add(it.tagName) }
        extraTrips.forEach { if (it.tag.isNotBlank()) set.add(it.tag) }
        return set.toList()
    }

    private fun loadTripsByKey(context: Context, key: String): List<TripEntry> {
        val file = getMonthFile(context, key)
        if (!file.exists()) return emptyList()
        val list = mutableListOf<TripEntry>()
        try {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                list.add(jsonToEntry(arr.getJSONObject(i)))
            }
        } catch (_: Exception) { }
        return list
    }

    private fun saveTripsByKey(context: Context, key: String, entries: List<TripEntry>) {
        val file = getMonthFile(context, key)
        if (entries.isEmpty()) {
            if (file.exists()) file.delete()
            return
        }
        val arr = JSONArray()
        for (e in entries) arr.put(entryToJson(e))
        file.writeText(arr.toString())
    }

    private fun entryToJson(e: TripEntry): JSONObject = JSONObject().apply {
        put("id", e.id)
        put("startTs", e.startTs)
        put("endTs", e.endTs)
        put("durationMs", e.durationMs)
        put("km", e.km.toDouble())
        put("maxSpeed", e.maxSpeed.toDouble())
        put("liters", e.liters.toDouble())
        put("cost", e.cost.toDouble())
        put("tag", e.tag)
        put("isManual", e.isManual)
        put("note", e.note)
    }

    private fun jsonToEntry(obj: JSONObject): TripEntry = TripEntry(
        id = obj.optString("id", UUID.randomUUID().toString()),
        startTs = obj.optLong("startTs", 0L),
        endTs = obj.optLong("endTs", 0L),
        durationMs = obj.optLong("durationMs", 0L),
        km = obj.optDouble("km", 0.0).toFloat(),
        maxSpeed = obj.optDouble("maxSpeed", 0.0).toFloat(),
        liters = obj.optDouble("liters", 0.0).toFloat(),
        cost = obj.optDouble("cost", 0.0).toFloat(),
        tag = obj.optString("tag", ""),
        isManual = obj.optBoolean("isManual", false),
        note = obj.optString("note", "")
    )
}
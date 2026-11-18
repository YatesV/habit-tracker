package com.zerorizz.habits

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.roundToInt

val ALL_DAYS: Set<Int> = (1..7).toSet()

enum class HabitType(val label: String, val emoji: String, val hint: String) {
    CHECK("Yes / No", "✅", "Did it or didn't"),
    COUNT("Count", "🔢", "e.g. 8 glasses of water"),
    TIMER("Timer", "⏱️", "e.g. read for 30 minutes"),
    NUMBER("Number", "📏", "e.g. run 5 km"),
    QUIT("Quit", "🚫", "Avoid something; log slips"),
}

data class Habit(
    val id: Long,
    val name: String,
    val emoji: String = "✅",
    val color: Int = 0,
    val type: HabitType = HabitType.CHECK,
    val target: Double = 1.0,
    val unit: String = "",
    val days: Set<Int> = ALL_DAYS,
    val tags: List<String> = emptyList(),
    val folder: String = "",
    val note: String = "",
    val archived: Boolean = false,
    val createdAt: LocalDate = LocalDate.now(),
    val entries: Map<LocalDate, Double> = emptyMap(),
    val timerStartedAt: Long? = null,
) {
    /** Earliest day that counts: creation day, or an earlier back-filled entry. */
    val startDate: LocalDate
        get() {
            val first = entries.keys.minByOrNull { it.toEpochDay() } ?: return createdAt
            return if (first.isBefore(createdAt)) first else createdAt
        }

    val effectiveTarget: Double get() = if (type == HabitType.CHECK || type == HabitType.QUIT) 1.0 else target

    fun value(d: LocalDate): Double = entries[d] ?: 0.0
    fun isScheduled(d: LocalDate): Boolean = d.dayOfWeek.value in days
    fun isDone(d: LocalDate): Boolean = when (type) {
        HabitType.QUIT -> value(d) == 0.0
        else -> value(d) >= effectiveTarget
    }

    fun progress(d: LocalDate): Float = when (type) {
        HabitType.QUIT -> if (value(d) == 0.0) 1f else 0f
        else -> (value(d) / effectiveTarget).toFloat().coerceIn(0f, 1f)
    }

    fun withValue(d: LocalDate, v: Double): Habit =
        copy(entries = if (v <= 0.0) entries - d else entries + (d to v))

    /** Tap behaviour: yes/no flips, quit flips a slip, others jump between empty and target. */
    fun toggled(d: LocalDate): Habit = when (type) {
        HabitType.QUIT -> withValue(d, if (value(d) > 0) 0.0 else 1.0)
        else -> withValue(d, if (isDone(d)) 0.0 else effectiveTarget)
    }

    fun formatValue(v: Double): String = when (type) {
        HabitType.CHECK -> if (v >= 1) "Done" else "Not done"
        HabitType.QUIT -> if (v == 0.0) "Clean" else "${v.roundToInt()} slip${if (v.roundToInt() == 1) "" else "s"}"
        HabitType.COUNT -> trimNum(v) + if (unit.isNotBlank()) " $unit" else ""
        HabitType.TIMER -> formatMinutes(v)
        HabitType.NUMBER -> trimNum(v) + if (unit.isNotBlank()) " $unit" else ""
    }

    fun progressText(d: LocalDate): String = when (type) {
        HabitType.CHECK -> if (isDone(d)) "Done" else "Not yet"
        HabitType.QUIT -> formatValue(value(d))
        HabitType.TIMER -> "${formatMinutes(value(d))} / ${formatMinutes(target)}"
        else -> "${trimNum(value(d))} / ${trimNum(target)}" + if (unit.isNotBlank()) " $unit" else ""
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("emoji", emoji)
        put("color", color)
        put("type", type.name)
        put("target", target)
        put("unit", unit)
        put("days", JSONArray(days.sorted()))
        put("tags", JSONArray(tags))
        put("folder", folder)
        put("note", note)
        put("archived", archived)
        put("createdAt", createdAt.toString())
        put("entries", JSONObject().apply { entries.forEach { (d, v) -> put(d.toString(), v) } })
        timerStartedAt?.let { put("timerStartedAt", it) }
    }

    companion object {
        fun fromJson(o: JSONObject): Habit {
            val e = o.optJSONObject("entries") ?: JSONObject()
            val entries = mutableMapOf<LocalDate, Double>()
            e.keys().forEach { k -> runCatching { entries[LocalDate.parse(k)] = e.getDouble(k) } }
            val daysArr = o.optJSONArray("days")
            val days = daysArr?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() }
            val tagsArr = o.optJSONArray("tags")
            return Habit(
                id = o.getLong("id"),
                name = o.optString("name", "Habit"),
                emoji = o.optString("emoji", "✅"),
                color = o.optInt("color", 0),
                type = runCatching { HabitType.valueOf(o.optString("type", "CHECK")) }
                    .getOrDefault(HabitType.CHECK),
                target = o.optDouble("target", 1.0).let { if (it.isNaN() || it <= 0) 1.0 else it },
                unit = o.optString("unit", ""),
                days = if (days.isNullOrEmpty()) ALL_DAYS else days,
                tags = tagsArr?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                folder = o.optString("folder", ""),
                note = o.optString("note", ""),
                archived = o.optBoolean("archived", false),
                createdAt = runCatching { LocalDate.parse(o.optString("createdAt")) }
                    .getOrDefault(LocalDate.now()),
                entries = entries,
                timerStartedAt = if (o.has("timerStartedAt")) o.getLong("timerStartedAt") else null,
            )
        }
    }
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark"), AMOLED("AMOLED"),
}

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** -1 = Material You (wallpaper) colors, otherwise an index into HabitColors. */
    val accent: Int = -1,
    val compact: Boolean = false,
    val showWeekRow: Boolean = true,
    val groupByFolder: Boolean = true,
    val showLikelihood: Boolean = true,
    val weekStartsMonday: Boolean = false,
    val userName: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("theme", theme.name)
        put("accent", accent)
        put("compact", compact)
        put("showWeekRow", showWeekRow)
        put("groupByFolder", groupByFolder)
        put("showLikelihood", showLikelihood)
        put("weekStartsMonday", weekStartsMonday)
        put("userName", userName)
    }

    companion object {
        fun fromJson(o: JSONObject?): Settings {
            if (o == null) return Settings()
            return Settings(
                theme = runCatching { ThemeMode.valueOf(o.optString("theme")) }.getOrDefault(ThemeMode.SYSTEM),
                accent = o.optInt("accent", -1),
                compact = o.optBoolean("compact", false),
                showWeekRow = o.optBoolean("showWeekRow", true),
                groupByFolder = o.optBoolean("groupByFolder", true),
                showLikelihood = o.optBoolean("showLikelihood", true),
                weekStartsMonday = o.optBoolean("weekStartsMonday", false),
                userName = o.optString("userName", ""),
            )
        }
    }
}

data class AppData(
    val habits: List<Habit> = emptyList(),
    val journal: Map<LocalDate, String> = emptyMap(),
    val settings: Settings = Settings(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 2)
        put("habits", JSONArray().apply { habits.forEach { put(it.toJson()) } })
        put("journal", JSONObject().apply { journal.forEach { (d, t) -> put(d.toString(), t) } })
        put("settings", settings.toJson())
    }

    fun habitByName(name: String): Habit? =
        habits.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    companion object {
        fun fromJson(o: JSONObject): AppData {
            val arr = o.optJSONArray("habits") ?: JSONArray()
            val habits = (0 until arr.length()).map { Habit.fromJson(arr.getJSONObject(it)) }
            val j = o.optJSONObject("journal") ?: JSONObject()
            val journal = mutableMapOf<LocalDate, String>()
            j.keys().forEach { k -> runCatching { journal[LocalDate.parse(k)] = j.getString(k) } }
            return AppData(habits, journal, Settings.fromJson(o.optJSONObject("settings")))
        }
    }
}

fun trimNum(v: Double): String =
    if (v == Math.floor(v)) v.toLong().toString() else String.format("%.1f", v)

fun formatMinutes(m: Double): String {
    val total = m.roundToInt()
    val h = total / 60
    val mm = total % 60
    return when {
        h == 0 -> "${mm}m"
        mm == 0 -> "${h}h"
        else -> "${h}h ${mm}m"
    }
}

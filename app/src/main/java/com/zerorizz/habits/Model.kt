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
    /** Photo proof: the day only counts once a photo is attached. */
    val requireProof: Boolean = false,
    val allowGallery: Boolean = false,
    /** Proof is only required from this day on, so turning it on never breaks old streaks. */
    val proofSince: LocalDate? = null,
    /** Day → photo file name inside filesDir/proofs. */
    val proofs: Map<LocalDate, String> = emptyMap(),
) {
    val startDate: LocalDate
        get() {
            val first = entries.keys.minByOrNull { it.toEpochDay() } ?: return createdAt
            return if (first.isBefore(createdAt)) first else createdAt
        }

    val effectiveTarget: Double get() = if (type == HabitType.CHECK || type == HabitType.QUIT) 1.0 else target
    val supportsProof: Boolean get() = type != HabitType.QUIT

    fun value(d: LocalDate): Double = entries[d] ?: 0.0
    fun isScheduled(d: LocalDate): Boolean = d.dayOfWeek.value in days

    fun needsProof(d: LocalDate): Boolean =
        requireProof && supportsProof && (proofSince == null || !d.isBefore(proofSince))

    fun hasProof(d: LocalDate): Boolean = proofs.containsKey(d)

    /** Goal reached, ignoring proof. */
    fun goalMet(d: LocalDate): Boolean = when (type) {
        HabitType.QUIT -> value(d) == 0.0
        else -> value(d) >= effectiveTarget
    }

    /** Waiting on a photo before it counts. */
    fun awaitingProof(d: LocalDate): Boolean = goalMet(d) && needsProof(d) && !hasProof(d)

    fun isDone(d: LocalDate): Boolean = goalMet(d) && (!needsProof(d) || hasProof(d))

    fun progress(d: LocalDate): Float {
        val p = when (type) {
            HabitType.QUIT -> if (value(d) == 0.0) 1f else 0f
            else -> (value(d) / effectiveTarget).toFloat().coerceIn(0f, 1f)
        }
        return if (awaitingProof(d)) minOf(p, 0.9f) else p
    }

    fun withValue(d: LocalDate, v: Double): Habit =
        copy(entries = if (v <= 0.0) entries - d else entries + (d to v))

    fun toggled(d: LocalDate): Habit = when (type) {
        HabitType.QUIT -> withValue(d, if (value(d) > 0) 0.0 else 1.0)
        else -> withValue(d, if (goalMet(d)) 0.0 else effectiveTarget)
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
        put("requireProof", requireProof)
        put("allowGallery", allowGallery)
        proofSince?.let { put("proofSince", it.toString()) }
        put("proofs", JSONObject().apply { proofs.forEach { (d, f) -> put(d.toString(), f) } })
    }

    /** Shareable version with no history, for habit packs. */
    fun toTemplateJson(): JSONObject = JSONObject().apply {
        put("name", name); put("emoji", emoji); put("color", color); put("type", type.name)
        put("target", target); put("unit", unit); put("days", JSONArray(days.sorted()))
        put("tags", JSONArray(tags)); put("folder", folder); put("note", note)
        put("requireProof", requireProof); put("allowGallery", allowGallery)
    }

    companion object {
        fun fromJson(o: JSONObject, newId: Long? = null): Habit {
            val e = o.optJSONObject("entries") ?: JSONObject()
            val entries = mutableMapOf<LocalDate, Double>()
            e.keys().forEach { k -> runCatching { entries[LocalDate.parse(k)] = e.getDouble(k) } }
            val p = o.optJSONObject("proofs") ?: JSONObject()
            val proofs = mutableMapOf<LocalDate, String>()
            p.keys().forEach { k -> runCatching { proofs[LocalDate.parse(k)] = p.getString(k) } }
            val daysArr = o.optJSONArray("days")
            val days = daysArr?.let { a -> (0 until a.length()).map { a.getInt(it) }.filter { it in 1..7 }.toSet() }
            val tagsArr = o.optJSONArray("tags")
            return Habit(
                id = newId ?: o.optLong("id", System.nanoTime()),
                name = o.optString("name", "Habit").take(60),
                emoji = o.optString("emoji", "✅").take(8),
                color = o.optInt("color", 0),
                type = runCatching { HabitType.valueOf(o.optString("type", "CHECK")) }.getOrDefault(HabitType.CHECK),
                target = o.optDouble("target", 1.0).let { if (it.isNaN() || it <= 0) 1.0 else it },
                unit = o.optString("unit", "").take(20),
                days = if (days.isNullOrEmpty()) ALL_DAYS else days,
                tags = tagsArr?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                folder = o.optString("folder", ""),
                note = o.optString("note", ""),
                archived = o.optBoolean("archived", false),
                createdAt = runCatching { LocalDate.parse(o.optString("createdAt")) }.getOrDefault(LocalDate.now()),
                entries = entries,
                timerStartedAt = if (o.has("timerStartedAt")) o.getLong("timerStartedAt") else null,
                requireProof = o.optBoolean("requireProof", false),
                allowGallery = o.optBoolean("allowGallery", false),
                proofSince = runCatching { LocalDate.parse(o.getString("proofSince")) }.getOrNull(),
                proofs = proofs,
            )
        }
    }
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark"), AMOLED("AMOLED"),
}

enum class CardStyle(val label: String) {
    FILLED("Filled"), OUTLINED("Outlined"), GRADIENT("Gradient"), MINIMAL("Minimal"),
}

data class ThemePreset(
    val id: String,
    val name: String,
    val emoji: String,
    val seed: Int,
    val mode: ThemeMode? = null,
    val pro: Boolean = false,
    val packId: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("emoji", emoji)
        put("seed", String.format("#%06X", seed and 0xFFFFFF))
        mode?.let { put("mode", it.name) }
        packId?.let { put("packId", it) }
    }

    companion object {
        fun fromJson(o: JSONObject, packId: String? = null): ThemePreset? = runCatching {
            val seedStr = o.getString("seed").removePrefix("#")
            ThemePreset(
                id = o.optString("id", "t" + System.nanoTime()),
                name = o.optString("name", "Theme").take(30),
                emoji = o.optString("emoji", "🎨").take(8),
                seed = (0xFF000000 or seedStr.toLong(16)).toInt(),
                mode = runCatching { ThemeMode.valueOf(o.getString("mode")) }.getOrNull(),
                packId = packId ?: o.optString("packId").ifBlank { null },
            )
        }.getOrNull()
    }
}

/** A shareable bundle of habit templates and themes: Habitify's plugin format. */
data class HabitPack(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val author: String,
    val habits: List<Habit>,
    val themes: List<ThemePreset> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("format", "habitify-pack")
        put("formatVersion", 1)
        put("id", id); put("name", name); put("emoji", emoji)
        put("description", description); put("author", author)
        put("habits", JSONArray().apply { habits.forEach { put(it.toTemplateJson()) } })
        put("themes", JSONArray().apply { themes.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): HabitPack? = runCatching {
            if (o.optString("format") != "habitify-pack") return null
            val id = o.optString("id").ifBlank { "pack" + System.nanoTime() }
            val ha = o.optJSONArray("habits") ?: JSONArray()
            val ta = o.optJSONArray("themes") ?: JSONArray()
            HabitPack(
                id = id,
                name = o.optString("name", "Pack").take(40),
                emoji = o.optString("emoji", "📦").take(8),
                description = o.optString("description", "").take(300),
                author = o.optString("author", "").take(40),
                habits = (0 until minOf(ha.length(), 50)).map { Habit.fromJson(ha.getJSONObject(it), newId = 0) },
                themes = (0 until minOf(ta.length(), 20)).mapNotNull { ThemePreset.fromJson(ta.getJSONObject(it), id) },
            )
        }.getOrNull()
    }
}

data class InstalledPack(val id: String, val name: String, val emoji: String, val author: String) {
    fun toJson(): JSONObject = JSONObject().apply { put("id", id); put("name", name); put("emoji", emoji); put("author", author) }
    companion object {
        fun fromJson(o: JSONObject) = InstalledPack(o.getString("id"), o.optString("name"), o.optString("emoji", "📦"), o.optString("author"))
    }
}

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** -1 = Material You (wallpaper) colors, otherwise an index into HabitColors. */
    val accent: Int = -1,
    /** Custom ARGB accent; overrides [accent] when set. */
    val customAccent: Int? = null,
    val themePreset: String? = null,
    val cardStyle: CardStyle = CardStyle.FILLED,
    val corner: Int = 20,
    val textScale: Float = 1f,
    val compact: Boolean = false,
    val showWeekRow: Boolean = true,
    val groupByFolder: Boolean = true,
    val showLikelihood: Boolean = true,
    val weekStartsMonday: Boolean = false,
    val userName: String = "",
    val onboarded: Boolean = false,
    val goals: Set<String> = emptySet(),
    val addons: Set<String> = setOf(Addons.CELEBRATIONS, Addons.MOTIVATION),
    val pro: Boolean = false,
    val devUnlocked: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("theme", theme.name)
        put("accent", accent)
        customAccent?.let { put("customAccent", it) }
        themePreset?.let { put("themePreset", it) }
        put("cardStyle", cardStyle.name)
        put("corner", corner)
        put("textScale", textScale.toDouble())
        put("compact", compact)
        put("showWeekRow", showWeekRow)
        put("groupByFolder", groupByFolder)
        put("showLikelihood", showLikelihood)
        put("weekStartsMonday", weekStartsMonday)
        put("userName", userName)
        put("onboarded", onboarded)
        put("goals", JSONArray(goals.toList()))
        put("addons", JSONArray(addons.toList()))
        put("pro", pro)
        put("devUnlocked", devUnlocked)
    }

    companion object {
        private fun strSet(a: JSONArray?): Set<String>? = a?.let { (0 until it.length()).map { i -> it.getString(i) }.toSet() }

        fun fromJson(o: JSONObject?): Settings {
            if (o == null) return Settings()
            return Settings(
                theme = runCatching { ThemeMode.valueOf(o.optString("theme")) }.getOrDefault(ThemeMode.SYSTEM),
                accent = o.optInt("accent", -1),
                customAccent = if (o.has("customAccent")) o.getInt("customAccent") else null,
                themePreset = o.optString("themePreset").ifBlank { null },
                cardStyle = runCatching { CardStyle.valueOf(o.optString("cardStyle")) }.getOrDefault(CardStyle.FILLED),
                corner = o.optInt("corner", 20).coerceIn(0, 32),
                textScale = o.optDouble("textScale", 1.0).toFloat().coerceIn(0.8f, 1.3f),
                compact = o.optBoolean("compact", false),
                showWeekRow = o.optBoolean("showWeekRow", true),
                groupByFolder = o.optBoolean("groupByFolder", true),
                showLikelihood = o.optBoolean("showLikelihood", true),
                weekStartsMonday = o.optBoolean("weekStartsMonday", false),
                userName = o.optString("userName", ""),
                onboarded = o.optBoolean("onboarded", false),
                goals = strSet(o.optJSONArray("goals")) ?: emptySet(),
                addons = strSet(o.optJSONArray("addons")) ?: setOf(Addons.CELEBRATIONS, Addons.MOTIVATION),
                pro = o.optBoolean("pro", false),
                devUnlocked = o.optBoolean("devUnlocked", false),
            )
        }
    }
}

data class AppData(
    val habits: List<Habit> = emptyList(),
    val journal: Map<LocalDate, String> = emptyMap(),
    val settings: Settings = Settings(),
    /** Day → mood 1 (awful) … 5 (great). */
    val moods: Map<LocalDate, Int> = emptyMap(),
    val customThemes: List<ThemePreset> = emptyList(),
    val packs: List<InstalledPack> = emptyList(),
) {
    val isPro: Boolean get() = settings.pro || settings.devUnlocked
    val activeHabits: List<Habit> get() = habits.filter { !it.archived }
    fun addon(id: String): Boolean = id in settings.addons && (!Addons.byId(id).pro || isPro)

    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 3)
        put("habits", JSONArray().apply { habits.forEach { put(it.toJson()) } })
        put("journal", JSONObject().apply { journal.forEach { (d, t) -> put(d.toString(), t) } })
        put("settings", settings.toJson())
        put("moods", JSONObject().apply { moods.forEach { (d, m) -> put(d.toString(), m) } })
        put("customThemes", JSONArray().apply { customThemes.forEach { put(it.toJson()) } })
        put("packs", JSONArray().apply { packs.forEach { put(it.toJson()) } })
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
            val m = o.optJSONObject("moods") ?: JSONObject()
            val moods = mutableMapOf<LocalDate, Int>()
            m.keys().forEach { k -> runCatching { moods[LocalDate.parse(k)] = m.getInt(k).coerceIn(1, 5) } }
            val sj = o.optJSONObject("settings")
            var settings = Settings.fromJson(sj)
            // People upgrading from older versions already set up the app.
            if ((sj == null || !sj.has("onboarded")) && habits.isNotEmpty()) settings = settings.copy(onboarded = true)
            val ct = o.optJSONArray("customThemes") ?: JSONArray()
            val pk = o.optJSONArray("packs") ?: JSONArray()
            return AppData(
                habits = habits,
                journal = journal,
                settings = settings,
                moods = moods,
                customThemes = (0 until ct.length()).mapNotNull { ThemePreset.fromJson(ct.getJSONObject(it)) },
                packs = (0 until pk.length()).mapNotNull { runCatching { InstalledPack.fromJson(pk.getJSONObject(it)) }.getOrNull() },
            )
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

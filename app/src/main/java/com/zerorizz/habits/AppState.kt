package com.zerorizz.habits

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

object Storage {
    private const val FILE = "habitify.json"

    fun load(ctx: Context): AppData {
        val f = File(ctx.filesDir, FILE)
        if (f.exists()) {
            return try {
                AppData.fromJson(JSONObject(f.readText()))
            } catch (e: Exception) {
                f.copyTo(File(ctx.filesDir, "habitify-corrupt-${System.currentTimeMillis()}.json"), true)
                AppData()
            }
        }
        return migrateV1(ctx)
    }

    fun save(ctx: Context, data: AppData) {
        val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.writeText(data.toJson().toString())
        tmp.renameTo(File(ctx.filesDir, FILE))
    }

    private fun migrateV1(ctx: Context): AppData {
        val raw = ctx.getSharedPreferences("habits", Context.MODE_PRIVATE)
            .getString("habits_v1", null) ?: return AppData()
        return try {
            val arr = JSONArray(raw)
            val habits = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val dates = o.getJSONArray("done")
                val done = (0 until dates.length()).map { LocalDate.parse(dates.getString(it)) }
                Habit(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    color = i % HabitColors.size,
                    createdAt = done.minByOrNull { it.toEpochDay() } ?: LocalDate.now(),
                    entries = done.associateWith { 1.0 },
                )
            }
            AppData(habits = habits, settings = Settings(onboarded = habits.isNotEmpty())).also { save(ctx, it) }
        } catch (e: Exception) {
            AppData()
        }
    }
}

class AppState(private val ctx: Context) {
    var data by mutableStateOf(Storage.load(ctx))
        private set

    /** Bumped when a habit gets completed; drives confetti + haptics. */
    var celebrateTick by mutableIntStateOf(0)
        private set
    var celebrateBig by mutableStateOf(false)
        private set

    val proofDir: File get() = File(ctx.filesDir, "proofs").apply { mkdirs() }

    fun update(f: (AppData) -> AppData) {
        data = f(data)
        Storage.save(ctx, data)
    }

    /** Applies a change to one habit and fires a celebration if it just got completed. */
    private fun updateHabit(id: Long, date: LocalDate? = null, f: (Habit) -> Habit) {
        val before = habit(id)
        val today = LocalDate.now()
        val d = date ?: today
        val allBefore = data.activeHabits.filter { it.isScheduled(today) }.all { it.isDone(today) }
        update { dd -> dd.copy(habits = dd.habits.map { if (it.id == id) f(it) else it }) }
        val after = habit(id)
        if (before != null && after != null && !before.isDone(d) && after.isDone(d) && after.type != HabitType.QUIT) {
            val sched = data.activeHabits.filter { it.isScheduled(today) }
            celebrateBig = d == today && !allBefore && sched.isNotEmpty() && sched.all { it.isDone(today) }
            celebrateTick++
        }
    }

    fun habit(id: Long): Habit? = data.habits.firstOrNull { it.id == id }

    fun upsert(h: Habit) = update { d ->
        if (d.habits.any { it.id == h.id }) d.copy(habits = d.habits.map { if (it.id == h.id) h else it })
        else d.copy(habits = d.habits + h)
    }

    fun addHabits(list: List<Habit>) = update { d ->
        var id = System.currentTimeMillis()
        val used = d.habits.map { it.color }
        d.copy(habits = d.habits + list.mapIndexed { i, h ->
            h.copy(
                id = id++,
                createdAt = LocalDate.now(),
                color = if (h.color in HabitColors.indices) h.color else (used.size + i) % HabitColors.size,
                proofSince = if (h.requireProof) LocalDate.now() else null,
                entries = emptyMap(), proofs = emptyMap(),
            )
        })
    }

    fun delete(id: Long) {
        habit(id)?.proofs?.values?.forEach { File(proofDir, it).delete() }
        update { d -> d.copy(habits = d.habits.filterNot { it.id == id }) }
    }

    fun setArchived(id: Long, archived: Boolean) = updateHabit(id) { it.copy(archived = archived) }
    fun setValue(id: Long, date: LocalDate, v: Double) = updateHabit(id, date) { it.withValue(date, v) }

    fun toggle(id: Long, date: LocalDate) = updateHabit(id, date) { h ->
        val t = h.toggled(date)
        // Un-checking also removes that day's proof photo
        if (!t.goalMet(date) && h.hasProof(date)) {
            File(proofDir, h.proofs.getValue(date)).delete()
            t.copy(proofs = t.proofs - date)
        } else t
    }

    fun increment(id: Long, date: LocalDate, delta: Double) =
        updateHabit(id, date) { it.withValue(date, (it.value(date) + delta).coerceAtLeast(0.0)) }

    fun startTimer(id: Long) = updateHabit(id) { it.copy(timerStartedAt = System.currentTimeMillis()) }

    fun stopTimer(id: Long) = updateHabit(id) { h ->
        val started = h.timerStartedAt ?: return@updateHabit h
        val minutes = (System.currentTimeMillis() - started) / 60000.0
        val today = LocalDate.now()
        h.copy(timerStartedAt = null)
            .withValue(today, Math.round((h.value(today) + minutes) * 10) / 10.0)
    }

    /** Attaches a proof photo. For yes/no habits this also marks the day done. */
    fun attachProof(id: Long, date: LocalDate, fileName: String) = updateHabit(id, date) { h ->
        h.proofs[date]?.let { old -> if (old != fileName) File(proofDir, old).delete() }
        val withProof = h.copy(proofs = h.proofs + (date to fileName))
        if (h.type == HabitType.CHECK && !h.goalMet(date)) withProof.withValue(date, 1.0) else withProof
    }

    fun removeProof(id: Long, date: LocalDate) = updateHabit(id, date) { h ->
        h.proofs[date]?.let { File(proofDir, it).delete() }
        h.copy(proofs = h.proofs - date)
    }

    fun proofFile(name: String): File = File(proofDir, name)

    fun move(id: Long, delta: Int) = update { d ->
        val list = d.habits.toMutableList()
        val active = list.filter { !it.archived }
        val i = active.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in active.indices) return@update d
        val a = list.indexOf(active[i])
        val b = list.indexOf(active[j])
        val tmp = list[a]; list[a] = list[b]; list[b] = tmp
        d.copy(habits = list)
    }

    fun setJournal(date: LocalDate, text: String) = update { d ->
        d.copy(journal = if (text.isBlank()) d.journal - date else d.journal + (date to text))
    }

    fun setMood(date: LocalDate, mood: Int?) = update { d ->
        d.copy(moods = if (mood == null) d.moods - date else d.moods + (date to mood.coerceIn(1, 5)))
    }

    fun updateSettings(f: (Settings) -> Settings) = update { it.copy(settings = f(it.settings)) }

    fun setPro(pro: Boolean) {
        if (data.settings.pro != pro) updateSettings { it.copy(pro = pro) }
    }

    fun toggleAddon(id: String, on: Boolean) = updateSettings {
        it.copy(addons = if (on) it.addons + id else it.addons - id)
    }

    fun applyTheme(p: ThemePreset) = updateSettings {
        it.copy(
            themePreset = p.id,
            customAccent = if (p.id == "default") null else p.seed,
            accent = -1,
            theme = p.mode ?: it.theme,
        )
    }

    fun installPack(pack: HabitPack, habits: List<Habit>) = update { d ->
        val withThemes = d.copy(
            customThemes = d.customThemes.filterNot { it.packId == pack.id } + pack.themes.map { it.copy(packId = pack.id) },
            packs = if (pack.themes.isNotEmpty() || BUILT_IN_PACKS.none { it.id == pack.id })
                d.packs.filterNot { it.id == pack.id } + InstalledPack(pack.id, pack.name, pack.emoji, pack.author)
            else d.packs,
        )
        withThemes
    }.also { if (habits.isNotEmpty()) addHabits(habits) }

    fun uninstallPack(id: String) = update { d ->
        d.copy(
            packs = d.packs.filterNot { it.id == id },
            customThemes = d.customThemes.filterNot { it.packId == id },
        )
    }

    fun exportTo(uri: Uri): Boolean = writeTo(uri, data.toJson().toString(2))

    fun writeTo(uri: Uri, text: String): Boolean = runCatching {
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
    }.isSuccess

    fun readText(uri: Uri): String? = runCatching {
        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
    }.getOrNull()

    fun readBackup(uri: Uri): AppData? = runCatching { AppData.fromJson(JSONObject(readText(uri)!!)) }.getOrNull()

    fun readPack(uri: Uri): HabitPack? = runCatching { HabitPack.fromJson(JSONObject(readText(uri)!!)) }.getOrNull()

    fun replaceAll(newData: AppData) = update { newData.copy(settings = newData.settings.copy(onboarded = true)) }
}

package com.zerorizz.habits

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
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
                // Keep the broken file so nothing is lost, then start fresh
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

    /** Pulls habits from the first version of the app. */
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
            AppData(habits = habits).also { save(ctx, it) }
        } catch (e: Exception) {
            AppData()
        }
    }
}

class AppState(private val ctx: Context) {
    var data by mutableStateOf(Storage.load(ctx))
        private set

    fun update(f: (AppData) -> AppData) {
        data = f(data)
        Storage.save(ctx, data)
    }

    private fun updateHabit(id: Long, f: (Habit) -> Habit) =
        update { d -> d.copy(habits = d.habits.map { if (it.id == id) f(it) else it }) }

    fun habit(id: Long): Habit? = data.habits.firstOrNull { it.id == id }

    fun upsert(h: Habit) = update { d ->
        if (d.habits.any { it.id == h.id }) d.copy(habits = d.habits.map { if (it.id == h.id) h else it })
        else d.copy(habits = d.habits + h)
    }

    fun delete(id: Long) = update { d -> d.copy(habits = d.habits.filterNot { it.id == id }) }
    fun setArchived(id: Long, archived: Boolean) = updateHabit(id) { it.copy(archived = archived) }
    fun setValue(id: Long, date: LocalDate, v: Double) = updateHabit(id) { it.withValue(date, v) }
    fun toggle(id: Long, date: LocalDate) = updateHabit(id) { it.toggled(date) }
    fun increment(id: Long, date: LocalDate, delta: Double) =
        updateHabit(id) { it.withValue(date, (it.value(date) + delta).coerceAtLeast(0.0)) }

    fun startTimer(id: Long) = updateHabit(id) { it.copy(timerStartedAt = System.currentTimeMillis()) }

    fun stopTimer(id: Long) = updateHabit(id) { h ->
        val started = h.timerStartedAt ?: return@updateHabit h
        val minutes = (System.currentTimeMillis() - started) / 60000.0
        val today = LocalDate.now()
        h.copy(timerStartedAt = null)
            .withValue(today, Math.round((h.value(today) + minutes) * 10) / 10.0)
    }

    /** Moves a habit up/down among the active (non-archived) habits. */
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

    fun updateSettings(f: (Settings) -> Settings) = update { it.copy(settings = f(it.settings)) }

    fun exportTo(uri: Uri): Boolean = runCatching {
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(data.toJson().toString(2).toByteArray()) }
    }.isSuccess

    fun readBackup(uri: Uri): AppData? = runCatching {
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        AppData.fromJson(JSONObject(text!!))
    }.getOrNull()

    fun replaceAll(newData: AppData) = update { newData }
}

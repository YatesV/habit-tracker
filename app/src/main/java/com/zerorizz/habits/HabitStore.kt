package com.zerorizz.habits

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class Habit(
    val id: Long,
    val name: String,
    val doneDates: Set<LocalDate> = emptySet(),
) {
    fun isDone(date: LocalDate) = date in doneDates

    /** Consecutive days ending today (or yesterday, if today isn't checked yet). */
    fun currentStreak(today: LocalDate = LocalDate.now()): Int {
        var day = if (isDone(today)) today else today.minusDays(1)
        var count = 0
        while (isDone(day)) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    fun bestStreak(): Int {
        if (doneDates.isEmpty()) return 0
        val sorted = doneDates.sorted()
        var best = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (sorted[i - 1].plusDays(1) == sorted[i]) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    fun toggled(date: LocalDate): Habit =
        copy(doneDates = if (isDone(date)) doneDates - date else doneDates + date)
}

/** Saves habits on the phone as JSON. No internet, no account. */
class HabitStore(context: Context) {
    private val prefs = context.getSharedPreferences("habits", Context.MODE_PRIVATE)

    fun load(): List<Habit> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val dates = o.getJSONArray("done")
                Habit(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    doneDates = (0 until dates.length())
                        .map { LocalDate.parse(dates.getString(it)) }
                        .toSet(),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(habits: List<Habit>) {
        val arr = JSONArray()
        habits.forEach { h ->
            arr.put(
                JSONObject()
                    .put("id", h.id)
                    .put("name", h.name)
                    .put("done", JSONArray(h.doneDates.map { it.toString() }))
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "habits_v1"
    }
}

package com.zerorizz.habits

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.min
import kotlin.math.roundToInt

data class Rank(val minLevel: Int, val name: String, val emoji: String)

val RANKS = listOf(
    Rank(1, "Rookie", "🌱"),
    Rank(3, "Apprentice", "🪵"),
    Rank(5, "Committed", "🔥"),
    Rank(8, "Disciplined", "⚔️"),
    Rank(12, "Relentless", "🛡️"),
    Rank(17, "Iron Will", "🦾"),
    Rank(23, "Unbreakable", "💎"),
    Rank(30, "Legend", "👑"),
)

data class Attribute(val name: String, val emoji: String, val value: Float, val desc: String)
data class Badge(val emoji: String, val name: String, val desc: String, val earned: Boolean)

data class Profile(
    val xp: Int,
    val level: Int,
    val levelStartXp: Int,
    val nextLevelXp: Int,
    val rank: Rank,
    val nextRank: Rank?,
    val willpower: Int,
    val attributes: List<Attribute>,
    val followThrough: Float?,
    val badges: List<Badge>,
    val perfectDays: Int,
    val totalCompletions: Int,
    val bestStreak: Int,
)

data class Ranked(val habit: Habit, val rate30: Float?, val streak: Int)

data class Overview(
    val rate7: Float?,
    val rate30: Float?,
    val rateAll: Float?,
    val totalCompletions: Int,
    val perfectDays: Int,
    val bestStreak: Int,
    val heatmap: Map<LocalDate, Float>,
    val weekday: List<Float?>,
    val trendWeeks: List<Float?>,
    val trendMonths: List<Float?>,
    val ranking: List<Ranked>,
    val tagRates: List<Pair<String, Float?>>,
)

object Stats {

    private fun later(a: LocalDate, b: LocalDate) = if (a.isAfter(b)) a else b
    private fun earlier(a: LocalDate, b: LocalDate) = if (a.isBefore(b)) a else b

    /**
     * Scheduled days in [from, to] with whether they succeeded.
     * Today only counts once it's decided: done (normal habits) or slipped (quit habits).
     */
    fun outcomes(h: Habit, from: LocalDate, to: LocalDate, today: LocalDate): List<Pair<LocalDate, Boolean>> {
        val out = ArrayList<Pair<LocalDate, Boolean>>()
        var d = later(from, h.startDate)
        val end = earlier(to, today)
        while (!d.isAfter(end)) {
            if (h.isScheduled(d)) {
                val ok = h.isDone(d)
                val decided = d != today || (if (h.type == HabitType.QUIT) !ok else ok)
                if (decided) out.add(d to ok)
            }
            d = d.plusDays(1)
        }
        return out
    }

    fun rate(h: Habit, from: LocalDate, to: LocalDate, today: LocalDate): Float? {
        val o = outcomes(h, from, to, today)
        return if (o.isEmpty()) null else o.count { it.second }.toFloat() / o.size
    }

    fun combinedRate(habits: List<Habit>, from: LocalDate, to: LocalDate, today: LocalDate): Float? {
        var ok = 0
        var total = 0
        habits.forEach { h -> outcomes(h, from, to, today).forEach { total++; if (it.second) ok++ } }
        return if (total == 0) null else ok.toFloat() / total
    }

    fun currentStreak(h: Habit, today: LocalDate): Int {
        var n = 0
        if (h.isScheduled(today)) {
            if (h.isDone(today)) n++ else if (h.type == HabitType.QUIT) return 0
        }
        var d = today.minusDays(1)
        val start = h.startDate
        while (!d.isBefore(start)) {
            if (h.isScheduled(d)) {
                if (h.isDone(d)) n++ else break
            }
            d = d.minusDays(1)
        }
        return n
    }

    fun bestStreak(h: Habit, today: LocalDate): Int {
        var best = 0
        var run = 0
        var d = h.startDate
        while (!d.isAfter(today)) {
            if (h.isScheduled(d)) {
                if (h.isDone(d)) {
                    run++
                    if (run > best) best = run
                } else if (d != today || h.type == HabitType.QUIT) {
                    run = 0
                }
            }
            d = d.plusDays(1)
        }
        return best
    }

    fun totalCompletions(h: Habit, today: LocalDate): Int =
        outcomes(h, h.startDate, today, today).count { it.second } +
            if (h.isScheduled(today) && h.isDone(today) && h.type == HabitType.QUIT) 1 else 0

    fun totalValue(h: Habit): Double = h.entries.values.sum()

    /** Chance (0–1) of completing this habit today, from recent history. Null if too little data. */
    fun likelihood(h: Habit, today: LocalDate): Float? {
        val past = outcomes(h, today.minusDays(120), today.minusDays(1), today).asReversed()
        if (past.size < 3) return null
        var w = 1.0
        var num = 0.0
        var den = 0.0
        for ((_, ok) in past.take(40)) {
            if (ok) num += w
            den += w
            w *= 0.9
        }
        val ewma = num / den
        val same = past.filter { it.first.dayOfWeek == today.dayOfWeek }.take(8)
        val sameRate = if (same.size >= 2) same.count { it.second }.toDouble() / same.size else ewma
        val streakBoost = min(1.0, currentStreak(h, today) / 14.0)
        return (0.65 * ewma + 0.25 * sameRate + 0.10 * streakBoost).coerceIn(0.02, 0.99).toFloat()
    }

    fun heatmap(h: Habit, today: LocalDate, days: Long = 371): Map<LocalDate, Float> {
        val map = HashMap<LocalDate, Float>()
        var d = later(today.minusDays(days), h.startDate)
        while (!d.isAfter(today)) {
            map[d] = if (!h.isScheduled(d) && h.value(d) == 0.0) -1f else h.progress(d)
            d = d.plusDays(1)
        }
        return map
    }

    fun overallHeatmap(habits: List<Habit>, today: LocalDate, days: Long = 371): Map<LocalDate, Float> {
        val map = HashMap<LocalDate, Float>()
        var d = today.minusDays(days)
        while (!d.isAfter(today)) {
            val sched = habits.filter { it.isScheduled(d) && !d.isBefore(it.startDate) }
            if (sched.isNotEmpty()) map[d] = sched.sumOf { it.progress(d).toDouble() }.toFloat() / sched.size
            d = d.plusDays(1)
        }
        return map
    }

    /** Success rate per weekday, Monday first. */
    fun weekdayRates(habits: List<Habit>, today: LocalDate, lookback: Long = 182): List<Float?> {
        val ok = IntArray(7)
        val total = IntArray(7)
        habits.forEach { h ->
            outcomes(h, today.minusDays(lookback), today, today).forEach { (d, s) ->
                val i = d.dayOfWeek.value - 1
                total[i]++
                if (s) ok[i]++
            }
        }
        return (0 until 7).map { if (total[it] == 0) null else ok[it].toFloat() / total[it] }
    }

    /** Oldest → newest buckets of [bucketDays] each, ending today. */
    fun trend(habits: List<Habit>, today: LocalDate, buckets: Int, bucketDays: Long): List<Float?> =
        (buckets - 1 downTo 0).map { b ->
            val end = today.minusDays(b * bucketDays)
            combinedRate(habits, end.minusDays(bucketDays - 1), end, today)
        }

    /** Of the misses in the last 90 days, how often the very next scheduled day was a success. */
    fun resilience(habits: List<Habit>, today: LocalDate): Float? {
        var misses = 0
        var recovered = 0
        var anyData = false
        habits.forEach { h ->
            val o = outcomes(h, today.minusDays(90), today, today)
            if (o.isNotEmpty()) anyData = true
            for (i in 0 until o.size - 1) {
                if (!o[i].second) {
                    misses++
                    if (o[i + 1].second) recovered++
                }
            }
        }
        return when {
            misses > 0 -> recovered.toFloat() / misses
            anyData -> 1f
            else -> null
        }
    }

    fun perfectDays(habits: List<Habit>, today: LocalDate): List<LocalDate> {
        if (habits.isEmpty()) return emptyList()
        val start = habits.minOf { it.startDate.toEpochDay() }
        val out = ArrayList<LocalDate>()
        for (e in start..today.toEpochDay()) {
            val d = LocalDate.ofEpochDay(e)
            val sched = habits.filter { it.isScheduled(d) && !d.isBefore(it.startDate) }
            if (sched.isNotEmpty() && sched.all { it.isDone(d) }) out.add(d)
        }
        return out
    }

    fun xpForLevel(level: Int): Int = 50 * (level - 1) * level

    fun xp(habits: List<Habit>, today: LocalDate): Int {
        var xp = 0
        habits.forEach { h ->
            var run = 0
            var d = h.startDate
            while (!d.isAfter(today)) {
                if (h.isScheduled(d)) {
                    if (h.isDone(d)) {
                        run++
                        xp += if (h.type == HabitType.QUIT) 5 + min(run - 1, 10) else 10 + min(run - 1, 20)
                    } else if (d != today || h.type == HabitType.QUIT) {
                        run = 0
                    }
                }
                d = d.plusDays(1)
            }
        }
        return xp
    }

    fun willpowerLabel(w: Int): String = when {
        w >= 90 -> "Unstoppable"
        w >= 75 -> "Iron-willed"
        w >= 60 -> "Strong"
        w >= 40 -> "Steady"
        w >= 20 -> "Building"
        else -> "Just starting"
    }

    fun profile(data: AppData, today: LocalDate): Profile {
        val all = data.habits
        val active = all.filter { !it.archived }

        val xp = xp(all, today)
        var level = 1
        while (xpForLevel(level + 1) <= xp) level++
        val rank = RANKS.last { level >= it.minLevel }
        val nextRank = RANKS.firstOrNull { it.minLevel > level }

        val r14 = combinedRate(active, today.minusDays(13), today, today)
        val rPrev14 = combinedRate(active, today.minusDays(27), today.minusDays(14), today)
        val r30 = combinedRate(active, today.minusDays(29), today, today)
        val res = resilience(active, today)
        val best = all.maxOfOrNull { bestStreak(it, today) } ?: 0
        val momentum = if (r14 != null && rPrev14 != null) (0.5f + (r14 - rPrev14) / 2f).coerceIn(0f, 1f)
        else if (r14 != null) 0.5f else 0f

        val attributes = listOf(
            Attribute("Discipline", "⚔️", r14 ?: 0f, "Success rate over the last 14 days"),
            Attribute("Consistency", "📅", r30 ?: 0f, "Success rate over the last 30 days"),
            Attribute("Resilience", "🛡️", res ?: 0f, "How often you bounce back right after a miss"),
            Attribute("Endurance", "🏔️", min(1f, best / 30f), "Best streak, out of 30 days"),
            Attribute("Momentum", "🚀", momentum, "Last 2 weeks compared to the 2 before"),
            Attribute("Ambition", "🎯", min(1f, active.size / 6f), "How many habits you're running (6 = max)"),
        )

        val hasData = r30 != null
        val willpower = if (!hasData) 0 else (100 * (
            0.30f * (r14 ?: 0f) + 0.25f * (r30 ?: 0f) + 0.20f * (res ?: 0f) +
                0.15f * min(1f, best / 30f) + 0.10f * momentum
            )).roundToInt().coerceIn(0, 100)

        val todays = active.filter { it.isScheduled(today) }
        val followThrough = if (todays.isEmpty()) null else
            todays.map { if (it.isDone(today) && it.type != HabitType.QUIT) 1f else likelihood(it, today) ?: 0.5f }
                .average().toFloat()

        val perfect = perfectDays(active, today)
        val perfectSet = perfect.toHashSet()
        var perfectRun = 0
        var bestPerfectRun = 0
        perfect.forEach { d ->
            perfectRun = if (perfectSet.contains(d.minusDays(1))) perfectRun + 1 else 1
            if (perfectRun > bestPerfectRun) bestPerfectRun = perfectRun
        }
        val completions = all.sumOf { totalCompletions(it, today) }
        val timerHours = all.filter { it.type == HabitType.TIMER }.sumOf { totalValue(it) } / 60.0
        val comeback = res != null && all.any { h ->
            val o = outcomes(h, h.startDate, today, today)
            (0 until o.size - 1).any { !o[it].second && o[it + 1].second }
        }

        val badges = listOf(
            Badge("👣", "First Step", "Complete a habit once", completions >= 1),
            Badge("🎲", "On a Roll", "3-day streak", best >= 3),
            Badge("🗓️", "Week Warrior", "7-day streak", best >= 7),
            Badge("🌓", "Fortnight", "14-day streak", best >= 14),
            Badge("🌕", "Monthly Master", "30-day streak", best >= 30),
            Badge("🏛️", "Centurion", "100-day streak", best >= 100),
            Badge("💯", "Century", "100 total completions", completions >= 100),
            Badge("⭐", "Perfect Day", "Finish every habit in a day", perfect.isNotEmpty()),
            Badge("🌟", "Perfect Week", "7 perfect days in a row", bestPerfectRun >= 7),
            Badge("🔁", "Comeback Kid", "Bounce back the day after a miss", comeback),
            Badge("🧺", "Collector", "Run 5 habits at once", active.size >= 5),
            Badge("📓", "Journaler", "Write 7 journal entries", data.journal.size >= 7),
            Badge("⏳", "Timekeeper", "Log 10 hours on timer habits", timerHours >= 10),
            Badge("🦾", "Iron Will", "Reach 80 willpower", willpower >= 80),
            Badge("👑", "Legend", "Reach level 30", level >= 30),
        )

        return Profile(
            xp = xp,
            level = level,
            levelStartXp = xpForLevel(level),
            nextLevelXp = xpForLevel(level + 1),
            rank = rank,
            nextRank = nextRank,
            willpower = willpower,
            attributes = attributes,
            followThrough = followThrough,
            badges = badges,
            perfectDays = perfect.size,
            totalCompletions = completions,
            bestStreak = best,
        )
    }

    fun overview(data: AppData, today: LocalDate): Overview {
        val active = data.habits.filter { !it.archived }
        val ranking = active.map {
            Ranked(it, rate(it, today.minusDays(29), today, today), currentStreak(it, today))
        }.sortedWith(compareByDescending<Ranked> { it.rate30 ?: -1f }.thenByDescending { it.streak })
        val tags = active.flatMap { it.tags }.distinct().sorted()
        return Overview(
            rate7 = combinedRate(active, today.minusDays(6), today, today),
            rate30 = combinedRate(active, today.minusDays(29), today, today),
            rateAll = combinedRate(active, today.minusYears(50), today, today),
            totalCompletions = data.habits.sumOf { totalCompletions(it, today) },
            perfectDays = perfectDays(active, today).size,
            bestStreak = data.habits.maxOfOrNull { bestStreak(it, today) } ?: 0,
            heatmap = overallHeatmap(active, today),
            weekday = weekdayRates(active, today),
            trendWeeks = trend(active, today, 12, 7),
            trendMonths = trend(active, today, 12, 30),
            ranking = ranking,
            tagRates = tags.map { t ->
                t to combinedRate(active.filter { t in it.tags }, today.minusDays(29), today, today)
            },
        )
    }

    fun weekdayLabels(): List<String> =
        DayOfWeek.entries.map { it.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()) }
}

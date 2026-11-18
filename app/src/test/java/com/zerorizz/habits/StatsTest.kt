package com.zerorizz.habits

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class StatsTest {
    private val today = LocalDate.of(2026, 11, 17) // a Tuesday

    private fun check(daysAgoDone: List<Int>, created: Int = 30) = Habit(
        id = 1, name = "Gym", createdAt = today.minusDays(created.toLong()),
        entries = daysAgoDone.associate { today.minusDays(it.toLong()) to 1.0 },
    )

    @Test fun streakSkipsUnfinishedToday() {
        val h = check(listOf(1, 2, 3, 5))
        assertEquals(3, Stats.currentStreak(h, today))
        assertEquals(4, Stats.currentStreak(h.withValue(today, 1.0), today))
    }

    @Test fun bestStreak() {
        val h = check(listOf(10, 11, 12, 13, 14, 1, 2))
        assertEquals(5, Stats.bestStreak(h, today))
    }

    @Test fun scheduleRespected() {
        // weekdays only; Sat/Sun misses shouldn't break streak
        val weekdays = (1..14).filter { today.minusDays(it.toLong()).dayOfWeek.value <= 5 }
        val h = check(weekdays).copy(days = setOf(1, 2, 3, 4, 5))
        assertEquals(weekdays.size, Stats.currentStreak(h, today))
        assertEquals(1f, Stats.rate(h, today.minusDays(14), today, today)!!, 0.001f)
    }

    @Test fun quitHabit() {
        val h = Habit(id = 2, name = "Smoking", type = HabitType.QUIT, createdAt = today.minusDays(10),
            entries = mapOf(today.minusDays(4) to 2.0))
        assertEquals(4, Stats.currentStreak(h, today)) // 3 clean past days + clean today
        assertEquals(0, Stats.currentStreak(h.withValue(today, 1.0), today))
    }

    @Test fun countHabit() {
        val h = Habit(id = 3, name = "Water", type = HabitType.COUNT, target = 8.0, createdAt = today.minusDays(5),
            entries = mapOf(today.minusDays(1) to 8.0, today.minusDays(2) to 3.0))
        assertTrue(h.isDone(today.minusDays(1)))
        assertFalse(h.isDone(today.minusDays(2)))
        assertEquals(0.375f, h.progress(today.minusDays(2)), 0.001f)
        assertEquals(1, Stats.currentStreak(h, today))
    }

    @Test fun likelihoodRange() {
        val good = check((1..30).toList(), 40)
        val bad = check(listOf(20), 40)
        val pg = Stats.likelihood(good, today)!!
        val pb = Stats.likelihood(bad, today)!!
        assertTrue("good=$pg", pg > 0.9f)
        assertTrue("bad=$pb", pb < 0.15f)
        assertNull(Stats.likelihood(check(emptyList(), 1), today))
    }

    @Test fun profileEmptyAndFull() {
        val empty = Stats.profile(AppData(), today)
        assertEquals(1, empty.level)
        assertEquals(0, empty.willpower)
        assertEquals("Rookie", empty.rank.name)

        val habits = listOf(check((0..60).toList(), 60), check((0..60 step 2).toList(), 60).copy(id = 9, name = "Read"))
        val p = Stats.profile(AppData(habits = habits), today)
        assertTrue("xp=${p.xp}", p.xp > 500)
        assertTrue(p.level >= 3)
        assertTrue("wp=${p.willpower}", p.willpower in 50..100)
        assertTrue(p.badges.first { it.name == "Monthly Master" }.earned)
        assertEquals(6, p.attributes.size)
        p.attributes.forEach { assertTrue(it.value in 0f..1f) }

        val o = Stats.overview(AppData(habits = habits), today)
        assertEquals(12, o.trendWeeks.size)
        assertEquals(7, o.weekday.size)
        assertEquals("Gym", o.ranking.first().habit.name)
    }

    @Test fun markdownHelpers() {
        assertEquals(listOf("Gym", "Read"), extractLinks("did [[Gym]] and [[Read]] #win"))
        assertEquals(listOf("win", "life/goals"), extractTags("#win today, see #life/goals not a#tag"))
        assertEquals("- [x] a\n- [ ] b", toggleTaskLine("- [ ] a\n- [ ] b", 0))
    }
}

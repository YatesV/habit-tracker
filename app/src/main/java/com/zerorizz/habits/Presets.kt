package com.zerorizz.habits

import java.time.LocalDate

data class Addon(val id: String, val emoji: String, val name: String, val description: String, val pro: Boolean = false)

object Addons {
    const val CELEBRATIONS = "celebrations"
    const val MOTIVATION = "motivation"
    const val MOOD = "mood"

    val all = listOf(
        Addon(CELEBRATIONS, "🎉", "Celebrations", "Confetti and a buzz when you finish a habit, a bigger burst when you clear the whole day."),
        Addon(MOTIVATION, "💬", "Daily Motivation", "A fresh line of motivation at the top of Today, every day."),
        Addon(MOOD, "😊", "Mood Tracker", "Log how you feel each day, see your mood graph, and find out which habits lift your mood.", pro = true),
    )

    fun byId(id: String): Addon = all.firstOrNull { it.id == id } ?: Addon(id, "🧩", id, "")
}

data class Goal(val id: String, val emoji: String, val label: String, val packs: List<String>)

val GOALS = listOf(
    Goal("health", "🍎", "Health", listOf("selfcare", "morning")),
    Goal("fitness", "💪", "Fitness", listOf("fitness")),
    Goal("mind", "🧘", "Mindfulness", listOf("mind")),
    Goal("productivity", "🚀", "Productivity", listOf("study", "morning")),
    Goal("quit", "🚫", "Quit bad habits", listOf("quit")),
    Goal("sleep", "😴", "Better sleep", listOf("sleep")),
    Goal("learning", "📚", "Learning", listOf("study")),
    Goal("selfcare", "🧴", "Self-care", listOf("selfcare")),
)

private fun h(
    name: String, emoji: String, color: Int,
    type: HabitType = HabitType.CHECK, target: Double = 1.0, unit: String = "",
    days: Set<Int> = ALL_DAYS, tags: List<String> = emptyList(), folder: String = "",
    proof: Boolean = false, note: String = "",
) = Habit(
    id = 0, name = name, emoji = emoji, color = color, type = type, target = target, unit = unit,
    days = days, tags = tags, folder = folder, requireProof = proof, note = note,
)

val BUILT_IN_PACKS = listOf(
    HabitPack(
        "morning", "Morning Routine", "🌅", "Start every day the same strong way.", "Habitify",
        listOf(
            h("Wake up on time", "⏰", 6, folder = "Morning", tags = listOf("morning")),
            h("Make your bed", "🛏️", 1, folder = "Morning", tags = listOf("morning"), proof = true),
            h("Brush teeth", "🦷", 5, type = HabitType.COUNT, target = 2.0, unit = "times", folder = "Morning", tags = listOf("health"), proof = true),
            h("Drink water", "💧", 1, type = HabitType.COUNT, target = 8.0, unit = "glasses", tags = listOf("health")),
            h("Meditate", "🧘", 4, type = HabitType.TIMER, target = 10.0, folder = "Morning", tags = listOf("mind")),
        ),
    ),
    HabitPack(
        "fitness", "Fitness Starter", "💪", "Move every day and track your progress.", "Habitify",
        listOf(
            h("Workout", "🏋️", 7, days = setOf(1, 3, 5), tags = listOf("fitness"), proof = true),
            h("Walk", "🚶", 0, type = HabitType.NUMBER, target = 8000.0, unit = "steps", tags = listOf("fitness")),
            h("Stretch", "🤸", 9, type = HabitType.TIMER, target = 10.0, tags = listOf("fitness")),
            h("Protein", "🥩", 10, type = HabitType.NUMBER, target = 120.0, unit = "g", tags = listOf("fitness", "food")),
        ),
    ),
    HabitPack(
        "study", "Study & Focus", "📚", "Deep work, reading and fewer distractions.", "Habitify",
        listOf(
            h("Read", "📖", 2, type = HabitType.TIMER, target = 30.0, tags = listOf("learning")),
            h("Study", "🧠", 8, type = HabitType.TIMER, target = 60.0, days = setOf(1, 2, 3, 4, 5), tags = listOf("learning")),
            h("No phone first hour", "📵", 11, tags = listOf("focus")),
            h("Plan tomorrow", "📝", 3, tags = listOf("focus")),
        ),
    ),
    HabitPack(
        "mind", "Mindfulness", "🧘", "Calm, gratitude and reflection.", "Habitify",
        listOf(
            h("Meditate", "🧘", 4, type = HabitType.TIMER, target = 10.0, tags = listOf("mind")),
            h("Gratitude", "🙏", 6, tags = listOf("mind"), note = "Write **3 things** you're grateful for in your journal."),
            h("Time outside", "🌳", 0, type = HabitType.TIMER, target = 20.0, tags = listOf("mind")),
        ),
    ),
    HabitPack(
        "quit", "Quit Pack", "🚫", "Track clean days and break bad habits.", "Habitify",
        listOf(
            h("No vaping", "🚭", 7, type = HabitType.QUIT, tags = listOf("quit")),
            h("No junk food", "🍔", 2, type = HabitType.QUIT, tags = listOf("quit", "food")),
            h("No doomscrolling", "📱", 8, type = HabitType.QUIT, tags = listOf("quit")),
            h("No energy drinks", "🥤", 5, type = HabitType.QUIT, tags = listOf("quit")),
        ),
    ),
    HabitPack(
        "sleep", "Better Sleep", "😴", "Wind down and wake up rested.", "Habitify",
        listOf(
            h("Sleep", "😴", 8, type = HabitType.NUMBER, target = 8.0, unit = "hours", tags = listOf("sleep")),
            h("No screens after 11", "🌙", 11, tags = listOf("sleep")),
            h("Lights out on time", "💡", 6, tags = listOf("sleep")),
        ),
    ),
    HabitPack(
        "selfcare", "Self-care", "🧴", "Look after yourself, every day.", "Habitify",
        listOf(
            h("Skincare", "🧴", 3, tags = listOf("selfcare"), proof = true),
            h("Floss", "🦷", 5, tags = listOf("health"), proof = true),
            h("Take vitamins", "💊", 9, tags = listOf("health")),
            h("Eat a fruit or veg", "🍎", 0, type = HabitType.COUNT, target = 3.0, unit = "servings", tags = listOf("food")),
        ),
    ),
)

val THEME_PRESETS = listOf(
    ThemePreset("default", "Material You", "📱", 0xFF4CAF50.toInt()),
    ThemePreset("forest", "Forest", "🌲", 0xFF2E7D32.toInt()),
    ThemePreset("ocean", "Ocean", "🌊", 0xFF0277BD.toInt()),
    ThemePreset("sunset", "Sunset", "🌅", 0xFFFF7043.toInt()),
    ThemePreset("grape", "Grape", "🍇", 0xFF7B1FA2.toInt()),
    ThemePreset("rose", "Rose", "🌹", 0xFFE91E63.toInt()),
    ThemePreset("midnight", "Midnight", "🌌", 0xFF536DFE.toInt(), ThemeMode.AMOLED, pro = true),
    ThemePreset("gold", "Gold Rush", "🏆", 0xFFFFB300.toInt(), ThemeMode.DARK, pro = true),
    ThemePreset("mint", "Mint", "🍃", 0xFF26A69A.toInt(), ThemeMode.LIGHT, pro = true),
    ThemePreset("lava", "Lava", "🌋", 0xFFD84315.toInt(), ThemeMode.AMOLED, pro = true),
    ThemePreset("sakura", "Sakura", "🌸", 0xFFF48FB1.toInt(), ThemeMode.LIGHT, pro = true),
    ThemePreset("cyber", "Cyber", "👾", 0xFF00E5FF.toInt(), ThemeMode.AMOLED, pro = true),
)

/** Original lines written for Habitify. */
val MOTIVATION = listOf(
    "Small wins, stacked daily, become big ones.",
    "You don't need motivation. You need a streak.",
    "Do it badly today. It still counts.",
    "The best time was yesterday. The next best is now.",
    "Future you is watching. Make them proud.",
    "Show up. That's 80% of it.",
    "One more day. That's the whole plan.",
    "Discipline is remembering what you want.",
    "Missed yesterday? Today is the comeback.",
    "Consistency beats intensity.",
    "Make it easy to start and hard to skip.",
    "You're one habit away from a different life.",
    "Progress, not perfection.",
    "Win the morning, win the day.",
    "Don't break the chain.",
    "Every rep is a vote for who you're becoming.",
    "Hard now, easy later. Easy now, hard later.",
    "You've done hard things before.",
    "The goal isn't to be perfect. It's to keep going.",
    "Boring routines build exciting lives.",
    "If it takes under two minutes, do it now.",
    "Today counts. Make it count.",
    "Start where you are. Use what you have.",
    "Tiny steps still move you forward.",
    "Your streak is proof you can trust yourself.",
    "Be the person your habits say you are.",
    "Rest if you must, but don't quit.",
    "Momentum is built one day at a time.",
    "Less thinking, more doing.",
    "The comeback is always stronger than the setback.",
)

fun motivationFor(d: LocalDate): String = MOTIVATION[(d.dayOfYear + d.year) % MOTIVATION.size]

val MOODS = listOf("😞", "😕", "😐", "🙂", "😄")

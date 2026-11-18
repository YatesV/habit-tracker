package com.zerorizz.habits

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class UiSmokeTest {
    @get:Rule val rule = createComposeRule()

    private fun seed(ctx: Context): AppState {
        val t = LocalDate.now()
        val habits = listOf(
            Habit(1, "Gym", "🏋️", 0, HabitType.CHECK, createdAt = t.minusDays(40),
                entries = (1..30).filter { it % 3 != 0 }.associate { t.minusDays(it.toLong()) to 1.0 },
                tags = listOf("health"), folder = "Body", note = "# Plan\n- [ ] legs\n**go** see [[Water]] #fit"),
            Habit(2, "Water", "💧", 1, HabitType.COUNT, target = 8.0, unit = "glasses", createdAt = t.minusDays(20),
                entries = (0..15).associate { t.minusDays(it.toLong()) to (it % 9).toDouble() }),
            Habit(3, "Read", "📚", 2, HabitType.TIMER, target = 30.0, createdAt = t.minusDays(10),
                entries = mapOf(t.minusDays(1) to 45.0)),
            Habit(4, "Run", "🏃", 3, HabitType.NUMBER, target = 5.0, unit = "km", days = setOf(1, 3, 5), createdAt = t.minusDays(14)),
            Habit(5, "Vape", "🚫", 7, HabitType.QUIT, createdAt = t.minusDays(9), entries = mapOf(t.minusDays(3) to 1.0)),
        )
        val data = AppData(habits, mapOf(t to "# Today\nDid [[Gym]] #win\n- [x] done"))
        File(ctx.filesDir, "habitify.json").delete()
        Storage.save(ctx, data)
        return AppState(ctx)
    }

    private fun tab(name: String) = rule.onNode(hasText(name) and isSelectable() and !hasText("#", substring = true))

    @Test fun walkThroughApp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val state = seed(ctx)
        rule.setContent { HabitifyTheme(state.data.settings) { HabitifyApp(state) } }

        rule.onNodeWithText("Habitify").assertExists()
        rule.onNodeWithText("Gym").assertExists()
        // quick actions
        rule.onNodeWithContentDescription("Mark done").performClick()
        rule.onNodeWithContentDescription("Add one").performClick()
        rule.onNodeWithContentDescription("Start timer").performClick()
        rule.onNodeWithContentDescription("Stop timer").performClick()
        rule.onNodeWithText("Slipped").performClick()

        // tabs
        tab("Stats").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Leaderboard · last 30 days").assertExists()
        rule.onNodeWithText("Months").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithText("Months").assertIsSelected()
        tab("Journal").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Search notes").assertExists()
        tab("Profile").performClick(); rule.waitForIdle()
        rule.onNodeWithText("⚡ Willpower").assertExists()
        rule.onNodeWithText("Badges", substring = true).performScrollTo().assertExists()
        rule.onNodeWithText("Rank ladder").performScrollTo().assertExists()
        tab("Today").performClick(); rule.waitForIdle()

        // detail + edit
        rule.onNodeWithText("Water").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Last 12 months").assertExists()
        rule.onNodeWithContentDescription("Edit").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Edit habit").assertExists()
        rule.onNodeWithContentDescription("Back").performClick(); rule.waitForIdle()
        rule.onNodeWithContentDescription("Back").performClick(); rule.waitForIdle()

        // other habit details render
        listOf("Gym", "Read", "Run", "Vape").forEach { n ->
            rule.onNodeWithText(n).performClick(); rule.waitForIdle()
            rule.onNodeWithText("Success trend").assertExists()
            rule.onNodeWithContentDescription("Back").performClick(); rule.waitForIdle()
        }

        // new habit
        rule.onNodeWithText("New habit", useUnmergedTree = true).performClick(); rule.waitForIdle()
        rule.onNodeWithText("Habit name").performTextInput("Meditate")
        rule.onNodeWithText("Create habit").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithText("Meditate").assertExists()
        check(state.data.habits.any { it.name == "Meditate" })

        // settings
        rule.onNodeWithContentDescription("Settings").performClick(); rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1000); rule.waitForIdle()
        rule.onNodeWithText("Compact cards").performScrollTo().performClick(); rule.waitForIdle()
        check(state.data.settings.compact)
        rule.onNodeWithText("AMOLED").performScrollTo().performClick(); rule.waitForIdle()
        check(state.data.settings.theme == ThemeMode.AMOLED) { "theme=" + state.data.settings.theme }
        rule.onNodeWithContentDescription("Back").performClick(); rule.waitForIdle()

        // journal editor
        tab("Journal").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Today's note", useUnmergedTree = true).performClick(); rule.waitForIdle()
        rule.onNodeWithText("Edit").performClick(); rule.waitForIdle()
        rule.onNodeWithContentDescription("Back").performClick(); rule.waitForIdle()

        // data persisted to disk and reloads
        val reloaded = Storage.load(ctx)
        check(reloaded.habits.size == 6) { "habits=${reloaded.habits.size}" }
        check(reloaded.habits.first { it.name == "Gym" }.isDone(LocalDate.now()))
    }
}

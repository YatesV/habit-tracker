package com.zerorizz.habits

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
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

    private fun ctx() = ApplicationProvider.getApplicationContext<Context>()

    private fun seed(ctx: Context, onboarded: Boolean = true, pro: Boolean = false): AppState {
        val t = LocalDate.now()
        val habits = listOf(
            Habit(1, "Gym", "🏋️", 0, HabitType.CHECK, createdAt = t.minusDays(40),
                entries = (1..30).filter { it % 3 != 0 }.associate { t.minusDays(it.toLong()) to 1.0 },
                tags = listOf("health"), folder = "Body", note = "# Plan\n- [ ] legs\n**go** see [[Water]] #fit"),
            Habit(2, "Water", "💧", 1, HabitType.COUNT, target = 8.0, unit = "glasses", createdAt = t.minusDays(20),
                entries = (0..15).associate { t.minusDays(it.toLong()) to (it % 9).toDouble() }),
            Habit(3, "Read", "📚", 2, HabitType.TIMER, target = 30.0, createdAt = t.minusDays(10),
                entries = mapOf(t.minusDays(1) to 45.0)),
            Habit(4, "Brush", "🦷", 3, HabitType.CHECK, requireProof = true, proofSince = t.minusDays(2), createdAt = t.minusDays(14),
                entries = mapOf(t.minusDays(1) to 1.0)),
            Habit(5, "Vape", "🚫", 7, HabitType.QUIT, createdAt = t.minusDays(9), entries = mapOf(t.minusDays(3) to 1.0)),
        )
        val data = AppData(
            habits, mapOf(t to "# Today\nDid [[Gym]] #win\n- [x] done"),
            settings = Settings(onboarded = onboarded, pro = pro, addons = setOf(Addons.CELEBRATIONS, Addons.MOTIVATION, Addons.MOOD)),
            moods = (1..10).associate { t.minusDays(it.toLong()) to (it % 5) + 1 },
        )
        File(ctx.filesDir, "habitify.json").delete()
        Storage.save(ctx, data)
        return AppState(ctx)
    }

    private fun tab(name: String) = rule.onNode(hasText(name) and isSelectable() and !hasText("#", substring = true))

    @Test fun walkThroughApp() {
        val ctx = ctx()
        val state = seed(ctx, pro = true)
        rule.setContent { HabitifyTheme(state.data.settings) { HabitifyApp(state) } }

        rule.onNodeWithText("Habitify").assertExists()
        rule.onNodeWithText("Gym").assertExists()
        rule.onNodeWithText("How are you feeling?").assertExists()
        rule.onNodeWithText("😄").performClick(); rule.waitForIdle()
        check(state.data.moods[LocalDate.now()] == 5)

        // quick actions + celebration
        val ticks = state.celebrateTick
        rule.onNodeWithContentDescription("Mark done").performClick(); rule.waitForIdle()
        check(state.celebrateTick == ticks + 1) { "celebrate not fired" }
        rule.onNodeWithContentDescription("Add one").performClick()
        rule.onNodeWithContentDescription("Start timer").performClick()
        rule.onNodeWithContentDescription("Stop timer").performClick()
        rule.onNodeWithText("Slipped").performClick()
        // proof habit: shows camera button and stays not done until a photo is attached
        rule.onNodeWithContentDescription("Take proof photo").assertExists()
        rule.onNodeWithText("needs proof", substring = true).assertDoesNotExist()
        state.attachProof(4, LocalDate.now(), "fake.jpg"); rule.waitForIdle()
        check(state.habit(4)!!.isDone(LocalDate.now()))

        // tabs
        tab("Stats").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Leaderboard · last 30 days").assertExists()
        rule.onNodeWithText("😊 Mood · last 30 days").performScrollTo().assertExists()
        rule.onNodeWithText("Months").performScrollTo().performClick(); rule.waitForIdle()
        tab("Journal").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Search notes").assertExists()
        tab("Profile").performClick(); rule.waitForIdle()
        rule.onNodeWithText("⚡ Willpower").assertExists()
        rule.onNodeWithText("Rank ladder").performScrollTo().assertExists()
        tab("Today").performClick(); rule.waitForIdle()

        // details for every type, incl. proof gallery
        listOf("Gym", "Water", "Read", "Brush", "Vape").forEach { n ->
            rule.onNodeWithText(n).performClick(); rule.waitForIdle()
            rule.onNodeWithText("Success trend").assertExists()
            if (n == "Brush") rule.onNodeWithText("📸 Proof gallery (1)").assertExists()
            rule.onNodeWithContentDescription("Back").performClick(); rule.waitForIdle()
        }

        // edit habit with proof options
        rule.onNodeWithText("Water").performClick(); rule.waitForIdle()
        rule.onNodeWithContentDescription("Edit").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Require a photo").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Back").performClick(); rule.waitForIdle()
        rule.onNodeWithContentDescription("Back").performClick(); rule.waitForIdle()

        // new habit
        rule.onNodeWithText("New habit", useUnmergedTree = true).performClick(); rule.waitForIdle()
        rule.onNodeWithText("Habit name").performTextInput("Meditate")
        rule.onNodeWithText("Create habit").performScrollTo().performClick(); rule.waitForIdle()
        check(state.data.habits.any { it.name == "Meditate" })

        // settings: theme, presets, card style
        rule.onNodeWithContentDescription("Settings").performClick(); rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1000); rule.waitForIdle()
        rule.onNodeWithText("AMOLED").performScrollTo().performClick(); rule.waitForIdle()
        check(state.data.settings.theme == ThemeMode.AMOLED)
        rule.onNodeWithText("Ocean").performScrollTo().performClick(); rule.waitForIdle()
        check(state.data.settings.themePreset == "ocean")
        rule.onNodeWithText("Gradient").performScrollTo().performClick(); rule.waitForIdle()
        check(state.data.settings.cardStyle == CardStyle.GRADIENT)
        rule.onNodeWithText("Large").performScrollTo().performClick(); rule.waitForIdle()

        // add-ons + pack
        rule.onNodeWithText("Add-ons & packs").performScrollTo().performClick(); rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1000); rule.waitForIdle()
        rule.onNodeWithText("Fitness Starter").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithText("Add").performClick(); rule.waitForIdle()
        check(state.data.habits.any { it.name == "Workout" && it.requireProof })
        rule.onNodeWithContentDescription("Back").performClick(); rule.waitForIdle()

        // paywall renders without billing
        rule.onNodeWithText("Habitify Pro").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithText("You're Pro 👑").assertExists()
        rule.onNodeWithContentDescription("Close").performClick(); rule.waitForIdle()
        rule.onNodeWithContentDescription("Back").performClick(); rule.waitForIdle()

        // persisted
        val reloaded = Storage.load(ctx)
        check(reloaded.habits.any { it.name == "Meditate" })
        check(reloaded.settings.cardStyle == CardStyle.GRADIENT)
    }

    @Test fun freeLimitOpensPaywall() {
        val ctx = ctx()
        val state = seed(ctx, pro = false)
        rule.setContent { HabitifyTheme(state.data.settings) { HabitifyApp(state) } }
        rule.onNodeWithText("New habit", useUnmergedTree = true).performClick(); rule.waitForIdle()
        rule.onNodeWithText("Upgrade", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Subscriptions aren't available", substring = true).assertExists()
        // mood add-on is Pro: hidden on free
        rule.onNodeWithContentDescription("Close").performClick(); rule.waitForIdle()
        rule.onNodeWithText("How are you feeling?").assertDoesNotExist()
    }

    @Test fun onboardingFlow() {
        val ctx = ctx()
        File(ctx.filesDir, "habitify.json").delete()
        Storage.save(ctx, AppData())
        val state = AppState(ctx)
        rule.setContent { HabitifyTheme(state.data.settings) { HabitifyApp(state) } }

        rule.onNodeWithText("Welcome to Habitify").assertExists()
        rule.onNodeWithText("Get started").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Your name").performTextInput("Yates")
        rule.onNodeWithText("Next").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Fitness").performClick()
        rule.onNodeWithText("Next").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Pick your starter habits").assertExists()
        rule.onNodeWithText("Next").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Ocean").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithText("Next").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Celebrations").assertExists()
        rule.onNodeWithText("Next").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Let's go 🚀").performClick(); rule.waitForIdle()

        check(state.data.settings.onboarded)
        check(state.data.settings.userName == "Yates")
        check(state.data.habits.size == 3) { "habits=${state.data.habits.map { it.name }}" }
        check(state.data.habits.first().name == "Workout")
        rule.onNodeWithText("Workout").assertExists()
    }

    @Test fun packJsonRoundTrip() {
        val pack = BUILT_IN_PACKS.first()
        val parsed = HabitPack.fromJson(JSONObject(pack.toJson().toString()))!!
        check(parsed.habits.size == pack.habits.size)
        check(parsed.habits.map { it.name } == pack.habits.map { it.name })
        check(HabitPack.fromJson(JSONObject("{\"format\":\"nope\"}")) == null)
        val theme = ThemePreset("x", "X", "🎨", 0xFF112233.toInt())
        check(ThemePreset.fromJson(theme.toJson())!!.seed == theme.seed)
        // v2 data upgrades as already onboarded
        val v2 = JSONObject("{\"habits\":[{\"id\":1,\"name\":\"A\"}],\"settings\":{\"theme\":\"DARK\"}}")
        check(AppData.fromJson(v2).settings.onboarded)
    }
}

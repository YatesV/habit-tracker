package com.zerorizz.habits

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val PAGES = 7

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(state: AppState) {
    val data = state.data
    var page by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf(data.settings.userName) }
    var goals by remember { mutableStateOf(data.settings.goals) }
    val suggestions = remember(goals) {
        val packIds = GOALS.filter { it.id in goals }.flatMap { it.packs }.ifEmpty { listOf("morning") }.distinct()
        BUILT_IN_PACKS.filter { it.id in packIds }.flatMap { it.habits }.distinctBy { it.name }
    }
    var picked by remember(suggestions) { mutableStateOf(suggestions.take(3).map { it.name }.toSet()) }
    val limit = if (data.isPro) Int.MAX_VALUE else Pro.FREE_HABIT_LIMIT - data.activeHabits.size

    fun finish() {
        state.addHabits(suggestions.filter { it.name in picked }.take(limit.coerceAtLeast(0)))
        state.updateSettings { it.copy(userName = name.trim(), goals = goals, onboarded = true) }
    }

    Scaffold { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // progress
            if (page in 1 until PAGES - 1) {
                LinearProgressIndicator(
                    progress = { page / (PAGES - 1f) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).height(6.dp).clip(CircleShape),
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            } else Spacer(Modifier.height(30.dp))

            AnimatedContent(
                targetState = page,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    if (targetState > initialState) (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
                    else (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
                },
                label = "onboarding",
            ) { p ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (p) {
                        0 -> WelcomePage()
                        1 -> Page("👋", "What should we call you?", "This shows on your profile.") {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it.take(30) },
                                label = { Text("Your name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        2 -> Page("🎯", "What do you want to work on?", "Pick as many as you like.") {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                maxItemsInEachRow = 2,
                            ) {
                                GOALS.forEach { g ->
                                    val on = g.id in goals
                                    val bg by animateColorAsState(
                                        if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        label = "goal",
                                    )
                                    val sc by animateFloatAsState(if (on) 1.03f else 1f, spring(0.4f), label = "goalScale")
                                    Row(
                                        Modifier
                                            .weight(1f)
                                            .scale(sc)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(bg)
                                            .then(if (on) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)) else Modifier)
                                            .clickable { goals = if (on) goals - g.id else goals + g.id }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(g.emoji, fontSize = 24.sp)
                                        Spacer(Modifier.width(10.dp))
                                        Text(g.label, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                        3 -> Page(
                            "🌱", "Pick your starter habits",
                            if (data.isPro) "You can change or add more anytime." else "Free plan: up to ${Pro.FREE_HABIT_LIMIT} habits. You can add your own later.",
                        ) {
                            suggestions.forEach { h ->
                                val on = h.name in picked
                                val canPick = on || picked.size < limit
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable(enabled = canPick) { picked = if (on) picked - h.name else picked + h.name }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    EmojiBadge(h.emoji, habitColor(h.color), 40.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(h.name, fontWeight = FontWeight.Medium)
                                        Text(
                                            buildString {
                                                append(h.type.label)
                                                if (h.type == HabitType.COUNT || h.type == HabitType.NUMBER || h.type == HabitType.TIMER) append(" · ${h.formatValue(h.target)}")
                                                if (h.requireProof) append(" · 📸 proof")
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Checkbox(checked = on, onCheckedChange = null, enabled = canPick)
                                }
                            }
                        }
                        4 -> Page("🎨", "Make it yours", "Pick a look. Tap to preview it live.") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ThemeMode.entries.forEach { m ->
                                    FilterChip(
                                        selected = data.settings.theme == m,
                                        onClick = { state.updateSettings { it.copy(theme = m) } },
                                        label = { Text(m.label) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            ThemePresetGrid(state, THEME_PRESETS.filter { !it.pro || data.isPro })
                        }
                        5 -> Page("🧩", "Add-ons", "Switch features on or off. Find more in Settings → Add-ons.") {
                            Addons.all.forEach { a -> AddonRow(state, a) }
                            Spacer(Modifier.height(12.dp))
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("📸", fontSize = 24.sp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "Photo proof: turn it on for any habit and it only counts once you snap a pic. Starter habits marked 📸 already have it.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                }
                            }
                        }
                        else -> DonePage(name, picked.size.coerceAtMost(limit.coerceAtLeast(0)))
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (page in 1 until PAGES - 1) {
                    TextButton(onClick = { page-- }) { Text("Back") }
                }
                Spacer(Modifier.weight(1f))
                if (page in 1 until PAGES - 1) {
                    TextButton(onClick = { page++ }) { Text("Skip") }
                    Spacer(Modifier.width(8.dp))
                }
                Button(
                    onClick = { if (page == PAGES - 1) finish() else page++ },
                    modifier = Modifier.height(52.dp),
                ) {
                    Text(
                        when (page) {
                            0 -> "Get started"
                            PAGES - 1 -> "Let's go 🚀"
                            else -> "Next"
                        },
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    val inf = rememberInfiniteTransition(label = "logo")
    val pulse by inf.animateFloat(1f, 1.12f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val tilt by inf.animateFloat(-6f, 6f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "tilt")
    Spacer(Modifier.height(60.dp))
    Box(
        Modifier.size(140.dp).scale(pulse).rotate(tilt).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) { Text("✅", fontSize = 72.sp) }
    Spacer(Modifier.height(32.dp))
    Text("Welcome to Habitify", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
    Spacer(Modifier.height(12.dp))
    Text(
        "Build habits that stick. Track anything, prove it with a photo, level up your willpower, and watch your graphs grow.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(32.dp))
    listOf("📊" to "Heatmaps, trends & stats", "📸" to "Photo proof so you can't cheat", "⚡" to "Willpower score, XP & ranks", "📓" to "Markdown journal with links").forEach { (e, t) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(e, fontSize = 22.sp)
            Spacer(Modifier.width(14.dp))
            Text(t, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun DonePage(name: String, count: Int) {
    val sc = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) { sc.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow)) }
    Spacer(Modifier.height(80.dp))
    Text("🎉", fontSize = 96.sp, modifier = Modifier.scale(sc.value))
    Spacer(Modifier.height(24.dp))
    Text(
        "You're all set${if (name.isNotBlank()) ", ${name.trim()}" else ""}!",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        if (count > 0) "$count habit${if (count == 1) "" else "s"} ready to go. Check one off today to start your first streak."
        else "Add your first habit with the + button.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Page(emoji: String, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(16.dp))
    Text(emoji, fontSize = 48.sp)
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))
    Column(Modifier.fillMaxWidth(), content = content)
    Spacer(Modifier.height(24.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemePresetGrid(state: AppState, presets: List<ThemePreset>, onLocked: (() -> Unit)? = null) {
    val s = state.data.settings
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = 3,
    ) {
        presets.forEach { p ->
            val selected = s.themePreset == p.id || (p.id == "default" && s.themePreset == null && s.customAccent == null && s.accent < 0)
            val locked = p.pro && !state.data.isPro
            val c = Color(p.seed)
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .then(if (selected) Modifier.border(2.dp, c, RoundedCornerShape(16.dp)) else Modifier)
                    .clickable { if (locked) onLocked?.invoke() else state.applyTheme(p) }
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(
                        if (p.id == "default") androidx.compose.ui.graphics.Brush.sweepGradient(HabitColors.take(6) + HabitColors[0])
                        else androidx.compose.ui.graphics.Brush.linearGradient(listOf(c, c.copy(alpha = 0.6f)))
                    ),
                    contentAlignment = Alignment.Center,
                ) { Text(p.emoji, fontSize = 18.sp) }
                Spacer(Modifier.height(6.dp))
                Text(p.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                if (locked) ProBadge(Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun AddonRow(state: AppState, a: Addon, onLocked: (() -> Unit)? = null) {
    val locked = a.pro && !state.data.isPro
    val on = a.id in state.data.settings.addons && !locked
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text(a.emoji, fontSize = 22.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.name, fontWeight = FontWeight.SemiBold)
                if (a.pro) {
                    Spacer(Modifier.width(6.dp))
                    ProBadge()
                }
            }
            Text(a.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = on,
            onCheckedChange = { v -> if (locked) onLocked?.invoke() else state.toggleAddon(a.id, v) },
        )
    }
}

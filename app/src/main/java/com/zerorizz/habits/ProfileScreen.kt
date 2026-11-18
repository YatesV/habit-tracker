package com.zerorizz.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(state: AppState, nav: Nav, modifier: Modifier = Modifier) {
    val data = state.data
    val today = LocalDate.now()
    val p = remember(data, today) { Stats.profile(data, today) }
    val cs = MaterialTheme.colorScheme
    val active = data.habits.filter { !it.archived }
    var showBadge by remember { mutableStateOf<Badge?>(null) }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold) },
                actions = { IconButton(onClick = { nav.open(Route.Settings) }) { Icon(Icons.Default.Settings, "Settings") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(top = padding.calculateTopPadding())
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Level + rank header
            Card(
                colors = CardDefaults.cardColors(containerColor = cs.primaryContainer),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(72.dp).clip(CircleShape).background(cs.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("LV", fontSize = 11.sp, color = cs.onPrimary, fontWeight = FontWeight.Bold)
                                Text("${p.level}", fontSize = 26.sp, color = cs.onPrimary, fontWeight = FontWeight.Black)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                data.settings.userName.ifBlank { "You" },
                                style = MaterialTheme.typography.titleMedium,
                                color = cs.onPrimaryContainer,
                            )
                            Text(
                                "${p.rank.emoji} ${p.rank.name}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = cs.onPrimaryContainer,
                            )
                            Text(
                                "${p.xp} XP total",
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    val into = p.xp - p.levelStartXp
                    val span = (p.nextLevelXp - p.levelStartXp).coerceAtLeast(1)
                    LinearProgressIndicator(
                        progress = { into.toFloat() / span },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                        trackColor = cs.primary.copy(alpha = 0.15f),
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${p.nextLevelXp - p.xp} XP to level ${p.level + 1}" +
                            (p.nextRank?.let { "  ·  ${it.emoji} ${it.name} at Lv ${it.minLevel}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onPrimaryContainer,
                    )
                }
            }

            // Willpower + follow-through
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ElevatedCard(Modifier.weight(1f)) {
                    Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚡ Willpower", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(10.dp))
                        ProgressRing(p.willpower / 100f, willpowerColor(p.willpower), Modifier.size(96.dp), stroke = 10.dp) {
                            Text("${p.willpower}", fontSize = 28.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(Stats.willpowerLabel(p.willpower), fontWeight = FontWeight.SemiBold)
                    }
                }
                ElevatedCard(Modifier.weight(1f)) {
                    Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎯 Follow-through", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(10.dp))
                        ProgressRing(p.followThrough ?: 0f, cs.tertiary, Modifier.size(96.dp), stroke = 10.dp) {
                            Text(pct(p.followThrough), fontSize = 24.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (p.followThrough == null) "Nothing due today" else "odds for today",
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // At-risk habits
            val todays = active.filter { it.isScheduled(today) && !(it.isDone(today) && it.type != HabitType.QUIT) }
                .map { it to Stats.likelihood(it, today) }
                .sortedBy { it.second ?: 0.5f }
            if (todays.isNotEmpty()) {
                SectionCard("Today's odds, habit by habit") {
                    todays.forEach { (h, l) ->
                        val c = habitColor(h.color)
                        Row(
                            Modifier.fillMaxWidth().clickable { nav.open(Route.HabitDetail(h.id)) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(h.emoji, fontSize = 20.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(h.name, Modifier.weight(1f), maxLines = 1)
                            Text(
                                l?.let { pct(it) } ?: "New",
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    l == null -> cs.onSurfaceVariant
                                    l < 0.4f -> Color(0xFFE53935)
                                    l < 0.7f -> Color(0xFFFB8C00)
                                    else -> c
                                },
                            )
                            if (l != null && l < 0.4f) Text("  at risk", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE53935))
                        }
                    }
                    Text(
                        "Based on your recent history, the same weekday, and your current streak.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }

            // Attributes
            SectionCard("Attributes") {
                RadarChart(
                    p.attributes.map { it.name to it.value },
                    cs.primary,
                    Modifier.fillMaxWidth().height(260.dp),
                )
                Spacer(Modifier.height(8.dp))
                p.attributes.forEach { a ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${a.emoji}  ${a.name}", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Text("${(a.value * 100).toInt()}", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { a.value },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                        Text(a.desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }

            // Badges
            val earned = p.badges.count { it.earned }
            SectionCard("Badges · $earned / ${p.badges.size}") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    maxItemsInEachRow = 4,
                ) {
                    p.badges.forEach { b ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (b.earned) cs.secondaryContainer else cs.surfaceContainerHigh)
                                .clickable { showBadge = b }
                                .padding(vertical = 10.dp, horizontal = 4.dp)
                                .alpha(if (b.earned) 1f else 0.4f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(if (b.earned) b.emoji else "🔒", fontSize = 28.sp)
                            Text(b.name, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 12.sp)
                        }
                    }
                }
            }

            // Rank ladder
            SectionCard("Rank ladder") {
                RANKS.forEach { r ->
                    val current = r == p.rank
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (current) cs.primaryContainer else Color.Transparent)
                            .padding(10.dp)
                            .alpha(if (p.level >= r.minLevel) 1f else 0.45f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(r.emoji, fontSize = 22.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(r.name, Modifier.weight(1f), fontWeight = if (current) FontWeight.Bold else FontWeight.Normal)
                        Text("Lv ${r.minLevel}+", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Earn XP for every completed day: 10 XP plus a streak bonus of up to +20.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }

            StatGrid(
                listOf(
                    Triple("✅", "Completions", "${p.totalCompletions}"),
                    Triple("⭐", "Perfect days", "${p.perfectDays}"),
                    Triple("🏆", "Best streak", "${p.bestStreak}"),
                    Triple("📓", "Journal entries", "${data.journal.size}"),
                )
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    showBadge?.let { b ->
        AlertDialog(
            onDismissRequest = { showBadge = null },
            icon = { Text(b.emoji, fontSize = 40.sp) },
            title = { Text(b.name) },
            text = { Text(b.desc + if (b.earned) "\n\nUnlocked ✓" else "\n\nNot unlocked yet") },
            confirmButton = { TextButton(onClick = { showBadge = null }) { Text("Nice") } },
        )
    }
}

fun willpowerColor(w: Int): Color = when {
    w >= 75 -> Color(0xFF43A047)
    w >= 50 -> Color(0xFF7CB342)
    w >= 30 -> Color(0xFFFB8C00)
    else -> Color(0xFFE53935)
}

package com.zerorizz.habits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(state: AppState, nav: Nav, modifier: Modifier = Modifier) {
    val data = state.data
    val today = LocalDate.now()
    val o = remember(data, today) { Stats.overview(data, today) }
    var trendMode by remember { mutableIntStateOf(0) }
    val primary = MaterialTheme.colorScheme.primary

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Stats", fontWeight = FontWeight.Bold) }) },
    ) { padding ->
        Column(
            Modifier
                .padding(top = padding.calculateTopPadding())
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (o.ranking.isEmpty()) {
                EmptyState("📊", "Nothing to chart yet", "Add a habit and check it off for a few days. Your graphs fill in here.")
                return@Column
            }
            StatGrid(
                listOf(
                    Triple("📆", "Last 7 days", pct(o.rate7)),
                    Triple("📈", "Last 30 days", pct(o.rate30)),
                    Triple("🗓️", "All time", pct(o.rateAll)),
                    Triple("✅", "Completions", "${o.totalCompletions}"),
                    Triple("⭐", "Perfect days", "${o.perfectDays}"),
                    Triple("🏆", "Best streak", "${o.bestStreak} days"),
                )
            )

            SectionCard("All habits · last 12 months") {
                Heatmap(o.heatmap, primary, today, data.settings.weekStartsMonday)
            }

            SectionCard(
                "Success trend",
                action = { SegmentedToggle(listOf("Weeks", "Months"), trendMode, { trendMode = it }) },
            ) {
                val labels = if (trendMode == 0) {
                    (11 downTo 0).map { if (it == 0) "Now" else "-${it}w" }
                } else {
                    (11 downTo 0).map { today.minusDays(it * 30L).month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
                }
                LineChart(if (trendMode == 0) o.trendWeeks else o.trendMonths, labels, primary)
            }

            SectionCard("Best days of the week") {
                BarChart(o.weekday, Stats.weekdayLabels(), primary)
                val best = o.weekday.withIndex().filter { it.value != null }.maxByOrNull { it.value!! }
                val worst = o.weekday.withIndex().filter { it.value != null }.minByOrNull { it.value!! }
                if (best != null && worst != null && best.index != worst.index) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Strongest on ${java.time.DayOfWeek.of(best.index + 1).getDisplayName(TextStyle.FULL, Locale.getDefault())}s, " +
                            "weakest on ${java.time.DayOfWeek.of(worst.index + 1).getDisplayName(TextStyle.FULL, Locale.getDefault())}s.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard("Leaderboard · last 30 days") {
                o.ranking.forEachIndexed { i, r ->
                    val c = habitColor(r.habit.color)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { nav.open(Route.HabitDetail(r.habit.id)) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "#${i + 1}" },
                            Modifier.width(36.dp),
                            fontWeight = FontWeight.Bold,
                        )
                        EmojiBadge(r.habit.emoji, c, 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.habit.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { r.rate30 ?: 0f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                color = c,
                                trackColor = c.copy(alpha = 0.15f),
                                gapSize = 0.dp,
                                drawStopIndicator = {},
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(pct(r.rate30), fontWeight = FontWeight.Bold)
                            if (r.streak > 0) Text("🔥 ${r.streak}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (o.tagRates.isNotEmpty()) {
                SectionCard("By tag · last 30 days") {
                    o.tagRates.forEach { (tag, rate) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("#$tag", Modifier.width(110.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            LinearProgressIndicator(
                                progress = { rate ?: 0f },
                                modifier = Modifier.weight(1f).height(8.dp).clip(CircleShape),
                                gapSize = 0.dp,
                                drawStopIndicator = {},
                            )
                            Text(pct(rate), Modifier.width(48.dp), fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

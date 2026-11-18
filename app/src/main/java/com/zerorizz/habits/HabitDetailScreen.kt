package com.zerorizz.habits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitDetailScreen(state: AppState, id: Long, nav: Nav) {
    val h = state.habit(id)
    if (h == null) {
        LaunchedEffect(Unit) { nav.back() }
        return
    }
    val today = LocalDate.now()
    val color = habitColor(h.color)
    val settings = state.data.settings
    var trendMode by remember { mutableIntStateOf(0) }
    var pickDate by remember { mutableStateOf(false) }
    var valueDate by remember { mutableStateOf<LocalDate?>(null) }
    var viewProof by remember { mutableStateOf<LocalDate?>(null) }
    val capture = LocalProofCapture.current

    val s = remember(h, today) {
        object {
            val streak = Stats.currentStreak(h, today)
            val best = Stats.bestStreak(h, today)
            val r30 = Stats.rate(h, today.minusDays(29), today, today)
            val rAll = Stats.rate(h, h.startDate, today, today)
            val total = Stats.totalCompletions(h, today)
            val likely = Stats.likelihood(h, today)
            val heat = Stats.heatmap(h, today)
            val weeks = Stats.trend(listOf(h), today, 12, 7)
            val months = Stats.trend(listOf(h), today, 12, 30)
            val weekday = Stats.weekdayRates(listOf(h), today)
            val totalValue = Stats.totalValue(h)
        }
    }
    val backlinks = state.data.journal.entries
        .filter { (_, text) -> extractLinks(text).any { it.equals(h.name, ignoreCase = true) } }
        .sortedByDescending { it.key.toEpochDay() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text(h.name, maxLines = 1) },
                actions = {
                    IconButton(onClick = { pickDate = true }) { Icon(Icons.Default.DateRange, "Log a past day") }
                    IconButton(onClick = { nav.open(Route.HabitEdit(h.id)) }) { Icon(Icons.Default.Edit, "Edit") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge(h.emoji, color, 64.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(h.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        buildString {
                            append("${h.type.emoji} ${h.type.label}")
                            if (h.type != HabitType.CHECK && h.type != HabitType.QUIT) append(" · goal ${h.formatValue(h.target)}")
                            append(" · ${scheduleText(h.days)}")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (h.tags.isNotEmpty() || h.folder.isNotBlank()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (h.folder.isNotBlank()) AssistChip(onClick = {}, label = { Text("📁 ${h.folder}") })
                    h.tags.forEach { AssistChip(onClick = {}, label = { Text("#$it") }) }
                }
            }

            // Stats
            val tiles = mutableListOf(
                Triple(if (h.type == HabitType.QUIT) "🧊" else "🔥", "Current streak", "${s.streak} days"),
                Triple("🏆", "Best streak", "${s.best} days"),
                Triple("📈", "Last 30 days", pct(s.r30)),
                Triple("🗓️", "All time", pct(s.rAll)),
                Triple("✅", if (h.type == HabitType.QUIT) "Clean days" else "Completions", "${s.total}"),
                Triple(
                    "🎯", "Odds today",
                    if (h.isDone(today) && h.type != HabitType.QUIT) "Done ✓" else s.likely?.let { pct(it) } ?: "New",
                ),
            )
            if (h.type == HabitType.COUNT || h.type == HabitType.NUMBER || h.type == HabitType.TIMER) {
                val activeDays = h.entries.count { it.value > 0 }.coerceAtLeast(1)
                tiles += Triple("Σ", "Total", h.formatValue(s.totalValue))
                tiles += Triple("⌀", "Avg per active day", h.formatValue(s.totalValue / activeDays))
            }
            StatGrid(tiles)

            if (h.requireProof || h.proofs.isNotEmpty()) {
                val photos = h.proofs.entries.sortedByDescending { it.key.toEpochDay() }
                SectionCard(
                    "📸 Proof gallery (${photos.size})",
                    action = if (h.needsProof(today) && !h.hasProof(today)) {
                        { TextButton(onClick = { capture.request(h.id, today) }) { Text("Prove today") } }
                    } else null,
                ) {
                    if (photos.isEmpty()) {
                        Text(
                            "No proof yet. Complete this habit to take your first photo.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(photos, key = { it.key.toEpochDay() }) { (d, file) ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    ProofImage(
                                        state.proofFile(file),
                                        Modifier.size(96.dp).clip(RoundedCornerShape(14.dp)).clickable { viewProof = d },
                                    )
                                    Text(
                                        d.format(DateTimeFormatter.ofPattern("MMM d")),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        val needed = (0L until 30L).map { today.minusDays(it) }.filter { h.needsProof(it) && h.isScheduled(it) && h.goalMet(it) }
                        if (needed.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Proof rate (30 days): ${pct(needed.count { h.hasProof(it) }.toFloat() / needed.size)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            SectionCard("Last 12 months") {
                Heatmap(s.heat, color, today, settings.weekStartsMonday)
            }

            SectionCard(
                "Success trend",
                action = { SegmentedToggle(listOf("Weeks", "Months"), trendMode, { trendMode = it }) },
            ) {
                val pts = if (trendMode == 0) s.weeks else s.months
                val labels = if (trendMode == 0) {
                    (11 downTo 0).map { if (it == 0) "Now" else "-${it}w" }
                } else {
                    (11 downTo 0).map {
                        today.minusDays(it * 30L).month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    }
                }
                LineChart(pts, labels, color)
            }

            if (h.type == HabitType.COUNT || h.type == HabitType.NUMBER || h.type == HabitType.TIMER) {
                SectionCard("Last 30 days · ${if (h.type == HabitType.TIMER) "minutes" else h.unit.ifBlank { "amount" }}") {
                    val days = (29 downTo 0).map { today.minusDays(it.toLong()) }
                    val vals = days.map { if (it.isBefore(h.startDate)) null else h.value(it).toFloat() }
                    val maxV = maxOf(h.target.toFloat(), vals.filterNotNull().maxOrNull() ?: 0f) * 1.1f
                    LineChart(
                        vals,
                        days.map { it.format(DateTimeFormatter.ofPattern("M/d")) },
                        color,
                        maxValue = maxV,
                        percent = false,
                        refLine = h.target.toFloat(),
                    )
                    Text("Dashed line = daily goal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SectionCard("By day of week") {
                BarChart(s.weekday, Stats.weekdayLabels(), color)
            }

            if (h.note.isNotBlank()) {
                SectionCard("Notes") {
                    MarkdownView(
                        h.note,
                        onLink = { name -> state.data.habitByName(name)?.let { nav.open(Route.HabitDetail(it.id)) } },
                        onToggleTask = { line -> state.upsert(h.copy(note = toggleTaskLine(h.note, line))) },
                    )
                }
            }

            if (backlinks.isNotEmpty()) {
                SectionCard("Mentioned in journal (${backlinks.size})") {
                    backlinks.take(10).forEach { (date, text) ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { nav.open(Route.JournalEdit(date)) }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(friendlyDate(date, today), fontWeight = FontWeight.SemiBold)
                            Text(
                                stripMarkdown(text),
                                maxLines = 2,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            SectionCard("Recent history") {
                (0L until 14L).map { today.minusDays(it) }.filter { !it.isBefore(h.startDate) }.forEach { d ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                when {
                                    h.hasProof(d) -> viewProof = d
                                    h.type == HabitType.CHECK && h.needsProof(d) -> capture.request(h.id, d)
                                    h.type == HabitType.CHECK -> state.toggle(h.id, d)
                                    else -> valueDate = d
                                }
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(friendlyDate(d, today), Modifier.weight(1f))
                        if (h.hasProof(d)) Text("📸 ", fontSize = 14.sp)
                        if (h.awaitingProof(d)) Text("needs proof · ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Text(
                            if (!h.isScheduled(d) && h.value(d) == 0.0) "Rest day" else h.formatValue(h.value(d)),
                            color = if (h.isDone(d) && h.isScheduled(d)) color else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Text("Tap a day to change it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickDate) {
        PickDateDialog(today, onDismiss = { pickDate = false }) { d ->
            pickDate = false
            when {
                h.type == HabitType.CHECK && h.needsProof(d) && !h.isDone(d) -> capture.request(h.id, d)
                h.type == HabitType.CHECK -> state.toggle(h.id, d)
                else -> valueDate = d
            }
        }
    }
    viewProof?.let { d -> ProofViewer(state, h, d) { viewProof = null } }
    valueDate?.let { d ->
        ValueDialog(h, d, onDismiss = { valueDate = null }) { v ->
            state.setValue(h.id, d, v)
            valueDate = null
        }
    }
}

fun scheduleText(days: Set<Int>): String = when {
    days.size == 7 -> "every day"
    days == setOf(1, 2, 3, 4, 5) -> "weekdays"
    days == setOf(6, 7) -> "weekends"
    else -> days.sorted().joinToString(" ") {
        DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }
}

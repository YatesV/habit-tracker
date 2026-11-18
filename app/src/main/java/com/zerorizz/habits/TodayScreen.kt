package com.zerorizz.habits

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(state: AppState, nav: Nav, modifier: Modifier = Modifier) {
    val data = state.data
    val settings = data.settings
    val today = LocalDate.now()
    var selected by remember { mutableStateOf(today) }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var reorder by remember { mutableStateOf(false) }
    var valueFor by remember { mutableStateOf<Pair<Long, LocalDate>?>(null) }
    var confirmDelete by remember { mutableStateOf<Habit?>(null) }

    val active = data.habits.filter { !it.archived }
    val allTags = active.flatMap { it.tags }.distinct().sorted()
    val shown = active.filter { tagFilter == null || tagFilter in it.tags }
    val scheduled = active.filter { it.isScheduled(selected) }
    val done = scheduled.count { it.isDone(selected) }
    val likelihoods = remember(data, today) { active.associate { it.id to Stats.likelihood(it, today) } }
    val profile = remember(data, today) { Stats.profile(data, today) }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Habitify", fontWeight = FontWeight.Bold)
                        Text(
                            friendlyDate(selected, today),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { reorder = !reorder }) {
                        Icon(
                            Icons.AutoMirrored.Filled.List, "Reorder",
                            tint = if (reorder) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    IconButton(onClick = { nav.open(Route.Settings) }) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.open(Route.HabitEdit(null)) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New habit") },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp, bottom = 100.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "dates") { DateStrip(active, selected, today) { selected = it } }
            item(key = "summary") {
                SummaryCard(done, scheduled.size, selected == today, profile, settings) { nav.switchTab(3) }
            }
            if (allTags.isNotEmpty()) {
                item(key = "tags") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(selected = tagFilter == null, onClick = { tagFilter = null }, label = { Text("All") })
                        }
                        items(allTags) { t ->
                            FilterChip(
                                selected = tagFilter == t,
                                onClick = { tagFilter = if (tagFilter == t) null else t },
                                label = { Text("#$t") },
                            )
                        }
                    }
                }
            }
            if (active.isEmpty()) {
                item(key = "empty") {
                    EmptyState("🌱", "No habits yet", "Tap \"New habit\" to add your first one. Try a count, a timer, or a habit to quit.")
                }
            }
            val groups: Map<String, List<Habit>> =
                if (settings.groupByFolder) shown.groupBy { it.folder.trim() } else mapOf("" to shown)
            val ordered = groups.entries.sortedBy { if (it.key.isEmpty()) 0 else 1 }
            ordered.forEach { (folder, list) ->
                if (folder.isNotEmpty()) {
                    item(key = "folder:$folder") {
                        Text(
                            "📁  $folder",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                        )
                    }
                }
                items(list, key = { it.id }) { h ->
                    HabitCard(
                        h = h,
                        date = selected,
                        today = today,
                        state = state,
                        settings = settings,
                        likelihood = likelihoods[h.id],
                        reorder = reorder,
                        onOpen = { nav.open(Route.HabitDetail(h.id)) },
                        onEdit = { nav.open(Route.HabitEdit(h.id)) },
                        onDelete = { confirmDelete = h },
                        onValue = { d -> valueFor = h.id to d },
                    )
                }
            }
        }
    }

    valueFor?.let { (id, d) ->
        state.habit(id)?.let { h ->
            ValueDialog(h, d, onDismiss = { valueFor = null }) { v ->
                state.setValue(id, d, v)
                valueFor = null
            }
        }
    }
    confirmDelete?.let { h ->
        ConfirmDialog(
            "Delete \"${h.name}\"?",
            "This removes the habit and all its history. Archive it instead if you might want it back.",
            "Delete",
            onDismiss = { confirmDelete = null },
        ) {
            state.delete(h.id)
            confirmDelete = null
        }
    }
}

@Composable
private fun DateStrip(habits: List<Habit>, selected: LocalDate, today: LocalDate, onSelect: (LocalDate) -> Unit) {
    LazyRow(reverseLayout = true, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(60) { i ->
            val d = today.minusDays(i.toLong())
            val sched = habits.filter { it.isScheduled(d) && !d.isBefore(it.startDate) }
            val frac = if (sched.isEmpty()) 0f else sched.count { it.isDone(d) }.toFloat() / sched.size
            val isSel = d == selected
            val cs = MaterialTheme.colorScheme
            Column(
                Modifier
                    .width(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSel) cs.primary else cs.surfaceContainerHigh)
                    .clickable { onSelect(d) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    fontSize = 11.sp,
                    color = if (isSel) cs.onPrimary else cs.onSurfaceVariant,
                )
                Text(
                    d.dayOfMonth.toString(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = if (isSel) cs.onPrimary else cs.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.size(6.dp).clip(CircleShape).background(
                        when {
                            sched.isEmpty() -> Color.Transparent
                            frac >= 1f -> if (isSel) cs.onPrimary else cs.primary
                            frac > 0f -> (if (isSel) cs.onPrimary else cs.primary).copy(alpha = 0.4f)
                            else -> (if (isSel) cs.onPrimary else cs.outline).copy(alpha = 0.3f)
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    done: Int,
    total: Int,
    isToday: Boolean,
    profile: Profile,
    settings: Settings,
    onProfile: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onProfile),
        colors = CardDefaults.cardColors(containerColor = cs.primaryContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                if (total == 0) 0f else done.toFloat() / total,
                cs.primary,
                Modifier.size(76.dp),
                stroke = 9.dp,
                track = cs.primary.copy(alpha = 0.15f),
            ) {
                Text("$done/$total", fontWeight = FontWeight.Bold, color = cs.onPrimaryContainer)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    when {
                        total == 0 -> "Rest day"
                        done == total -> "All done 🎉"
                        else -> "${total - done} to go"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = cs.onPrimaryContainer,
                )
                if (isToday && settings.showLikelihood && profile.followThrough != null) {
                    Text(
                        "🎯 ${pct(profile.followThrough)} follow-through odds",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onPrimaryContainer,
                    )
                }
                Text(
                    "⚡ Willpower ${profile.willpower}  ·  Lv ${profile.level} ${profile.rank.emoji} ${profile.rank.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onPrimaryContainer.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitCard(
    h: Habit,
    date: LocalDate,
    today: LocalDate,
    state: AppState,
    settings: Settings,
    likelihood: Float?,
    reorder: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onValue: (LocalDate) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val color = habitColor(h.color)
    val done = h.isDone(date)
    val scheduled = h.isScheduled(date)
    val streak = Stats.currentStreak(h, today)
    var menu by remember { mutableStateOf(false) }
    val container by animateColorAsState(
        if (done && h.type != HabitType.QUIT) lerp(cs.surfaceContainerLow, color, 0.14f) else cs.surfaceContainerLow,
        label = "card",
    )
    val subtitle = buildList {
        if (!scheduled) add("Rest day")
        if (h.type != HabitType.CHECK) add(h.progressText(date))
        if (streak > 0) add(if (h.type == HabitType.QUIT) "🧊 $streak clean" else "🔥 $streak")
        if (settings.showLikelihood && date == today && scheduled && !(done && h.type != HabitType.QUIT)) {
            likelihood?.let { add("${pct(it)} likely") }
        }
        if (isEmpty()) add(if (done) "Done" else "Not yet")
    }.joinToString("  ·  ")

    Card(
        Modifier.fillMaxWidth().alpha(if (scheduled) 1f else 0.6f),
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(20.dp),
    ) {
        Box {
            Column(
                Modifier
                    .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                    .padding(if (settings.compact) 12.dp else 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiBadge(h.emoji, color, if (settings.compact) 38.dp else 46.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            h.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (reorder) {
                        IconButton(onClick = { state.move(h.id, -1) }) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                        IconButton(onClick = { state.move(h.id, 1) }) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                    } else {
                        QuickAction(h, date, today, state, color, onValue)
                    }
                }
                if (h.type == HabitType.COUNT || h.type == HabitType.TIMER || h.type == HabitType.NUMBER) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { h.progress(date) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = color,
                        trackColor = color.copy(alpha = 0.15f),
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
                if (settings.showWeekRow && !settings.compact) {
                    Spacer(Modifier.height(12.dp))
                    WeekDots(h, date, color) { d ->
                        if (h.type == HabitType.CHECK || h.type == HabitType.QUIT) state.toggle(h.id, d) else onValue(d)
                    }
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = { menu = false; onEdit() },
                )
                DropdownMenuItem(
                    text = { Text("Set value for ${friendlyDate(date, today).lowercase()}") },
                    leadingIcon = { Icon(Icons.Default.Create, null) },
                    onClick = { menu = false; onValue(date) },
                )
                DropdownMenuItem(
                    text = { Text("Archive") },
                    leadingIcon = { Icon(ArchiveIcon, null) },
                    onClick = { menu = false; state.setArchived(h.id, true) },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun QuickAction(
    h: Habit,
    date: LocalDate,
    today: LocalDate,
    state: AppState,
    color: Color,
    onValue: (LocalDate) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val filled = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = onColorFor(color))
    when (h.type) {
        HabitType.CHECK -> {
            val done = h.isDone(date)
            val bg by animateColorAsState(if (done) color else cs.surfaceVariant, label = "chk")
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(bg).clickable { state.toggle(h.id, date) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Check,
                    if (done) "Done" else "Mark done",
                    tint = if (done) onColorFor(color) else cs.onSurfaceVariant,
                )
            }
        }
        HabitType.COUNT -> Row(verticalAlignment = Alignment.CenterVertically) {
            if (h.value(date) > 0) {
                FilledTonalIconButton(onClick = { state.increment(h.id, date, -1.0) }, modifier = Modifier.size(38.dp)) {
                    Text("−", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
            }
            FilledIconButton(onClick = { state.increment(h.id, date, 1.0) }, colors = filled) {
                Icon(Icons.Default.Add, "Add one")
            }
        }
        HabitType.TIMER -> {
            if (date == today) {
                val running = h.timerStartedAt != null
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(running) {
                    while (running) {
                        now = System.currentTimeMillis()
                        delay(1000)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (running) {
                        val secs = ((now - (h.timerStartedAt ?: now)) / 1000).coerceAtLeast(0)
                        Text(
                            "%d:%02d".format(secs / 60, secs % 60),
                            fontWeight = FontWeight.Bold,
                            color = color,
                            fontSize = 16.sp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    FilledIconButton(
                        onClick = { if (running) state.stopTimer(h.id) else state.startTimer(h.id) },
                        colors = filled,
                    ) {
                        Icon(if (running) PauseIcon else Icons.Default.PlayArrow, if (running) "Stop timer" else "Start timer")
                    }
                }
            } else {
                FilledTonalIconButton(onClick = { onValue(date) }) { Icon(Icons.Default.Edit, "Set minutes") }
            }
        }
        HabitType.NUMBER -> FilledIconButton(onClick = { onValue(date) }, colors = filled) {
            Icon(if (h.value(date) > 0) Icons.Default.Edit else Icons.Default.Add, "Log amount")
        }
        HabitType.QUIT -> Row(verticalAlignment = Alignment.CenterVertically) {
            if (h.value(date) > 0) {
                FilledTonalIconButton(onClick = { state.increment(h.id, date, -1.0) }, modifier = Modifier.size(38.dp)) {
                    Text("−", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
            }
            FilledTonalButton(onClick = { state.increment(h.id, date, 1.0) }) { Text("Slipped") }
        }
    }
}

@Composable
fun WeekDots(h: Habit, endDate: LocalDate, color: Color, onTap: (LocalDate) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (offset in 6 downTo 0) {
            val d = endDate.minusDays(offset.toLong())
            val sched = h.isScheduled(d)
            val p = h.progress(d)
            val hasEntry = h.value(d) > 0
            val isEnd = offset == 0
            val fill = when {
                h.type == HabitType.QUIT && hasEntry -> Color(0xFFE53935)
                h.type == HabitType.QUIT -> if (d.isBefore(h.startDate)) cs.surfaceVariant else color.copy(alpha = 0.55f)
                p >= 1f -> color
                p > 0f -> color.copy(alpha = 0.25f + 0.5f * p)
                else -> cs.surfaceVariant
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .size(32.dp)
                        .alpha(if (sched || hasEntry) 1f else 0.35f)
                        .clip(CircleShape)
                        .background(fill)
                        .then(if (isEnd) Modifier.border(2.dp, color, CircleShape) else Modifier)
                        .clickable { onTap(d) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        d.dayOfMonth.toString(),
                        fontSize = 12.sp,
                        fontWeight = if (isEnd) FontWeight.Bold else FontWeight.Normal,
                        color = if (p >= 1f && h.type != HabitType.QUIT) onColorFor(color) else cs.onSurface,
                    )
                }
            }
        }
    }
}

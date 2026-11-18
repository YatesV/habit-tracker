package com.zerorizz.habits

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
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
    var viewProof by remember { mutableStateOf<Pair<Long, LocalDate>?>(null) }
    var confirmDelete by remember { mutableStateOf<Habit?>(null) }

    val active = data.activeHabits
    val allTags = active.flatMap { it.tags }.distinct().sorted()
    val shown = active.filter { tagFilter == null || tagFilter in it.tags }
    val scheduled = active.filter { it.isScheduled(selected) }
    val done = scheduled.count { it.isDone(selected) }
    val likelihoods = remember(data, today) { active.associate { it.id to Stats.likelihood(it, today) } }
    val profile = remember(data, today) { Stats.profile(data, today) }

    fun newHabit() {
        if (Pro.canAddHabits(data)) nav.open(Route.HabitEdit(null)) else nav.open(Route.Paywall)
    }

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
                onClick = { newHabit() },
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
            if (data.addon(Addons.MOTIVATION) && selected == today) {
                item(key = "motivation") { MotivationCard(today, Modifier.animateItem()) }
            }
            item(key = "summary") {
                SummaryCard(done, scheduled.size, selected == today, profile, settings) { nav.switchTab(3) }
            }
            if (data.addon(Addons.MOOD)) {
                item(key = "mood") {
                    MoodCard(data.moods[selected], Modifier.animateItem()) { m ->
                        state.setMood(selected, if (data.moods[selected] == m) null else m)
                    }
                }
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
                    EmptyState("🌱", "No habits yet", "Tap \"New habit\", or grab a ready-made pack from Add-ons in Settings.")
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
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp).animateItem(),
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
                        modifier = Modifier.animateItem(),
                        onOpen = { nav.open(Route.HabitDetail(h.id)) },
                        onEdit = { nav.open(Route.HabitEdit(h.id)) },
                        onDelete = { confirmDelete = h },
                        onValue = { d -> valueFor = h.id to d },
                        onViewProof = { d -> viewProof = h.id to d },
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
    viewProof?.let { (id, d) ->
        state.habit(id)?.let { h -> ProofViewer(state, h, d) { viewProof = null } }
    }
    confirmDelete?.let { h ->
        ConfirmDialog(
            "Delete \"${h.name}\"?",
            "This removes the habit, its history and its proof photos. Archive it instead if you might want it back.",
            "Delete",
            onDismiss = { confirmDelete = null },
        ) {
            state.delete(h.id)
            confirmDelete = null
        }
    }
}

@Composable
private fun MotivationCard(today: LocalDate, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("💬", fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Text(
            motivationFor(today),
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun MoodCard(mood: Int?, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("How are you feeling?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MOODS.forEachIndexed { i, e ->
                    val sel = mood == i + 1
                    val scale by animateFloatAsState(if (sel) 1.25f else if (mood == null) 1f else 0.85f, spring(0.4f), label = "mood")
                    Box(
                        Modifier
                            .size(48.dp)
                            .scale(scale)
                            .clip(CircleShape)
                            .background(if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { onPick(i + 1) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(e, fontSize = 26.sp, modifier = Modifier.alpha(if (mood == null || sel) 1f else 0.5f))
                    }
                }
            }
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
            val bg by animateColorAsState(if (isSel) cs.primary else cs.surfaceContainerHigh, label = "date")
            Column(
                Modifier
                    .width(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
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
        shape = RoundedCornerShape((settings.corner + 4).dp),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                if (total == 0) 0f else done.toFloat() / total,
                cs.primary,
                Modifier.size(76.dp),
                stroke = 9.dp,
                track = cs.primary.copy(alpha = 0.15f),
            ) {
                AnimatedContent(
                    targetState = done,
                    transitionSpec = {
                        if (targetState > initialState) (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                        else (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut())
                    },
                    label = "count",
                ) { n ->
                    Text("$n/$total", fontWeight = FontWeight.Bold, color = cs.onPrimaryContainer)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                AnimatedContent(
                    targetState = when {
                        total == 0 -> "Rest day"
                        done == total -> "All done 🎉"
                        else -> "${total - done} to go"
                    },
                    transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() },
                    label = "headline",
                ) { t ->
                    Text(t, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = cs.onPrimaryContainer)
                }
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
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onValue: (LocalDate) -> Unit,
    onViewProof: (LocalDate) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val color = habitColor(h.color)
    val done = h.isDone(date)
    val scheduled = h.isScheduled(date)
    val streak = Stats.currentStreak(h, today)
    val capture = LocalProofCapture.current
    var menu by remember { mutableStateOf(false) }
    val (colors, border) = habitCardColors(settings.cardStyle, color, done && h.type != HabitType.QUIT)
    val shape = RoundedCornerShape(settings.corner.dp)
    val subtitle = buildList {
        if (!scheduled) add("Rest day")
        if (h.awaitingProof(date)) add("📸 Proof needed")
        else if (h.type != HabitType.CHECK) add(h.progressText(date))
        if (streak > 0) add(if (h.type == HabitType.QUIT) "🧊 $streak clean" else "🔥 $streak")
        if (h.needsProof(date) && h.hasProof(date)) add("📸 Proven")
        if (settings.showLikelihood && date == today && scheduled && !(done && h.type != HabitType.QUIT)) {
            likelihood?.let { add("${pct(it)} likely") }
        }
        if (isEmpty()) add(if (done) "Done" else "Not yet")
    }.joinToString("  ·  ")

    /** Day-level tap used by the week row: proof habits go through the camera. */
    fun tapDay(d: LocalDate) {
        when {
            h.type == HabitType.CHECK && h.needsProof(d) && !h.isDone(d) -> capture.request(h.id, d)
            h.type == HabitType.CHECK || h.type == HabitType.QUIT -> state.toggle(h.id, d)
            else -> onValue(d)
        }
    }

    Card(
        modifier.fillMaxWidth().alpha(if (scheduled) 1f else 0.6f),
        colors = colors,
        border = border,
        shape = shape,
    ) {
        Box(
            if (settings.cardStyle == CardStyle.GRADIENT) Modifier.background(gradientBrush(color, done && h.type != HabitType.QUIT))
            else Modifier
        ) {
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
                            color = if (h.awaitingProof(date)) cs.error else cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (reorder) {
                        IconButton(onClick = { state.move(h.id, -1) }) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                        IconButton(onClick = { state.move(h.id, 1) }) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                    } else {
                        if (h.awaitingProof(date) && h.type != HabitType.CHECK) {
                            FilledTonalIconButton(onClick = { capture.request(h.id, date) }) { Icon(CameraIcon, "Add proof photo") }
                            Spacer(Modifier.width(4.dp))
                        }
                        QuickAction(h, date, today, state, color, onValue, onViewProof)
                    }
                }
                if (h.type == HabitType.COUNT || h.type == HabitType.TIMER || h.type == HabitType.NUMBER) {
                    Spacer(Modifier.height(10.dp))
                    val p by animateFloatAsState(h.progress(date), spring(dampingRatio = 0.7f), label = "prog")
                    LinearProgressIndicator(
                        progress = { p },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = color,
                        trackColor = color.copy(alpha = 0.15f),
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
                if (settings.showWeekRow && !settings.compact) {
                    Spacer(Modifier.height(12.dp))
                    WeekDots(h, date, color) { d -> tapDay(d) }
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
                if (h.requireProof && h.supportsProof) {
                    DropdownMenuItem(
                        text = { Text(if (h.hasProof(date)) "View proof" else "Add proof photo") },
                        leadingIcon = { Icon(CameraIcon, null) },
                        onClick = {
                            menu = false
                            if (h.hasProof(date)) onViewProof(date) else capture.request(h.id, date)
                        },
                    )
                }
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
    onViewProof: (LocalDate) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val filled = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = onColorFor(color))
    val capture = LocalProofCapture.current
    val (bounce, play) = rememberBounce()
    when (h.type) {
        HabitType.CHECK -> {
            val done = h.isDone(date)
            val needsPhoto = h.needsProof(date)
            val bg by animateColorAsState(if (done) color else cs.surfaceVariant, label = "chk")
            Box(
                Modifier
                    .bounce(bounce)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .clickable {
                        play()
                        when {
                            needsPhoto && !done -> capture.request(h.id, date)
                            needsPhoto && done -> onViewProof(date)
                            else -> state.toggle(h.id, date)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = done to needsPhoto,
                    transitionSpec = { scaleIn(spring(0.4f)) togetherWith fadeOut() },
                    label = "chkIcon",
                ) { (d, photo) ->
                    Icon(
                        if (photo && !d) CameraIcon else Icons.Default.Check,
                        when {
                            photo && !d -> "Take proof photo"
                            d -> "Done"
                            else -> "Mark done"
                        },
                        tint = if (d) onColorFor(color) else cs.onSurfaceVariant,
                    )
                }
            }
        }
        HabitType.COUNT -> Row(verticalAlignment = Alignment.CenterVertically) {
            if (h.value(date) > 0) {
                FilledTonalIconButton(onClick = { state.increment(h.id, date, -1.0) }, modifier = Modifier.size(38.dp)) {
                    Text("−", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
            }
            FilledIconButton(
                onClick = { play(); state.increment(h.id, date, 1.0) },
                colors = filled,
                modifier = Modifier.bounce(bounce),
            ) { Icon(Icons.Default.Add, "Add one") }
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
                        Text("%d:%02d".format(secs / 60, secs % 60), fontWeight = FontWeight.Bold, color = color, fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                    }
                    FilledIconButton(
                        onClick = { play(); if (running) state.stopTimer(h.id) else state.startTimer(h.id) },
                        colors = filled,
                        modifier = Modifier.bounce(bounce),
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
            val fill by animateColorAsState(
                when {
                    h.type == HabitType.QUIT && hasEntry -> Color(0xFFE53935)
                    h.type == HabitType.QUIT -> if (d.isBefore(h.startDate)) cs.surfaceVariant else color.copy(alpha = 0.55f)
                    h.isDone(d) -> color
                    p > 0f -> color.copy(alpha = 0.25f + 0.5f * p)
                    else -> cs.surfaceVariant
                },
                label = "dot",
            )
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
                        if (h.hasProof(d)) "📸" else d.dayOfMonth.toString(),
                        fontSize = if (h.hasProof(d)) 13.sp else 12.sp,
                        fontWeight = if (isEnd) FontWeight.Bold else FontWeight.Normal,
                        color = if (h.isDone(d) && h.type != HabitType.QUIT) onColorFor(color) else cs.onSurface,
                    )
                }
            }
        }
    }
}

package com.zerorizz.habits

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = HabitStore(this)
        setContent {
            HabitsTheme {
                HabitsScreen(store)
            }
        }
    }
}

@Composable
fun HabitsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(store: HabitStore) {
    var habits by remember { mutableStateOf(store.load()) }
    var showAdd by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Habit?>(null) }
    val today = LocalDate.now()

    fun update(newList: List<Habit>) {
        habits = newList
        store.save(newList)
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("Habits", fontWeight = FontWeight.Bold)
                        Text(
                            today.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New habit") },
            )
        },
    ) { padding ->
        if (habits.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No habits yet.\nTap \"New habit\" to add your first one.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val doneToday = habits.count { it.isDone(today) }
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    TodaySummary(doneToday, habits.size)
                }
                items(habits, key = { it.id }) { habit ->
                    HabitCard(
                        habit = habit,
                        today = today,
                        onToggle = { date ->
                            update(habits.map { if (it.id == habit.id) it.toggled(date) else it })
                        },
                        onLongPress = { toDelete = habit },
                    )
                }
                item {
                    Text(
                        "Tap a day to check it off. Long-press a habit to delete it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddHabitDialog(
            onDismiss = { showAdd = false },
            onAdd = { name ->
                update(habits + Habit(id = System.currentTimeMillis(), name = name))
                showAdd = false
            },
        )
    }

    toDelete?.let { habit ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            icon = { Icon(Icons.Default.Delete, null) },
            title = { Text("Delete \"${habit.name}\"?") },
            text = { Text("This removes the habit and its history.") },
            confirmButton = {
                TextButton(onClick = {
                    update(habits.filterNot { it.id == habit.id })
                    toDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
fun TodaySummary(done: Int, total: Int) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (done == total) "All done today 🎉" else "$done of $total done today",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else done.toFloat() / total },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitCard(
    habit: Habit,
    today: LocalDate,
    onToggle: (LocalDate) -> Unit,
    onLongPress: () -> Unit,
) {
    val streak = habit.currentStreak(today)
    val doneToday = habit.isDone(today)
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardDefaults.elevatedShape)
            .combinedClickable(onClick = { onToggle(today) }, onLongClick = onLongPress),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        habit.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        buildString {
                            append(if (streak > 0) "🔥 $streak day streak" else "No streak yet")
                            val best = habit.bestStreak()
                            if (best > streak) append("  ·  best $best")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val bg by animateColorAsState(
                    if (doneToday) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    label = "todayBg",
                )
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(bg)
                        .clickable { onToggle(today) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = if (doneToday) "Done today" else "Mark done today",
                        tint = if (doneToday) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            WeekRow(habit, today, onToggle)
        }
    }
}

@Composable
fun WeekRow(habit: Habit, today: LocalDate, onToggle: (LocalDate) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        for (offset in 6 downTo 0) {
            val day = today.minusDays(offset.toLong())
            val done = habit.isDone(day)
            val isToday = offset == 0
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (done) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .then(
                            if (isToday) Modifier.border(
                                2.dp, MaterialTheme.colorScheme.primary, CircleShape
                            ) else Modifier
                        )
                        .clickable { onToggle(day) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        day.dayOfMonth.toString(),
                        fontSize = 12.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (done) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun AddHabitDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New habit") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("e.g. Drink water, Gym, Read") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

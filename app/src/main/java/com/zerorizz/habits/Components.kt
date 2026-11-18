package com.zerorizz.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

sealed interface Route {
    data class HabitDetail(val id: Long) : Route
    data class HabitEdit(val id: Long?) : Route
    data class JournalEdit(val date: LocalDate) : Route
    data object Settings : Route
    data object Addons : Route
    data object Paywall : Route
}

class Nav(
    val open: (Route) -> Unit,
    val back: () -> Unit,
    val switchTab: (Int) -> Unit,
)

fun friendlyDate(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (d) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> d.format(DateTimeFormatter.ofPattern(if (d.year == today.year) "EEE, MMM d" else "MMM d, yyyy"))
}

@Composable
fun EmojiBadge(emoji: String, color: Color, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size.value * 0.46f).sp)
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, emoji: String? = null) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                (emoji?.let { "$it  " } ?: "") + label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** Grid of stat tiles, two per row. Each item: emoji, label, value. */
@Composable
fun StatGrid(items: List<Triple<String, String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (emoji, label, value) ->
                    StatTile(label, value, Modifier.weight(1f), emoji)
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                action?.invoke()
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun SegmentedToggle(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, o ->
            FilterChip(
                selected = i == selected,
                onClick = { onSelect(i) },
                label = { Text(o, maxLines = 1) },
            )
        }
    }
}

@Composable
fun ValueDialog(habit: Habit, date: LocalDate, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    val current = habit.value(date)
    var text by remember { mutableStateOf(if (current == 0.0) "" else trimNum(current)) }
    val label = when (habit.type) {
        HabitType.TIMER -> "Minutes"
        HabitType.QUIT -> "Slips"
        HabitType.CHECK -> "1 = done, 0 = not done"
        else -> habit.unit.ifBlank { "Amount" }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text(habit.emoji, fontSize = 28.sp) },
        title = { Text("${habit.name} · ${friendlyDate(date)}") },
        text = {
            Column {
                if (habit.type != HabitType.CHECK && habit.type != HabitType.QUIT) {
                    Text(
                        "Goal: ${habit.formatValue(habit.target)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { s -> text = s.filter { it.isDigit() || it == '.' } },
                    label = { Text(label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (habit.type != HabitType.CHECK) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val quick = when (habit.type) {
                            HabitType.TIMER -> listOf(5.0, 15.0, 30.0)
                            HabitType.QUIT -> listOf(1.0)
                            else -> listOf(1.0, 5.0)
                        }
                        quick.forEach { q ->
                            AssistChip(
                                onClick = { text = trimNum((text.toDoubleOrNull() ?: 0.0) + q) },
                                label = { Text("+${trimNum(q)}") },
                            )
                        }
                        if (habit.type != HabitType.QUIT) {
                            AssistChip(onClick = { text = trimNum(habit.target) }, label = { Text("Goal") })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.toDoubleOrNull() ?: 0.0) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave(0.0) }) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickDateDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val todayMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val st = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                st.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(st)
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun EmptyState(emoji: String, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(Color(0xFFFFB300), Color(0xFFFF6F00))
                )
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text("PRO", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White)
    }
}

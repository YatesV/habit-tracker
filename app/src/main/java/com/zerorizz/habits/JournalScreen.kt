package com.zerorizz.habits

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(state: AppState, nav: Nav, modifier: Modifier = Modifier) {
    val data = state.data
    val today = LocalDate.now()
    var query by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf<String?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    val allTags = data.journal.values.flatMap { extractTags(it) }.distinct().sorted()
    val entries = data.journal.entries
        .filter { (_, t) -> query.isBlank() || t.contains(query, ignoreCase = true) }
        .filter { (_, t) -> tag == null || tag in extractTags(t) }
        .sortedByDescending { it.key.toEpochDay() }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Journal", fontWeight = FontWeight.Bold) },
                actions = { IconButton(onClick = { pickDate = true }) { Icon(Icons.Default.DateRange, "Pick a day") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.open(Route.JournalEdit(today)) },
                icon = { Icon(Icons.Default.Edit, null) },
                text = { Text(if (data.journal.containsKey(today)) "Today's note" else "Write today") },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Search notes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (allTags.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(allTags) { t ->
                            FilterChip(selected = tag == t, onClick = { tag = if (tag == t) null else t }, label = { Text("#$t") })
                        }
                    }
                }
            }
            if (entries.isEmpty()) {
                item {
                    EmptyState(
                        "📓",
                        if (data.journal.isEmpty()) "Your journal is empty" else "No matches",
                        "Write daily notes in markdown. Link habits with [[Habit name]], add #tags and - [ ] checklists.",
                    )
                }
            }
            items(entries, key = { it.key.toEpochDay() }) { (date, text) ->
                ElevatedCard(Modifier.fillMaxWidth().clickable { nav.open(Route.JournalEdit(date)) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(friendlyDate(date, today), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stripMarkdown(text),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val links = extractLinks(text)
                        val tags = extractTags(text)
                        if (links.isNotEmpty() || tags.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                (links.map { "🔗 $it" } + tags.map { "#$it" }).joinToString("   "),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }

    if (pickDate) {
        PickDateDialog(today, onDismiss = { pickDate = false }) { d ->
            pickDate = false
            nav.open(Route.JournalEdit(d))
        }
    }
}

private fun journalTemplate(date: LocalDate, habits: List<Habit>): String = buildString {
    appendLine("# ${friendlyDate(date)}")
    appendLine()
    appendLine("## Wins")
    appendLine("- ")
    appendLine()
    appendLine("## To do")
    appendLine("- [ ] ")
    appendLine()
    appendLine("## Notes")
    if (habits.isNotEmpty()) append("Worked on [[${habits.first().name}]] ")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun JournalEditScreen(state: AppState, date: LocalDate, nav: Nav) {
    val saved = state.data.journal[date] ?: ""
    var value by remember { mutableStateOf(TextFieldValue(saved, TextRange(saved.length))) }
    var preview by remember { mutableStateOf(saved.isNotBlank()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val habits = state.data.habits.filter { !it.archived }

    fun persist() {
        if (value.text != (state.data.journal[date] ?: "")) state.setJournal(date, value.text)
    }
    LaunchedEffect(value.text) {
        delay(700)
        persist()
    }
    DisposableEffect(Unit) { onDispose { persist() } }
    BackHandler { persist(); nav.back() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { persist(); nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                title = { Text(friendlyDate(date)) },
                actions = {
                    TextButton(onClick = { preview = !preview }) { Text(if (preview) "Edit" else "Preview") }
                    if (value.text.isNotBlank()) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete note") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val doneHabits = habits.filter { it.isScheduled(date) && it.isDone(date) && it.type != HabitType.QUIT }
            if (doneHabits.isNotEmpty()) {
                Text("Done this day", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    doneHabits.forEach { h ->
                        AssistChip(
                            onClick = { nav.open(Route.HabitDetail(h.id)) },
                            label = { Text("${h.emoji} ${h.name}") },
                        )
                    }
                }
            }
            if (preview && value.text.isNotBlank()) {
                MarkdownView(
                    value.text,
                    onLink = { name -> state.data.habitByName(name)?.let { nav.open(Route.HabitDetail(it.id)) } },
                    onToggleTask = { line ->
                        val t = toggleTaskLine(value.text, line)
                        value = TextFieldValue(t, TextRange(t.length))
                    },
                )
            } else {
                if (value.text.isBlank()) {
                    AssistChip(
                        onClick = {
                            val t = journalTemplate(date, habits)
                            value = TextFieldValue(t, TextRange(t.length))
                        },
                        label = { Text("✨ Use daily template") },
                    )
                }
                MarkdownEditor(value, { value = it }, placeholder = "How did today go?", minLines = 12)
                if (habits.isNotEmpty()) {
                    Text("Link a habit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        habits.forEach { h ->
                            SuggestionChip(
                                onClick = { value = insertAtCursor(value, "[[${h.name}]] ") },
                                label = { Text("${h.emoji} ${h.name}") },
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog("Delete this note?", "This can't be undone.", "Delete", onDismiss = { confirmDelete = false }) {
            value = TextFieldValue("")
            state.setJournal(date, "")
            confirmDelete = false
            nav.back()
        }
    }
}

private fun insertAtCursor(v: TextFieldValue, s: String): TextFieldValue {
    val i = v.selection.max
    val t = v.text.substring(0, i) + s + v.text.substring(i)
    return TextFieldValue(t, TextRange(i + s.length))
}

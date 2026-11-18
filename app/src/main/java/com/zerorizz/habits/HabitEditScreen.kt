package com.zerorizz.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private val EMOJIS = listOf(
    "✅", "💧", "🏃", "📚", "🧘", "💪", "🛏️", "🥗", "🍎", "🚭", "🍺", "📵",
    "🎸", "✍️", "🧠", "💊", "🦷", "🧹", "💰", "🌞", "🚶", "🎮", "🙏", "❤️",
    "🐐", "🏋️", "🚴", "🎨", "🌱", "⏰", "🍬", "☕", "💻", "📖", "🎧", "🧊",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitEditScreen(state: AppState, id: Long?, nav: Nav) {
    val existing = id?.let { state.habit(it) }
    val allHabits = state.data.habits
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var emoji by remember { mutableStateOf(existing?.emoji ?: "✅") }
    var color by remember { mutableIntStateOf(existing?.color ?: (allHabits.size % HabitColors.size)) }
    var type by remember { mutableStateOf(existing?.type ?: HabitType.CHECK) }
    var target by remember { mutableStateOf(existing?.target?.let { trimNum(it) } ?: "") }
    var unit by remember { mutableStateOf(existing?.unit ?: "") }
    var days by remember { mutableStateOf(existing?.days ?: ALL_DAYS) }
    var tags by remember { mutableStateOf(existing?.tags ?: emptyList()) }
    var tagInput by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf(existing?.folder ?: "") }
    var note by remember { mutableStateOf(TextFieldValue(existing?.note ?: "")) }
    var confirmDelete by remember { mutableStateOf(false) }
    var requireProof by remember { mutableStateOf(existing?.requireProof ?: false) }
    var allowGallery by remember { mutableStateOf(existing?.allowGallery ?: false) }

    val needsTarget = type == HabitType.COUNT || type == HabitType.TIMER || type == HabitType.NUMBER
    val targetValue = target.toDoubleOrNull()
    val valid = name.isNotBlank() && days.isNotEmpty() && (!needsTarget || (targetValue != null && targetValue > 0))
    val knownTags = allHabits.flatMap { it.tags }.distinct().sorted()
    val knownFolders = allHabits.map { it.folder.trim() }.filter { it.isNotEmpty() }.distinct().sorted()

    fun save() {
        val h = (existing ?: Habit(id = System.currentTimeMillis(), name = name)).copy(
            name = name.trim(),
            emoji = emoji.ifBlank { "✅" },
            color = color,
            type = type,
            target = if (needsTarget) targetValue ?: 1.0 else 1.0,
            unit = unit.trim(),
            days = days,
            tags = tags,
            folder = folder.trim(),
            note = note.text,
            requireProof = requireProof && type != HabitType.QUIT,
            allowGallery = allowGallery,
            proofSince = when {
                !requireProof -> null
                existing?.requireProof == true -> existing.proofSince
                else -> java.time.LocalDate.now()
            },
        )
        state.upsert(h)
        nav.back()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text(if (existing == null) "New habit" else "Edit habit") },
                actions = {
                    TextButton(onClick = { save() }, enabled = valid) {
                        Icon(Icons.Default.Check, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Name + emoji preview
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge(emoji.ifBlank { "✅" }, habitColor(color), 56.dp)
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Habit name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Section("Type") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HabitType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text("${t.emoji} ${t.label}") },
                        )
                    }
                }
                Text(type.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (needsTarget) {
                Section("Daily goal") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = target,
                            onValueChange = { s -> target = s.filter { it.isDigit() || it == '.' } },
                            label = { Text(if (type == HabitType.TIMER) "Minutes" else "Goal") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        if (type != HabitType.TIMER) {
                            OutlinedTextField(
                                value = unit,
                                onValueChange = { unit = it },
                                label = { Text("Unit") },
                                placeholder = { Text(if (type == HabitType.COUNT) "glasses" else "km") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            if (type != HabitType.QUIT) {
                Section("Photo proof") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📸", fontSize = 24.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Require a photo", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "It only counts once you prove it, like a pic of your toothbrush.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(checked = requireProof, onCheckedChange = { requireProof = it })
                            }
                            if (requireProof) {
                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Allow gallery photos", fontWeight = FontWeight.Medium)
                                        Text(
                                            if (allowGallery) "Camera or gallery" else "Strict: camera only, so it has to be taken now",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Switch(checked = allowGallery, onCheckedChange = { allowGallery = it })
                                }
                                if (existing != null && !existing.requireProof) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Starts today. Past days stay as they are.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Section("Icon") {
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(8) },
                    label = { Text("Any emoji") },
                    singleLine = true,
                    modifier = Modifier.width(140.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EMOJIS.forEach { e ->
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (e == emoji) habitColor(color).copy(alpha = 0.3f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 22.sp) }
                    }
                }
            }

            Section("Color") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HabitColors.forEachIndexed { i, c ->
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(c)
                                .then(
                                    if (i == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { color = i },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (i == color) Icon(Icons.Default.Check, null, tint = onColorFor(c))
                        }
                    }
                }
            }

            Section("Schedule") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DayOfWeek.entries.forEach { dow ->
                        val on = dow.value in days
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (on) habitColor(color) else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { days = if (on) days - dow.value else days + dow.value },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                                fontWeight = FontWeight.Bold,
                                color = if (on) onColorFor(habitColor(color)) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { days = ALL_DAYS }, label = { Text("Every day") })
                    AssistChip(onClick = { days = setOf(1, 2, 3, 4, 5) }, label = { Text("Weekdays") })
                    AssistChip(onClick = { days = setOf(6, 7) }, label = { Text("Weekends") })
                }
            }

            Section("Folder") {
                OutlinedTextField(
                    value = folder,
                    onValueChange = { folder = it },
                    label = { Text("Folder (optional)") },
                    placeholder = { Text("Health, Morning routine…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (knownFolders.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        knownFolders.forEach { f ->
                            FilterChip(
                                selected = folder.trim() == f,
                                onClick = { folder = if (folder.trim() == f) "" else f },
                                label = { Text("📁 $f") },
                            )
                        }
                    }
                }
            }

            Section("Tags") {
                fun addTag() {
                    val t = tagInput.trim().removePrefix("#").replace(" ", "-")
                    if (t.isNotEmpty() && t !in tags) tags = tags + t
                    tagInput = ""
                }
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    label = { Text("Add a tag") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addTag() }),
                    trailingIcon = { TextButton(onClick = { addTag() }) { Text("Add") } },
                    modifier = Modifier.fillMaxWidth(),
                )
                val chips = (tags + knownTags).distinct()
                if (chips.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chips.forEach { t ->
                            val on = t in tags
                            FilterChip(
                                selected = on,
                                onClick = { tags = if (on) tags - t else tags + t },
                                label = { Text("#$t") },
                                trailingIcon = if (on) {
                                    { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) }
                                } else null,
                            )
                        }
                    }
                }
            }

            Section("Notes") {
                Text(
                    "Markdown works here: **bold**, - [ ] tasks, [[other habit]] links, #tags.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MarkdownEditor(note, { note = it }, placeholder = "Why this habit matters, rules, tips…", minLines = 4)
            }

            if (existing != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        state.setArchived(existing.id, !existing.archived)
                        nav.back()
                    }) { Text(if (existing.archived) "Unarchive" else "Archive") }
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Delete") }
                }
            }
            Button(onClick = { save() }, enabled = valid, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (existing == null) "Create habit" else "Save changes")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete && existing != null) {
        ConfirmDialog(
            "Delete \"${existing.name}\"?",
            "This removes the habit and all its history.",
            "Delete",
            onDismiss = { confirmDelete = false },
        ) {
            state.delete(existing.id)
            confirmDelete = false
            nav.back()
            nav.back()
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

package com.zerorizz.habits

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(state: AppState, nav: Nav) {
    val s = state.data.settings
    val ctx = LocalContext.current
    var pendingImport by remember { mutableStateOf<AppData?>(null) }
    var name by remember { mutableStateOf(s.userName) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = state.exportTo(uri)
            Toast.makeText(ctx, if (ok) "Backup saved" else "Couldn't save backup", Toast.LENGTH_SHORT).show()
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val d = state.readBackup(uri)
            if (d == null) Toast.makeText(ctx, "That file isn't a Habitify backup", Toast.LENGTH_SHORT).show()
            else pendingImport = d
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("Settings") },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard("Profile") {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        state.updateSettings { st -> st.copy(userName = it) }
                    },
                    label = { Text("Your name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard("Theme") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { m ->
                        FilterChip(
                            selected = s.theme == m,
                            onClick = { state.updateSettings { it.copy(theme = m) } },
                            label = { Text(m.label) },
                            leadingIcon = if (s.theme == m) {
                                { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                            } else null,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Accent color", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Material You (wallpaper) option
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Brush.sweepGradient(HabitColors.take(6) + HabitColors[0]))
                            .then(if (s.accent < 0) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { state.updateSettings { it.copy(accent = -1) } },
                        contentAlignment = Alignment.Center,
                    ) { if (s.accent < 0) Icon(Icons.Default.Check, null) }
                    HabitColors.forEachIndexed { i, c ->
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(c)
                                .then(if (s.accent == i) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { state.updateSettings { it.copy(accent = i) } },
                            contentAlignment = Alignment.Center,
                        ) { if (s.accent == i) Icon(Icons.Default.Check, null, tint = onColorFor(c)) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "The rainbow circle uses your wallpaper colors (Android 12+).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard("Layout") {
                ToggleRow("Compact cards", "Smaller habit cards, no week row", s.compact) { v -> state.updateSettings { it.copy(compact = v) } }
                ToggleRow("Show week row", "Last 7 days under each habit", s.showWeekRow) { v -> state.updateSettings { it.copy(showWeekRow = v) } }
                ToggleRow("Group by folder", "Show folders as sections on Today", s.groupByFolder) { v -> state.updateSettings { it.copy(groupByFolder = v) } }
                ToggleRow("Show odds", "Follow-through % on habits and summary", s.showLikelihood) { v -> state.updateSettings { it.copy(showLikelihood = v) } }
                ToggleRow("Week starts Monday", "For heatmaps", s.weekStartsMonday) { v -> state.updateSettings { it.copy(weekStartsMonday = v) } }
            }

            val archived = state.data.habits.filter { it.archived }
            SectionCard("Archived habits (${archived.size})") {
                if (archived.isEmpty()) {
                    Text(
                        "Long-press a habit on Today and pick Archive to hide it without losing its history.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                archived.forEach { h ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${h.emoji}  ${h.name}", Modifier.weight(1f))
                        TextButton(onClick = { state.setArchived(h.id, false) }) { Text("Restore") }
                    }
                }
            }

            SectionCard("Backup") {
                Text(
                    "Everything lives on this phone. Save a backup file before switching phones or reinstalling.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { exporter.launch("habitify-backup-${LocalDate.now()}.json") }) { Text("Export") }
                    OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "*/*")) }) { Text("Import") }
                }
            }

            Text(
                "Habitify · build ${BuildConfigLite.versionName(ctx)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    pendingImport?.let { d ->
        ConfirmDialog(
            "Replace everything?",
            "Import ${d.habits.size} habits and ${d.journal.size} journal entries. Your current data will be replaced.",
            "Import",
            onDismiss = { pendingImport = null },
        ) {
            state.replaceAll(d)
            pendingImport = null
            Toast.makeText(ctx, "Backup restored", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun ToggleRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

object BuildConfigLite {
    fun versionName(ctx: android.content.Context): String = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")
}

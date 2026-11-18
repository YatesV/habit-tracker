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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(state: AppState, nav: Nav) {
    val data = state.data
    val s = data.settings
    val ctx = LocalContext.current
    var pendingImport by remember { mutableStateOf<AppData?>(null) }
    var name by remember { mutableStateOf(s.userName) }
    var versionTaps by remember { mutableIntStateOf(0) }
    var hue by remember { mutableFloatStateOf(s.customAccent?.let { hueOf(Color(it)) } ?: 140f) }

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
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Pro banner
            Card(
                Modifier.fillMaxWidth().clickable { nav.open(Route.Paywall) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            ) {
                Row(
                    Modifier
                        .background(Brush.horizontalGradient(listOf(Color(0xFFFFB300), Color(0xFFFF6F00))))
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("👑", fontSize = 30.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (data.isPro) "Habitify Pro" else "Upgrade to Pro", fontWeight = FontWeight.Black, color = Color.White, fontSize = 18.sp)
                        Text(
                            if (data.isPro) "Active. Thanks for your support!" else "Unlimited habits, mood tracker, premium themes",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White)
                }
            }

            NavRow("🧩", "Add-ons & packs", "Celebrations, mood tracker, habit packs, make your own") { nav.open(Route.Addons) }

            SectionCard("Profile") {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(30)
                        state.updateSettings { st -> st.copy(userName = name) }
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
                Spacer(Modifier.height(14.dp))
                Text("Presets", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                ThemePresetGrid(state, THEME_PRESETS + data.customThemes) { nav.open(Route.Paywall) }

                Spacer(Modifier.height(16.dp))
                Text("Accent color", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HabitColors.forEachIndexed { i, c ->
                        val sel = s.customAccent == null && s.accent == i
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(c)
                                .then(if (sel) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { state.updateSettings { it.copy(accent = i, customAccent = null, themePreset = null) } },
                            contentAlignment = Alignment.Center,
                        ) { if (sel) Icon(Icons.Default.Check, null, tint = onColorFor(c), modifier = Modifier.size(18.dp)) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Custom color", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    if (!data.isPro) ProBadge()
                }
                val preview = Color.hsv(hue, 0.7f, 0.85f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(preview))
                    Spacer(Modifier.width(12.dp))
                    Slider(
                        value = hue,
                        onValueChange = { hue = it },
                        onValueChangeFinished = {
                            if (data.isPro) state.updateSettings { it.copy(customAccent = Color.hsv(hue, 0.7f, 0.85f).toArgb(), themePreset = null) }
                            else nav.open(Route.Paywall)
                        },
                        valueRange = 0f..360f,
                        colors = SliderDefaults.colors(thumbColor = preview, activeTrackColor = preview),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            SectionCard("Cards & text") {
                Text("Card style", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardStyle.entries.forEach { c ->
                        FilterChip(
                            selected = s.cardStyle == c,
                            onClick = { state.updateSettings { it.copy(cardStyle = c) } },
                            label = { Text(c.label) },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                var corner by remember { mutableFloatStateOf(s.corner.toFloat()) }
                Text("Corner roundness: ${corner.toInt()}", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = corner,
                    onValueChange = { corner = it },
                    onValueChangeFinished = { state.updateSettings { it.copy(corner = corner.toInt()) } },
                    valueRange = 0f..32f,
                    steps = 7,
                )
                Box(
                    Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(corner.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Text("Preview", color = MaterialTheme.colorScheme.onPrimaryContainer) }
                Spacer(Modifier.height(14.dp))
                Text("Text size", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Small" to 0.9f, "Normal" to 1f, "Large" to 1.15f).forEach { (label, v) ->
                        FilterChip(
                            selected = kotlin.math.abs(s.textScale - v) < 0.01f,
                            onClick = { state.updateSettings { it.copy(textScale = v) } },
                            label = { Text(label) },
                        )
                    }
                }
            }

            SectionCard("Layout") {
                ToggleRow("Compact cards", "Smaller habit cards, no week row", s.compact) { v -> state.updateSettings { it.copy(compact = v) } }
                ToggleRow("Show week row", "Last 7 days under each habit", s.showWeekRow) { v -> state.updateSettings { it.copy(showWeekRow = v) } }
                ToggleRow("Group by folder", "Show folders as sections on Today", s.groupByFolder) { v -> state.updateSettings { it.copy(groupByFolder = v) } }
                ToggleRow("Show odds", "Follow-through % on habits and summary", s.showLikelihood) { v -> state.updateSettings { it.copy(showLikelihood = v) } }
                ToggleRow("Week starts Monday", "For heatmaps", s.weekStartsMonday) { v -> state.updateSettings { it.copy(weekStartsMonday = v) } }
            }

            val archived = data.habits.filter { it.archived }
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
                        TextButton(onClick = {
                            if (Pro.canAddHabits(data)) state.setArchived(h.id, false) else nav.open(Route.Paywall)
                        }) { Text("Restore") }
                    }
                }
            }

            SectionCard("Backup") {
                Text(
                    "Everything lives on this phone. Save a backup before switching phones or reinstalling. Proof photos aren't included in the backup.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { exporter.launch("habitify-backup-${LocalDate.now()}.json") }) { Text("Export") }
                    OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "*/*")) }) { Text("Import") }
                }
            }

            NavRow("🔁", "Run setup again", "Go through the welcome flow again") {
                state.updateSettings { it.copy(onboarded = false) }
                nav.switchTab(0)
            }

            Text(
                "Habitify · build ${BuildConfigLite.versionName(ctx)}" + if (s.devUnlocked) " · dev Pro" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable {
                        // Hidden developer unlock, only in test (debug) builds
                        if (!BuildConfig.DEBUG) return@clickable
                        versionTaps++
                        if (versionTaps >= 7) {
                            versionTaps = 0
                            state.updateSettings { it.copy(devUnlocked = !it.devUnlocked) }
                            Toast.makeText(ctx, if (!s.devUnlocked) "Developer Pro unlocked" else "Developer Pro off", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(8.dp),
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

private fun hueOf(c: Color): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(c.toArgb(), hsv)
    return hsv[0]
}

@Composable
private fun NavRow(emoji: String, title: String, desc: String, onClick: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 24.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
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

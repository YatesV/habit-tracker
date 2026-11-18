package com.zerorizz.habits

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddonsScreen(state: AppState, nav: Nav) {
    val ctx = LocalContext.current
    val data = state.data
    var preview by remember { mutableStateOf<HabitPack?>(null) }
    var makePack by remember { mutableStateOf(false) }
    var exportJson by remember { mutableStateOf<String?>(null) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val p = state.readPack(uri)
            if (p == null) Toast.makeText(ctx, "That file isn't a Habitify pack", Toast.LENGTH_SHORT).show()
            else preview = p
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = exportJson
        if (uri != null && json != null) {
            Toast.makeText(ctx, if (state.writeTo(uri, json)) "Pack saved. Share the file with friends!" else "Couldn't save pack", Toast.LENGTH_SHORT).show()
        }
        exportJson = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("Add-ons & packs") },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard("🧩 Add-ons") {
                Addons.all.forEach { a -> AddonRow(state, a) { nav.open(Route.Paywall) } }
            }

            SectionCard("📦 Habit packs") {
                Text(
                    "Ready-made sets of habits. Tap one to preview and pick what to add.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                BUILT_IN_PACKS.forEach { p -> PackRow(p) { preview = p } }
            }

            if (data.packs.isNotEmpty()) {
                SectionCard("Installed community packs") {
                    data.packs.forEach { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(p.emoji, fontSize = 24.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Medium)
                                if (p.author.isNotBlank()) Text("by ${p.author}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { state.uninstallPack(p.id) }) { Text("Remove") }
                        }
                    }
                    Text(
                        "Removing a pack removes its themes. Habits you added stay.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard("🛠️ Make & share") {
                Text(
                    "Packs are small .json files with habits and themes. Make one from your habits and send it to friends, or import one someone made.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { if (data.isPro) importer.launch(arrayOf("application/json", "*/*")) else nav.open(Route.Paywall) }) {
                        Text("Import pack")
                    }
                    if (!data.isPro) ProBadge()
                    OutlinedButton(onClick = { makePack = true }, enabled = data.activeHabits.isNotEmpty()) { Text("Create pack") }
                }
            }
        }
    }

    preview?.let { p ->
        PackPreviewDialog(state, p, onDismiss = { preview = null }, onNeedPro = { preview = null; nav.open(Route.Paywall) }) { chosen ->
            state.installPack(p, chosen)
            Toast.makeText(ctx, "Added ${chosen.size} habit${if (chosen.size == 1) "" else "s"}" + if (p.themes.isNotEmpty()) " + ${p.themes.size} theme(s)" else "", Toast.LENGTH_SHORT).show()
            preview = null
        }
    }
    if (makePack) {
        CreatePackDialog(state, onDismiss = { makePack = false }) { pack ->
            makePack = false
            exportJson = pack.toJson().toString(2)
            exporter.launch(pack.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-') + ".habitify.json")
        }
    }
}

@Composable
private fun PackRow(p: HabitPack, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text(p.emoji, fontSize = 24.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, fontWeight = FontWeight.SemiBold)
            Text(
                p.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                p.habits.joinToString(" ") { it.emoji },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text("${p.habits.size}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PackPreviewDialog(
    state: AppState,
    pack: HabitPack,
    onDismiss: () -> Unit,
    onNeedPro: () -> Unit,
    onInstall: (List<Habit>) -> Unit,
) {
    val existing = state.data.habits.map { it.name.lowercase() }.toSet()
    var picked by remember { mutableStateOf(pack.habits.filter { it.name.lowercase() !in existing }.map { it.name }.toSet()) }
    val slots = Pro.slotsLeft(state.data)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text(pack.emoji, fontSize = 36.sp) },
        title = { Text(pack.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (pack.description.isNotBlank()) Text(pack.description, style = MaterialTheme.typography.bodyMedium)
                if (pack.author.isNotBlank()) Text("by ${pack.author}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                pack.habits.forEach { h ->
                    val on = h.name in picked
                    val dup = h.name.lowercase() in existing
                    Row(
                        Modifier.fillMaxWidth().clickable { picked = if (on) picked - h.name else picked + h.name }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text("${h.emoji} ${h.name}", Modifier.weight(1f))
                        if (h.requireProof) Text("📸")
                        if (dup) Text(" have it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (pack.themes.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Themes: " + pack.themes.joinToString { "${it.emoji} ${it.name}" }, style = MaterialTheme.typography.bodySmall)
                }
                if (picked.size > slots) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Free plan: you have room for $slots more habit${if (slots == 1) "" else "s"}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            if (picked.size > slots) {
                TextButton(onClick = onNeedPro) { Text("Go Pro") }
            } else {
                TextButton(
                    onClick = { onInstall(pack.habits.filter { it.name in picked }) },
                    enabled = picked.isNotEmpty() || pack.themes.isNotEmpty(),
                ) { Text("Add") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CreatePackDialog(state: AppState, onDismiss: () -> Unit, onCreate: (HabitPack) -> Unit) {
    val habits = state.data.activeHabits
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("📦") }
    var picked by remember { mutableStateOf(habits.map { it.id }.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create a pack") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(emoji, { emoji = it.take(8) }, Modifier.width(72.dp), singleLine = true, label = { Text("Icon") })
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(name, { name = it.take(40) }, Modifier.weight(1f), singleLine = true, label = { Text("Pack name") })
                }
                OutlinedTextField(desc, { desc = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("Description") })
                Spacer(Modifier.height(8.dp))
                habits.forEach { h ->
                    val on = h.id in picked
                    Row(
                        Modifier.fillMaxWidth().clickable { picked = if (on) picked - h.id else picked + h.id }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text("${h.emoji} ${h.name}")
                    }
                }
                Text(
                    "Only the habit setup is shared, never your history, notes or photos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && picked.isNotEmpty(),
                onClick = {
                    onCreate(
                        HabitPack(
                            id = "pack-" + System.currentTimeMillis(),
                            name = name.trim(),
                            emoji = emoji.ifBlank { "📦" },
                            description = desc.trim(),
                            author = state.data.settings.userName,
                            habits = habits.filter { it.id in picked }.map { it.copy(note = "") },
                        )
                    )
                },
            ) { Text("Save file") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

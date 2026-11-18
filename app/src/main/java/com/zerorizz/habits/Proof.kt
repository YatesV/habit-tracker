package com.zerorizz.habits

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

val CameraIcon: ImageVector = ImageVector.Builder("Camera", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(9f, 3f); lineTo(7.2f, 5f); horizontalLineTo(4f)
        curveTo(2.9f, 5f, 2f, 5.9f, 2f, 7f); verticalLineTo(19f)
        curveTo(2f, 20.1f, 2.9f, 21f, 4f, 21f); horizontalLineTo(20f)
        curveTo(21.1f, 21f, 22f, 20.1f, 22f, 19f); verticalLineTo(7f)
        curveTo(22f, 5.9f, 21.1f, 5f, 20f, 5f); horizontalLineTo(16.8f); lineTo(15f, 3f); close()
        moveTo(12f, 8f); curveTo(14.76f, 8f, 17f, 10.24f, 17f, 13f)
        curveTo(17f, 15.76f, 14.76f, 18f, 12f, 18f); curveTo(9.24f, 18f, 7f, 15.76f, 7f, 13f)
        curveTo(7f, 10.24f, 9.24f, 8f, 12f, 8f); close()
    }
}.build()

object ProofImages {
    private const val MAX_PX = 1600

    /** Reads an image, fixes rotation, shrinks it and saves it as a JPEG in [dest]. */
    fun saveScaled(ctx: Context, source: Uri, dest: File): Boolean = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > MAX_PX * 2 || bounds.outHeight / sample > MAX_PX * 2) sample *= 2
        val bmp = ctx.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return false
        val rotation = runCatching {
            ctx.contentResolver.openInputStream(source)?.use { s ->
                when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        val scale = minOf(1f, MAX_PX.toFloat() / maxOf(bmp.width, bmp.height))
        val m = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
        val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        FileOutputStream(dest).use { out.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        true
    }.getOrDefault(false)

    fun decode(file: File, maxPx: Int): ImageBitmap? = runCatching {
        if (!file.exists()) return null
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, b)
        var s = 1
        while (b.outWidth / (s * 2) >= maxPx && b.outHeight / (s * 2) >= maxPx) s *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
    }.getOrNull()
}

/** Starts the camera / photo picker and attaches the result as proof. */
class ProofCapture(val request: (habitId: Long, date: LocalDate) -> Unit)

val LocalProofCapture = staticCompositionLocalOf { ProofCapture { _, _ -> } }

@Composable
fun rememberProofCapture(state: AppState): ProofCapture {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // Survives the camera activity even if Android recreates ours
    var pendingHabit by rememberSaveable { mutableLongStateOf(-1L) }
    var pendingDay by rememberSaveable { mutableLongStateOf(0L) }
    var pendingFile by rememberSaveable { mutableStateOf("") }
    var chooseFor by remember { mutableStateOf<Habit?>(null) }

    fun finish(source: Uri?, cleanup: File?) {
        val id = pendingHabit
        val day = LocalDate.ofEpochDay(pendingDay)
        if (source == null || id < 0) { cleanup?.delete(); return }
        scope.launch {
            val name = "${id}_${day}_${System.currentTimeMillis()}.jpg"
            val dest = File(state.proofDir, name)
            val ok = withContext(Dispatchers.IO) { ProofImages.saveScaled(ctx, source, dest) }
            cleanup?.delete()
            if (ok) {
                state.attachProof(id, day, name)
                Toast.makeText(ctx, "Proof saved ✓", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(ctx, "Couldn't read that photo", Toast.LENGTH_SHORT).show()
            }
            pendingHabit = -1L
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = File(pendingFile)
        if (ok && f.exists() && f.length() > 0) {
            finish(Uri.fromFile(f), f)
        } else {
            f.delete()
            pendingHabit = -1L
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        finish(uri, null)
    }

    fun launchCamera() {
        val tmp = File(File(ctx.cacheDir, "capture").apply { mkdirs() }, "proof_${System.currentTimeMillis()}.jpg")
        pendingFile = tmp.path
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", tmp)
        runCatching { camera.launch(uri) }.onFailure {
            Toast.makeText(ctx, "No camera app found", Toast.LENGTH_SHORT).show()
        }
    }

    chooseFor?.let { h ->
        AlertDialog(
            onDismissRequest = { chooseFor = null },
            icon = { Text("📸", fontSize = 32.sp) },
            title = { Text("Prove it: ${h.name}") },
            text = { Text("Take a photo now, or pick one you already took.") },
            confirmButton = {
                TextButton(onClick = { chooseFor = null; launchCamera() }) { Text("Take photo") }
            },
            dismissButton = {
                TextButton(onClick = {
                    chooseFor = null
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Gallery") }
            },
        )
    }

    return remember {
        ProofCapture { id, date ->
            val h = state.habit(id) ?: return@ProofCapture
            pendingHabit = id
            pendingDay = date.toEpochDay()
            if (h.allowGallery) chooseFor = h else launchCamera()
        }
    }
}

@Composable
fun ProofImage(file: File, modifier: Modifier = Modifier, maxPx: Int = 400, contentScale: ContentScale = ContentScale.Crop) {
    val img by produceState<ImageBitmap?>(null, file.path, maxPx) {
        value = withContext(Dispatchers.IO) { ProofImages.decode(file, maxPx) }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        img?.let { Image(it, contentDescription = "Proof photo", modifier = Modifier.fillMaxSize(), contentScale = contentScale) }
            ?: Text("📷", fontSize = 20.sp)
    }
}

@Composable
fun ProofViewer(state: AppState, habit: Habit, date: LocalDate, onDismiss: () -> Unit) {
    val name = habit.proofs[date] ?: return
    val capture = LocalProofCapture.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(16.dp)) {
                Text("${habit.emoji} ${habit.name}", style = MaterialTheme.typography.titleLarge)
                Text(friendlyDate(date), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                ProofImage(
                    state.proofFile(name),
                    Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 480.dp).clip(RoundedCornerShape(16.dp)),
                    maxPx = 1200,
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { state.removeProof(habit.id, date); onDismiss() }) { Text("Remove") }
                    TextButton(onClick = { onDismiss(); capture.request(habit.id, date) }) { Text("Retake") }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

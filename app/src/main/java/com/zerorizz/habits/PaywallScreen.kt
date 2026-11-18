package com.zerorizz.habits

import android.app.Activity
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalBilling = staticCompositionLocalOf<BillingManager?> { null }

@Composable
fun PaywallScreen(state: AppState, nav: Nav) {
    val billing = LocalBilling.current
    val ctx = LocalContext.current
    val isPro = state.data.isPro
    val inf = rememberInfiniteTransition(label = "crown")
    val bob by inf.animateFloat(-6f, 6f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val shine by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "shine")

    Scaffold { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))
                Box(
                    Modifier
                        .size(120.dp)
                        .rotate(bob)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(Color(0xFFFFD54F), Color(0xFFFF8F00), Color(0xFFFFF176), Color(0xFFFFD54F)),
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) { Text("👑", fontSize = 60.sp, modifier = Modifier.scale(1f + 0.06f * kotlin.math.sin(shine * 6.283f))) }
                Spacer(Modifier.height(20.dp))
                Text(
                    if (isPro) "You're Pro 👑" else "Habitify Pro",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isPro) "Thanks for supporting Habitify. Everything is unlocked."
                    else "Go all-in on your habits.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Pro.perks.forEach { (e, t) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(e, fontSize = 22.sp)
                                Spacer(Modifier.width(14.dp))
                                Text(t, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))

                if (!isPro) {
                    when {
                        billing == null || billing.status == BillingManager.Status.UNAVAILABLE -> {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                Text(
                                    "Subscriptions aren't available in this version yet. They'll turn on once Habitify is on Google Play.",
                                    Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        billing.status == BillingManager.Status.CONNECTING -> CircularProgressIndicator()
                        else -> {
                            billing.offers.forEachIndexed { i, o ->
                                val modifier = Modifier.fillMaxWidth().height(58.dp)
                                val content: @Composable RowScope.() -> Unit = {
                                    Text("${o.label} · ${o.price}", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                }
                                if (i == 0) Button(onClick = { (ctx as? Activity)?.let { billing.buy(it, o) } }, modifier, content = content)
                                else OutlinedButton(onClick = { (ctx as? Activity)?.let { billing.buy(it, o) } }, modifier, content = content)
                                Spacer(Modifier.height(10.dp))
                            }
                            Text(
                                "Renews automatically. Cancel anytime in Google Play.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    billing?.message?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { billing?.restore() }, enabled = billing != null) { Text("Restore purchases") }
                } else {
                    Button(onClick = nav.back, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Awesome") }
                }
                Spacer(Modifier.height(24.dp))
            }
            IconButton(onClick = nav.back, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(Icons.Default.Close, "Close")
            }
        }
    }
}

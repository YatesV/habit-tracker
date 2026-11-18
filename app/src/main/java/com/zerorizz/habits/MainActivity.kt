package com.zerorizz.habits

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val state = AppState(applicationContext)
        val billing = BillingManager(applicationContext) { pro -> runOnUiThread { state.setPro(pro) } }
        billing.start()
        setContent {
            HabitifyTheme(state.data.settings) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CompositionLocalProvider(LocalBilling provides billing) {
                        HabitifyApp(state)
                    }
                }
            }
        }
    }
}

private data class Tab(val label: String, val icon: @Composable () -> Unit)

@Composable
fun HabitifyApp(state: AppState) {
    val capture = rememberProofCapture(state)
    CompositionLocalProvider(LocalProofCapture provides capture) {
        Box(Modifier.fillMaxSize()) {
            Crossfade(targetState = state.data.settings.onboarded, animationSpec = tween(500), label = "onboard") { onboarded ->
                if (onboarded) MainApp(state) else OnboardingScreen(state)
            }
            ConfettiOverlay(
                trigger = state.celebrateTick,
                big = state.celebrateBig,
                enabled = state.data.addon(Addons.CELEBRATIONS),
            )
        }
    }
}

@Composable
private fun MainApp(state: AppState) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var stack by remember { mutableStateOf(listOf<Route>()) }
    val nav = remember {
        Nav(
            open = { r -> stack = stack + r },
            back = { stack = stack.dropLast(1) },
            switchTab = { t -> stack = emptyList(); tab = t },
        )
    }
    BackHandler(enabled = stack.isNotEmpty()) { nav.back() }
    BackHandler(enabled = stack.isEmpty() && tab != 0) { tab = 0 }

    val tabs = listOf(
        Tab("Today") { Icon(Icons.Default.CheckCircle, null) },
        Tab("Stats") { Icon(BarsIcon, null) },
        Tab("Journal") { Icon(Icons.Default.Edit, null) },
        Tab("Profile") { Icon(Icons.Default.Star, null) },
    )

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = t.icon,
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { padding ->
            val m = Modifier.padding(bottom = padding.calculateBottomPadding())
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { dir * it / 10 }) togetherWith fadeOut(tween(150))
                },
                label = "tabs",
            ) { t ->
                when (t) {
                    0 -> TodayScreen(state, nav, m)
                    1 -> StatsScreen(state, nav, m)
                    2 -> JournalScreen(state, nav, m)
                    else -> ProfileScreen(state, nav, m)
                }
            }
        }

        AnimatedContent(
            targetState = stack,
            contentKey = { it.lastOrNull() },
            transitionSpec = {
                val top = targetState.lastOrNull() ?: initialState.lastOrNull()
                val forward = targetState.size >= initialState.size
                if (top == Route.Paywall) {
                    if (forward) (slideInVertically { it } + fadeIn()) togetherWith fadeOut()
                    else fadeIn() togetherWith (slideOutVertically { it } + fadeOut())
                } else if (forward) {
                    (slideInHorizontally(tween(320)) { it } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(320)) { -it / 4 } + fadeOut(tween(200)))
                } else {
                    (slideInHorizontally(tween(320)) { -it / 4 } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(320)) { it } + fadeOut(tween(200)))
                }
            },
            label = "route",
        ) { s ->
            val route = s.lastOrNull()
            if (route != null) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (route) {
                        is Route.HabitDetail -> HabitDetailScreen(state, route.id, nav)
                        is Route.HabitEdit -> HabitEditScreen(state, route.id, nav)
                        is Route.JournalEdit -> JournalEditScreen(state, route.date, nav)
                        Route.Settings -> SettingsScreen(state, nav)
                        Route.Addons -> AddonsScreen(state, nav)
                        Route.Paywall -> PaywallScreen(state, nav)
                    }
                }
            }
        }
    }
}

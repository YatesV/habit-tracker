package com.zerorizz.habits

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
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
        setContent {
            HabitifyTheme(state.data.settings) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    HabitifyApp(state)
                }
            }
        }
    }
}

private data class Tab(val label: String, val icon: @Composable () -> Unit)

@Composable
fun HabitifyApp(state: AppState) {
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
            when (tab) {
                0 -> TodayScreen(state, nav, m)
                1 -> StatsScreen(state, nav, m)
                2 -> JournalScreen(state, nav, m)
                else -> ProfileScreen(state, nav, m)
            }
        }

        AnimatedContent(
            targetState = stack.lastOrNull(),
            transitionSpec = {
                (fadeIn() + slideInHorizontally { it / 6 }) togetherWith fadeOut()
            },
            label = "route",
        ) { route ->
            if (route != null) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (route) {
                        is Route.HabitDetail -> HabitDetailScreen(state, route.id, nav)
                        is Route.HabitEdit -> HabitEditScreen(state, route.id, nav)
                        is Route.JournalEdit -> JournalEditScreen(state, route.date, nav)
                        Route.Settings -> SettingsScreen(state, nav)
                    }
                }
            }
        }
    }
}

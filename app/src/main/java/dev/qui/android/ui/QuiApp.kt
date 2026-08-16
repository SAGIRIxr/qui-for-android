/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Navigation shell. The bottom bar mirrors qui's MobileFooterNav: Dashboard,
 * a client/instance entry with a live count badge, and Settings.
 */

package dev.qui.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import dev.qui.android.ui.addintent.AddIntent
import dev.qui.android.ui.dashboard.DashboardScreen
import dev.qui.android.ui.detail.TorrentDetailScreen
import dev.qui.android.ui.login.LoginScreen
import dev.qui.android.ui.settings.SettingsScreen
import dev.qui.android.ui.torrents.TorrentsScreen
import kotlinx.coroutines.flow.MutableStateFlow

object Routes {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val TORRENTS = "torrents"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{instanceId}/{hash}"

    fun detail(instanceId: Int, hash: String) = "detail/$instanceId/$hash"
}

private data class NavEntry(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val NAV_ENTRIES = listOf(
    NavEntry(Routes.DASHBOARD, "Dashboard", Icons.Default.Home),
    NavEntry(Routes.TORRENTS, "Clients", Icons.Default.Storage),
    NavEntry(Routes.SETTINGS, "Settings", Icons.Default.Settings),
)

@Composable
fun QuiApp(pendingAdd: MutableStateFlow<AddIntent?>) {
    val root: RootViewModel = hiltViewModel()
    val isConfigured by root.isConfigured.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    // Null means the stored session has not been read yet; showing nothing avoids a
    // login flash for users who are already signed in.
    when (isConfigured) {
        null -> Box(Modifier.fillMaxSize())
        false -> LoginScreen(onAuthenticated = { /* isConfigured flips and swaps the tree */ })
        true -> MainScaffold(navController = navController, pendingAdd = pendingAdd)
    }
}

@Composable
private fun MainScaffold(
    navController: NavHostController,
    pendingAdd: MutableStateFlow<AddIntent?>,
) {
    val shell: ShellViewModel = hiltViewModel()
    val instances by shell.instances.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination

    // The detail screen is full-bleed in qui too; the bar would only crowd it.
    val showBottomBar = NAV_ENTRIES.any { entry ->
        currentRoute?.hierarchy?.any { it.route == entry.route } == true
    }

    LaunchedEffect(Unit) { shell.refresh() }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    NAV_ENTRIES.forEach { entry ->
                        val selected = currentRoute?.hierarchy?.any { it.route == entry.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(entry.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (entry.route == Routes.TORRENTS && instances.isNotEmpty()) {
                                    BadgedBox(badge = { Badge { Text("${instances.count { it.isActive }}") } }) {
                                        Icon(entry.icon, contentDescription = entry.label)
                                    }
                                } else {
                                    Icon(entry.icon, contentDescription = entry.label)
                                }
                            },
                            label = {
                                Text(
                                    entry.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TORRENTS,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onOpenInstance = { navController.navigate(Routes.TORRENTS) },
                )
            }
            composable(Routes.TORRENTS) {
                TorrentsScreen(
                    pendingAdd = pendingAdd,
                    onOpenTorrent = { instanceId, hash ->
                        navController.navigate(Routes.detail(instanceId, hash))
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(
                    navArgument("instanceId") { type = NavType.IntType },
                    navArgument("hash") { type = NavType.StringType },
                ),
            ) {
                TorrentDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

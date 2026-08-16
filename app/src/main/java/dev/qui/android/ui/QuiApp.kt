/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Navigation shell. The bottom bar mirrors qui's MobileFooterNav: Dashboard,
 * a client/instance entry with a live count badge, and Settings.
 */

package dev.qui.android.ui

import androidx.annotation.StringRes
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
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
import dev.qui.android.R
import dev.qui.android.ui.addintent.AddIntent
import dev.qui.android.ui.components.LocalTrackerIcons
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
    @StringRes val label: Int,
    val icon: ImageVector,
)

private val NAV_ENTRIES = listOf(
    NavEntry(Routes.DASHBOARD, R.string.nav_dashboard, Icons.Default.Home),
    NavEntry(Routes.TORRENTS, R.string.nav_clients, Icons.Default.Storage),
    NavEntry(Routes.SETTINGS, R.string.nav_settings, Icons.Default.Settings),
)

@Composable
fun QuiApp(
    pendingAdd: MutableStateFlow<AddIntent?>,
    pendingTorrent: MutableStateFlow<Pair<Int, String>?>,
) {
    val root: RootViewModel = hiltViewModel()
    val isConfigured by root.isConfigured.collectAsStateWithLifecycle()
    val trackerIcons by root.trackerIcons.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    LaunchedEffect(isConfigured) {
        if (isConfigured == true) root.loadTrackerIcons()
    }

    // Checked once per app start, and only when a newer release exists that the user
    // has not already told us to stop mentioning.
    val updatePrompt by root.updatePrompt.collectAsStateWithLifecycle()
    updatePrompt?.let { status ->
        UpdateDialog(
            status = status,
            onDismiss = root::dismissUpdatePrompt,
            onSkip = root::skipUpdate,
        )
    }

    // Shared so the nav bar and the torrent screen's action bar retract together.
    val mobileScroll = remember { MobileScrollState() }

    CompositionLocalProvider(
        LocalTrackerIcons provides trackerIcons,
        LocalMobileScroll provides mobileScroll,
    ) {
        // Null means the stored session has not been read yet; showing nothing avoids a
        // login flash for users who are already signed in.
        when (isConfigured) {
            null -> Box(Modifier.fillMaxSize())
            false -> LoginScreen(onAuthenticated = { /* isConfigured flips the tree */ })
            true -> MainScaffold(
                navController = navController,
                pendingAdd = pendingAdd,
                pendingTorrent = pendingTorrent,
            )
        }
    }
}

@Composable
private fun MainScaffold(
    navController: NavHostController,
    pendingAdd: MutableStateFlow<AddIntent?>,
    pendingTorrent: MutableStateFlow<Pair<Int, String>?>,
) {
    val shell: ShellViewModel = hiltViewModel()
    val instances by shell.instances.collectAsStateWithLifecycle()
    val currentInstanceName by shell.currentInstanceName.collectAsStateWithLifecycle()
    val unifiedScope by shell.unifiedScope.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination

    // The detail screen is full-bleed in qui too; the bar would only crowd it.
    val showBottomBar = NAV_ENTRIES.any { entry ->
        currentRoute?.hierarchy?.any { it.route == entry.route } == true
    }

    LaunchedEffect(Unit) { shell.refresh() }

    // A row tapped on the home-screen widget lands straight on that torrent.
    val widgetTorrent by pendingTorrent.collectAsStateWithLifecycle()
    LaunchedEffect(widgetTorrent) {
        val (instanceId, hash) = widgetTorrent ?: return@LaunchedEffect
        pendingTorrent.value = null
        navController.navigate(Routes.detail(instanceId, hash))
    }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar && LocalMobileScroll.current.barsVisible,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    NAV_ENTRIES.forEach { entry ->
                        val selected = currentRoute?.hierarchy?.any { it.route == entry.route } == true
                        val isClients = entry.route == Routes.TORRENTS
                        val activeCount = instances.count { it.isActive }
                        val description = stringResource(entry.label)
                        // qui names the middle tab after the client in scope; the
                        // generic word is only the fallback when nothing is active.
                        val label = when {
                            !isClients -> description
                            unifiedScope -> stringResource(R.string.scope_all_clients)
                            currentInstanceName != null -> currentInstanceName!!
                            instances.isEmpty() -> description
                            activeCount == 0 -> stringResource(R.string.nav_no_active_clients)
                            else -> description
                        }

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
                                if (isClients && instances.isNotEmpty()) {
                                    BadgedBox(badge = { Badge { Text("$activeCount") } }) {
                                        Icon(entry.icon, contentDescription = description)
                                    }
                                } else {
                                    Icon(entry.icon, contentDescription = description)
                                }
                            },
                            label = {
                                Text(
                                    text = label,
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

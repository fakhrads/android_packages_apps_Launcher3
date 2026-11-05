/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.settings.compose

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.SessionCommitReceiver
import com.android.launcher3.allapps.AppDrawerStyle
import com.android.launcher3.settings.compose.screens.AppDrawerSettingsScreen
import com.android.launcher3.settings.compose.screens.HomeScreenSettingsScreen
import com.android.launcher3.settings.compose.screens.HubScreen
import com.android.launcher3.settings.compose.screens.IconsSettingsScreen
import com.android.launcher3.settings.compose.screens.MiscSettingsScreen
import com.android.launcher3.settings.compose.screens.RecentsSettingsScreen
import com.android.launcher3.settings.compose.screens.SettingsSubscreen
import com.android.launcher3.settings.compose.theme.ZeroLauncherTheme

/**
 * Main Settings Activity for ZeroLauncher built on Material 3 Expressive & Jetpack Compose.
 * Adopts the Bento Grid modular design language from ZeroEngine on onyx.
 */
class ZeroSettingsActivity : ComponentActivity() {

    // Same side effects the legacy Settings* fragments ran on preference changes.
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == LauncherPrefs.APP_DRAWER_STYLE.sharedPrefKey &&
                AppDrawerStyle.isIos(AppDrawerStyle.get(this))) {
            // The iOS drawer only lives on the home screen, so new apps must land there.
            prefs.edit().putBoolean(SessionCommitReceiver.ADD_ICON_PREFERENCE_KEY, true).apply()
        }
        when (key) {
            in RESTART_KEYS ->
                LauncherAppState.INSTANCE.executeIfCreated { it.setNeedsRestart() }
            LauncherPrefs.DRAWER_LIST.sharedPrefKey ->
                try {
                    LauncherAppState.getInstance(this).model.rebindCallbacks("drawerListChanged")
                } catch (_: Exception) {
                    LauncherAppState.INSTANCE.executeIfCreated { it.setNeedsRestart() }
                }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LauncherPrefs.getPrefs(this).registerOnSharedPreferenceChangeListener(prefListener)

        val initialScreen = parseInitialScreen(intent?.getStringExtra("root_key"))

        setContent {
            ZeroLauncherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    ZeroSettingsApp(
                        initialScreen = initialScreen,
                        onFinish = { finish() }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LauncherPrefs.getPrefs(this).unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    private fun parseInitialScreen(rootKey: String?): SettingsSubscreen {
        return when (rootKey?.lowercase()) {
            "home_screen", "homescreen" -> SettingsSubscreen.HOMESCREEN
            "icons", "theming" -> SettingsSubscreen.ICONS
            "app_drawer", "drawer" -> SettingsSubscreen.APP_DRAWER
            "recents", "overview" -> SettingsSubscreen.RECENTS
            "misc", "miscellaneous" -> SettingsSubscreen.MISC
            else -> SettingsSubscreen.HUB
        }
    }

    private companion object {
        val RESTART_KEYS = setOf(
            // SettingsHomescreen
            LauncherPrefs.SHOW_HOTSEAT_BG,
            LauncherPrefs.HOTSEAT_OPACITY,
            LauncherPrefs.DOCK_SEARCH,
            LauncherPrefs.DOCK_THEME,
            LauncherPrefs.DOCK_MUSIC_SEARCH,
            LauncherPrefs.HOTSEAT_QSB_SPACE,
            LauncherPrefs.SHOW_STATUS_BAR,
            LauncherPrefs.SHORT_PARALLAX,
            LauncherPrefs.SINGLE_PAGE_CENTER,
            LauncherPrefs.DARK_STATUS_BAR,
            LauncherPrefs.SHOW_QUICKSPACE,
            LauncherPrefs.SHOW_QUICKSPACE_ALT,
            LauncherPrefs.SHOW_QUICKSPACE_CLOCK,
            LauncherPrefs.QUICKSPACE_CLOCK_COLOR,
            LauncherPrefs.SHOW_QUICKSPACE_PSONALITY,
            LauncherPrefs.SHOW_QUICKSPACE_NOWPLAYING,
            LauncherPrefs.SHOW_QUICKSPACE_WEATHER,
            LauncherPrefs.SHOW_QUICKSPACE_WEATHER_CITY,
            LauncherPrefs.SHOW_QUICKSPACE_WEATHER_TEXT,
            LauncherPrefs.AUTO_HIDE_DOTS,
            // SettingsAppDrawer
            LauncherPrefs.ALL_APPS_SEARCH_PLACEMENT,
            LauncherPrefs.DRAWER_SCROLLBAR,
            LauncherPrefs.DRAWER_SEARCH,
            LauncherPrefs.ALL_APPS_DARK_TEXT,
            LauncherPrefs.APP_DRAWER_STYLE,
            LauncherPrefs.APP_DRAWER_SORT_MODE,
            LauncherPrefs.APP_DRAWER_CUSTOM_COLOR_ENABLED,
            LauncherPrefs.APP_DRAWER_CUSTOM_COLOR_LIGHT,
            LauncherPrefs.APP_DRAWER_CUSTOM_COLOR_DARK,
            // SettingsRecents
            LauncherPrefs.RECENTS_MEMINFO,
            // SettingsMisc
            LauncherPrefs.BLUR_DEPTH,
        ).map { it.sharedPrefKey }.toSet()
    }
}

@Composable
fun ZeroSettingsApp(
    initialScreen: SettingsSubscreen,
    onFinish: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(initialScreen) }

    BackHandler {
        if (currentScreen != SettingsSubscreen.HUB) {
            currentScreen = SettingsSubscreen.HUB
        } else {
            onFinish()
        }
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            if (targetState != SettingsSubscreen.HUB) {
                // Navigate forward
                (slideInHorizontally { width -> width / 3 } + fadeIn())
                    .togetherWith(slideOutHorizontally { width -> -width / 3 } + fadeOut())
            } else {
                // Navigate back
                (slideInHorizontally { width -> -width / 3 } + fadeIn())
                    .togetherWith(slideOutHorizontally { width -> width / 3 } + fadeOut())
            }
        },
        label = "settings_navigation"
    ) { screen ->
        when (screen) {
            SettingsSubscreen.HUB -> {
                HubScreen(
                    onNavigate = { currentScreen = it },
                    modifier = Modifier
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            SettingsSubscreen.HOMESCREEN -> {
                HomeScreenSettingsScreen(
                    onBack = { currentScreen = SettingsSubscreen.HUB }
                )
            }
            SettingsSubscreen.ICONS -> {
                IconsSettingsScreen(
                    onBack = { currentScreen = SettingsSubscreen.HUB }
                )
            }
            SettingsSubscreen.APP_DRAWER -> {
                AppDrawerSettingsScreen(
                    onBack = { currentScreen = SettingsSubscreen.HUB }
                )
            }
            SettingsSubscreen.RECENTS -> {
                RecentsSettingsScreen(
                    onBack = { currentScreen = SettingsSubscreen.HUB }
                )
            }
            SettingsSubscreen.MISC -> {
                MiscSettingsScreen(
                    onBack = { currentScreen = SettingsSubscreen.HUB }
                )
            }
        }
    }
}

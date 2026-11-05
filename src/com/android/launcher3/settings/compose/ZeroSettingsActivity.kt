/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.settings.compose

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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

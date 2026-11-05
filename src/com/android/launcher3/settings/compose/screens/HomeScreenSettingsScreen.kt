/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.settings.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Feed
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.launcher3.settings.compose.components.ScreenScaffold
import com.android.launcher3.settings.compose.components.SettingSlider
import com.android.launcher3.settings.compose.components.SettingSwitch
import com.android.launcher3.settings.compose.components.SettingsCard
import com.android.launcher3.settings.compose.data.rememberBooleanPref
import com.android.launcher3.settings.compose.data.rememberIntPref

@Composable
fun HomeScreenSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    var lockLayout by rememberBooleanPref("pref_workspace_lock", false)
    var addIconToHome by rememberBooleanPref("pref_add_icon_to_home", true)
    var showStatusBar by rememberBooleanPref("pref_show_statusbar", true)
    var darkStatusBar by rememberBooleanPref("pref_dark_status_bar", false)
    var desktopLabels by rememberBooleanPref("pref_desktop_show_labels", true)

    var sleepGesture by rememberBooleanPref("pref_sleep_gesture", true)
    var sleepGestureHaptic by rememberBooleanPref("pref_sleep_gesture_haptic", true)

    var minusOne by rememberBooleanPref("pref_enable_minus_one", true)
    var dockSearch by rememberBooleanPref("pref_dock_search", true)
    var qsbOpacity by rememberIntPref("pref_qsb_outer_opacity", 100)
    var musicSearch by rememberBooleanPref("pref_dock_music_search", true)

    ScreenScaffold(
        title = "Homescreen",
        onBack = onBack
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsCard(
                title = "Desktop layout",
                icon = Icons.Rounded.Dashboard
            ) {
                SettingSwitch(
                    title = "Lock desktop layout",
                    subtitle = "Prevent moving or dropping icons and widgets",
                    checked = lockLayout,
                    onCheckedChange = { lockLayout = it },
                    icon = Icons.Rounded.Lock
                )
                SettingSwitch(
                    title = "Add icons to home",
                    subtitle = "For newly installed applications",
                    checked = addIconToHome,
                    onCheckedChange = { addIconToHome = it }
                )
                SettingSwitch(
                    title = "Show status bar",
                    subtitle = "Display system icons on the top",
                    checked = showStatusBar,
                    onCheckedChange = { showStatusBar = it },
                    icon = Icons.Rounded.Visibility
                )
                SettingSwitch(
                    title = "Dark status bar icons",
                    subtitle = "Force dark status bar glyphs on desktop",
                    checked = darkStatusBar,
                    onCheckedChange = { darkStatusBar = it },
                    enabled = showStatusBar
                )
                SettingSwitch(
                    title = "Show icon labels",
                    subtitle = "Display text under app icons on workspace",
                    checked = desktopLabels,
                    onCheckedChange = { desktopLabels = it }
                )
            }

            SettingsCard(
                title = "Gestures",
                icon = Icons.Rounded.TouchApp
            ) {
                SettingSwitch(
                    title = "Double tap to sleep",
                    subtitle = "Turn off screen by double tapping blank workspace",
                    checked = sleepGesture,
                    onCheckedChange = { sleepGesture = it }
                )
                SettingSwitch(
                    title = "Vibrate on sleep gesture",
                    subtitle = "Provide tactile feedback when sleep trigger activates",
                    checked = sleepGestureHaptic,
                    onCheckedChange = { sleepGestureHaptic = it },
                    enabled = sleepGesture
                )
            }

            SettingsCard(
                title = "Search & feed",
                icon = Icons.Rounded.Search
            ) {
                SettingSwitch(
                    title = "Google Feed page",
                    subtitle = "Swipe left to open Google Discover (-1 screen)",
                    checked = minusOne,
                    onCheckedChange = { minusOne = it },
                    icon = Icons.Rounded.Feed
                )
                SettingSwitch(
                    title = "Dock search bar",
                    subtitle = "Show Google search bar at the bottom hotseat",
                    checked = dockSearch,
                    onCheckedChange = { dockSearch = it }
                )
                SettingSlider(
                    title = "Search bar opacity",
                    value = qsbOpacity,
                    min = 0,
                    max = 100,
                    step = 5,
                    unit = "%",
                    onValueChange = { qsbOpacity = it },
                    enabled = dockSearch
                )
                SettingSwitch(
                    title = "Music recognition button",
                    subtitle = "Quick sound search on dock bar",
                    checked = musicSearch,
                    onCheckedChange = { musicSearch = it },
                    enabled = dockSearch
                )
            }
        }
    }
}

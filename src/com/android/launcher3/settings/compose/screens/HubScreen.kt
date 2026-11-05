/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.settings.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.launcher3.settings.compose.components.HeroOverviewCard
import com.android.launcher3.settings.compose.components.MetricTile
import com.android.launcher3.settings.compose.components.NavRow
import com.android.launcher3.settings.compose.components.QuickToggleTile
import com.android.launcher3.settings.compose.components.SettingsCard
import com.android.launcher3.settings.compose.components.StatusChip
import com.android.launcher3.settings.compose.data.LauncherPrefsBridge
import com.android.launcher3.customization.IconDatabase
import com.android.launcher3.settings.compose.data.rememberBooleanPref
import com.android.launcher3.settings.compose.data.rememberStringPref
import com.android.launcher3.settings.compose.data.rememberThemedIconsState

enum class SettingsSubscreen {
    HUB,
    HOMESCREEN,
    ICONS,
    APP_DRAWER,
    RECENTS,
    MISC
}

@Composable
fun HubScreen(
    onNavigate: (SettingsSubscreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // 2x2 Quick toggles
    var lockLayout by rememberBooleanPref(key = "pref_workspace_lock", defaultValue = false)
    var sleepGesture by rememberBooleanPref(key = "pref_sleep_gesture", defaultValue = true)
    var themedIcons by rememberThemedIconsState()
    var dockSearch by rememberBooleanPref(key = "pref_dock_search", defaultValue = true)

    val iconPack by rememberStringPref(IconDatabase.KEY_ICON_PACK, IconDatabase.VALUE_DEFAULT)
    val recentsStyle by rememberStringPref("pref_recents_style", "default")

    val gridText = remember { LauncherPrefsBridge.getGridSummary(context) }
    val iconPackText = remember(iconPack) { LauncherPrefsBridge.getIconPackSummary(context) }
    val recentsText = LauncherPrefsBridge.getRecentsStyleSummary(recentsStyle)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .navigationBarsPadding()
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Bento Hero Card: System Overview + Telemetry + Quick Restart
        HeroOverviewCard(
            title = "ZeroLauncher",
            subtitle = "Workspace & gesture engine",
            icon = Icons.Rounded.Home,
            trailingAction = {
                StatusChip(
                    label = "Restart",
                    icon = Icons.Rounded.Refresh,
                    onClick = { LauncherPrefsBridge.restart(context) }
                )
            },
            metricsContent = {
                MetricTile(
                    label = "Grid layout",
                    value = gridText,
                    icon = Icons.Rounded.GridView
                )
                MetricTile(
                    label = "Icon style",
                    value = if (themedIcons) "Themed" else "Stock",
                    icon = Icons.Rounded.Palette,
                    highlight = themedIcons
                )
                MetricTile(
                    label = "Recents",
                    value = recentsText,
                    icon = Icons.Rounded.Layers
                )
            },
            chipsContent = {
                StatusChip(label = "Onyx Edition", icon = Icons.Rounded.Widgets)
                StatusChip(label = "AOSP 17", icon = Icons.Rounded.Tune)
            }
        )

        // 2. Section: Quick Controls (2x2 Bento Grid)
        Text(
            text = "Quick controls",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickToggleTile(
                title = "Lock layout",
                subtitle = "Prevent moving items",
                checked = lockLayout,
                onCheckedChange = { lockLayout = it },
                icon = Icons.Rounded.Lock,
                modifier = Modifier.weight(1f)
            )
            QuickToggleTile(
                title = "Double tap sleep",
                subtitle = "Turn off screen",
                checked = sleepGesture,
                onCheckedChange = { sleepGesture = it },
                icon = Icons.Rounded.TouchApp,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickToggleTile(
                title = "Themed icons",
                subtitle = "Adaptive monochrome",
                checked = themedIcons,
                onCheckedChange = { themedIcons = it },
                icon = Icons.Rounded.Palette,
                modifier = Modifier.weight(1f)
            )
            QuickToggleTile(
                title = "Dock search",
                subtitle = "Bottom search bar",
                checked = dockSearch,
                onCheckedChange = { dockSearch = it },
                icon = Icons.Rounded.Search,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 3. Section: Bento Category Navigation
        SettingsCard(
            title = "Configuration",
            icon = Icons.Rounded.Settings
        ) {
            NavRow(
                title = "Homescreen",
                summary = "Layout, wallpaper, At A Glance, search bar, dock",
                icon = Icons.Rounded.Home,
                onClick = { onNavigate(SettingsSubscreen.HOMESCREEN) },
                badgeText = gridText
            )

            NavRow(
                title = "Icons & theming",
                summary = "Size scaling, icon packs, font scale",
                icon = Icons.Rounded.Palette,
                onClick = { onNavigate(SettingsSubscreen.ICONS) },
                badgeText = iconPackText
            )

            NavRow(
                title = "App drawer",
                summary = "Sorting, opacity, layout style, row height",
                icon = Icons.Rounded.Apps,
                onClick = { onNavigate(SettingsSubscreen.APP_DRAWER) }
            )

            NavRow(
                title = "Recents & multitasking",
                summary = "Overview style, freeform gestures, memory info",
                icon = Icons.Rounded.Layers,
                onClick = { onNavigate(SettingsSubscreen.RECENTS) },
                badgeText = recentsText
            )

            NavRow(
                title = "System & miscellaneous",
                summary = "Blur depth, burn-in protection, restart",
                icon = Icons.Rounded.Tune,
                onClick = { onNavigate(SettingsSubscreen.MISC) }
            )
        }
    }
}

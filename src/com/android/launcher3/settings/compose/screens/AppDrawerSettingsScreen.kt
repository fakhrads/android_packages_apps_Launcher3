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
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.settings.compose.components.ChoiceItem
import com.android.launcher3.settings.compose.components.ColorSettingRow
import com.android.launcher3.settings.compose.components.ChoiceList
import com.android.launcher3.settings.compose.components.NavRow
import com.android.launcher3.settings.compose.components.ScreenScaffold
import com.android.launcher3.settings.compose.components.SettingSlider
import com.android.launcher3.settings.compose.components.SettingSwitch
import com.android.launcher3.settings.compose.components.SettingsCard
import com.android.launcher3.settings.compose.data.LauncherPrefsBridge
import com.android.launcher3.settings.compose.data.rememberBooleanPref
import com.android.launcher3.settings.compose.data.rememberIntPref
import com.android.launcher3.settings.compose.data.rememberStringPref

@Composable
fun AppDrawerSettingsScreen(
    onBack: () -> Unit,
    onOpenHiddenApps: () -> Unit,
    onOpenDrawerTabs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val context = LocalContext.current
    var drawerStyle by rememberStringPref("pref_app_drawer_style", "normal")
    var sortingMode by rememberStringPref("pref_app_drawer_sorting_mode", "alphabetical")
    var searchPlacement by rememberStringPref("pref_allapps_search_placement", "top")
    var opacity by rememberIntPref("pref_app_drawer_opacity", 100)
    var rowHeight by rememberIntPref("pref_row_height", 100)

    var scrollbar by rememberBooleanPref("pref_drawer_scrollbar", true)
    var showLabels by rememberBooleanPref("pref_drawer_show_labels", true)
    var twoLineLabels by rememberBooleanPref("pref_enable_two_line_toggle", false)
    var openKeyboard by rememberBooleanPref("pref_drawer_open_keyboard", false)
    var darkText by rememberBooleanPref("pref_all_apps_dark_text", false)
    var defaultListView by rememberBooleanPref("pref_drawer_list", true)
    val autoOrganize = !defaultListView
    // Unset, this follows the older all apps themed icons pref (see LauncherPrefs).
    val iconCustomizationDefault = remember {
        LauncherPrefsBridge.getPrefs(context).getBoolean("pref_allapps_themed_icons", true)
    }
    var iconCustomization by rememberBooleanPref(
        "pref_allapps_icon_customization", iconCustomizationDefault
    )
    var customColor by rememberBooleanPref("pref_app_drawer_custom_color_enabled", false)
    var customColorLight by rememberIntPref("pref_app_drawer_custom_color_light", -1)
    var customColorDark by rememberIntPref("pref_app_drawer_custom_color_dark", -16777216)

    val styleChoices = listOf(
        ChoiceItem("normal", stringResource(R.string.drawer_style_normal)),
        ChoiceItem("fullscreen", stringResource(R.string.drawer_style_legacy)),
        ChoiceItem("horizontal_list", stringResource(R.string.drawer_style_list)),
        ChoiceItem("vertical", stringResource(R.string.drawer_style_oneui)),
        ChoiceItem("ios", stringResource(R.string.drawer_style_ios))
    )

    val sortChoices = listOf(
        ChoiceItem(
            value = "alphabetical",
            title = "Alphabetical (A–Z)",
            summary = "Standard application listing sorted by name",
            icon = Icons.Rounded.Sort
        ),
        ChoiceItem(
            value = "install_date",
            title = "Install date",
            summary = "Newest installed apps first",
            icon = Icons.Rounded.Schedule
        ),
        ChoiceItem(
            value = "usage",
            title = "Usage frequency",
            summary = "Prioritize apps you launch most often",
            icon = Icons.Rounded.Apps
        )
    )

    val searchChoices = listOf(
        ChoiceItem(
            value = "top",
            title = "Top bar",
            summary = "Classic Android application search placement",
            icon = Icons.Rounded.Search
        ),
        ChoiceItem(
            value = "bottom",
            title = "Bottom dock",
            summary = "Comfortable one-handed thumb reach",
            icon = Icons.Rounded.Search
        ),
        ChoiceItem(
            value = "hidden",
            title = "Hidden",
            summary = "No search bar in the app drawer",
            icon = Icons.Rounded.SearchOff
        )
    )

    ScreenScaffold(
        title = "App drawer",
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
                title = stringResource(R.string.drawer_style_title),
                icon = Icons.Rounded.Apps
            ) {
                ChoiceList(
                    choices = styleChoices,
                    selected = drawerStyle,
                    onSelect = { drawerStyle = it }
                )
            }

            SettingsCard(
                title = "Organization & layout",
                icon = Icons.Rounded.Category
            ) {
                SettingSwitch(
                    title = "Smart Auto-Organize",
                    subtitle = "Group apps into smart category folders (Games, Social, Media, Tools) at the top",
                    checked = autoOrganize,
                    onCheckedChange = { defaultListView = !it }
                )
                SettingSwitch(
                    title = stringResource(R.string.pref_icon_customization_title),
                    subtitle = stringResource(R.string.pref_icon_customization_summary),
                    checked = iconCustomization,
                    onCheckedChange = { iconCustomization = it }
                )
                val hiddenCount = LauncherPrefs.get(context).get(LauncherPrefs.HIDDEN_APPS).size
                NavRow(
                    title = "Hidden apps",
                    summary = if (hiddenCount == 0) "Keep apps out of the app drawer and its search"
                    else "$hiddenCount hidden from the app drawer and its search",
                    icon = Icons.Rounded.Visibility,
                    onClick = onOpenHiddenApps
                )
                val tabsOn = LauncherPrefs.get(context).get(LauncherPrefs.DRAWER_TABS)
                NavRow(
                    title = "Drawer tabs",
                    summary = if (tabsOn) "On: your own tabs and app categories above the list"
                    else "Off: group apps into tabs above the list",
                    icon = Icons.Rounded.Tab,
                    onClick = onOpenDrawerTabs
                )
            }

            SettingsCard(
                title = "App sorting",
                icon = Icons.Rounded.Sort
            ) {
                ChoiceList(
                    choices = sortChoices,
                    selected = sortingMode,
                    onSelect = { sortingMode = it }
                )
            }

            SettingsCard(
                title = "Search placement",
                icon = Icons.Rounded.Search
            ) {
                ChoiceList(
                    choices = searchChoices,
                    selected = searchPlacement,
                    onSelect = { searchPlacement = it }
                )
            }

            SettingsCard(
                title = "Dimensions & opacity",
                icon = Icons.Rounded.Tune
            ) {
                SettingSlider(
                    title = "Background opacity",
                    value = opacity,
                    min = 0,
                    max = 100,
                    step = 5,
                    unit = "%",
                    onValueChange = { opacity = it }
                )
                SettingSwitch(
                    title = stringResource(R.string.pref_app_drawer_custom_color_title),
                    subtitle = stringResource(R.string.pref_app_drawer_custom_color_summary),
                    checked = customColor,
                    onCheckedChange = { customColor = it }
                )
                ColorSettingRow(
                    title = stringResource(R.string.pref_app_drawer_custom_color_light_title),
                    subtitle = null,
                    color = customColorLight,
                    onColorChange = { customColorLight = it },
                    enabled = customColor
                )
                ColorSettingRow(
                    title = stringResource(R.string.pref_app_drawer_custom_color_dark_title),
                    subtitle = null,
                    color = customColorDark,
                    onColorChange = { customColorDark = it },
                    enabled = customColor
                )
                SettingSlider(
                    title = "Row vertical height",
                    value = rowHeight,
                    min = 75,
                    max = 150,
                    step = 5,
                    unit = "%",
                    onValueChange = { rowHeight = it }
                )
            }

            SettingsCard(
                title = "Behavior & typography",
                icon = Icons.Rounded.Palette
            ) {
                SettingSwitch(
                    title = "Fast scrollbar",
                    subtitle = "Alphabet index on right edge",
                    checked = scrollbar,
                    onCheckedChange = { scrollbar = it },
                    // The paged drawer has no vertical scrolling, hence no fast scroller.
                    enabled = drawerStyle != "vertical"
                )
                SettingSwitch(
                    title = "Show application labels",
                    subtitle = "Text under application icons",
                    checked = showLabels,
                    onCheckedChange = { showLabels = it }
                )
                SettingSwitch(
                    title = "Allow two-line labels",
                    subtitle = "Wrap long app names across two lines",
                    checked = twoLineLabels,
                    onCheckedChange = { twoLineLabels = it },
                    enabled = showLabels
                )
                SettingSwitch(
                    title = "Dark label text",
                    subtitle = "Force black text for high-contrast light backgrounds",
                    checked = darkText,
                    onCheckedChange = { darkText = it },
                    enabled = showLabels
                )
                SettingSwitch(
                    title = "Open keyboard on drawer swipe",
                    subtitle = "Instantly focus search field when opening drawer",
                    checked = openKeyboard,
                    onCheckedChange = { openKeyboard = it },
                    enabled = searchPlacement != "hidden" && drawerStyle != "ios",
                    icon = Icons.Rounded.Keyboard
                )
            }
        }
    }
}

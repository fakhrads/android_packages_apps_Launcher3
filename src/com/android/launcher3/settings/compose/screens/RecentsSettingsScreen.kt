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
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.ViewStream
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.launcher3.settings.compose.components.ChoiceItem
import com.android.launcher3.settings.compose.components.ChoiceList
import com.android.launcher3.settings.compose.components.ScreenScaffold
import com.android.launcher3.settings.compose.components.SettingSlider
import com.android.launcher3.settings.compose.components.SettingSwitch
import com.android.launcher3.settings.compose.components.SettingsCard
import com.android.launcher3.settings.compose.data.rememberBooleanPref
import com.android.launcher3.settings.compose.data.rememberIntPref
import com.android.launcher3.settings.compose.data.rememberStringPref

@Composable
fun RecentsSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    var recentsStyle by rememberStringPref("pref_recents_style", "default")
    var scrollVibrate by rememberBooleanPref("pref_recents_scroll_vibrate", true)
    var meminfoZram by rememberBooleanPref("pref_recents_meminfo_zram", true)
    var recentsOpacity by rememberIntPref("pref_recents_opacity", 100)
    var screenshotButton by rememberBooleanPref("pref_recents_screenshot_button", true)
    var clearAllButton by rememberBooleanPref("pref_recents_clear_all_button", true)

    var freeformGesture by rememberBooleanPref("pref_start_freeform_by_gesture", true)
    var freeformThreshold by rememberIntPref("pref_start_freeform_progress", 25)

    val styleChoices = listOf(
        ChoiceItem(
            value = "default",
            title = "Default stack",
            summary = "AOSP horizontal scrolling task carousel",
            icon = Icons.Rounded.ViewCarousel
        ),
        ChoiceItem(
            value = "ios",
            title = "iOS grid style",
            summary = "Overlapping card layout with smooth depth transitions",
            icon = Icons.Rounded.Layers
        ),
        ChoiceItem(
            value = "slim",
            title = "Slim minimal cards",
            summary = "Compact cards maximizing background visibility and multitasking speed",
            icon = Icons.Rounded.ViewStream
        )
    )

    ScreenScaffold(
        title = "Recents & overview",
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
                title = "Overview layout",
                icon = Icons.Rounded.Layers
            ) {
                ChoiceList(
                    choices = styleChoices,
                    selected = recentsStyle,
                    onSelect = { recentsStyle = it }
                )
            }

            SettingsCard(
                title = "Actions & telemetry",
                icon = Icons.Rounded.Memory
            ) {
                SettingSwitch(
                    title = "RAM & ZRAM telemetry",
                    subtitle = "Show memory usage in the recents bottom bar",
                    checked = meminfoZram,
                    onCheckedChange = { meminfoZram = it },
                    icon = Icons.Rounded.Memory
                )
                SettingSwitch(
                    title = "Haptic feedback on scroll",
                    subtitle = "Gentle tick when snapping to each recent task",
                    checked = scrollVibrate,
                    onCheckedChange = { scrollVibrate = it }
                )
                SettingSwitch(
                    title = "Quick screenshot action",
                    subtitle = "Capture current active task directly from overview",
                    checked = screenshotButton,
                    onCheckedChange = { screenshotButton = it },
                    icon = Icons.Rounded.Screenshot
                )
                SettingSwitch(
                    title = "Clear all button",
                    subtitle = "Show button to dismiss all recent tasks",
                    checked = clearAllButton,
                    onCheckedChange = { clearAllButton = it },
                    icon = Icons.Rounded.DeleteSweep
                )
                SettingSlider(
                    title = "Card background opacity",
                    value = recentsOpacity,
                    min = 0,
                    max = 100,
                    step = 5,
                    unit = "%",
                    onValueChange = { recentsOpacity = it }
                )
            }

            SettingsCard(
                title = "Freeform window gesture",
                icon = Icons.Rounded.OpenInNew
            ) {
                SettingSwitch(
                    title = "Swipe up to floating window",
                    subtitle = "Fling task card upward to launch in ZeroEngine freeform mode",
                    checked = freeformGesture,
                    onCheckedChange = { freeformGesture = it },
                    icon = Icons.Rounded.TouchApp
                )
                SettingSlider(
                    title = "Trigger distance threshold",
                    value = freeformThreshold,
                    min = 10,
                    max = 50,
                    step = 5,
                    unit = "%",
                    onValueChange = { freeformThreshold = it },
                    enabled = freeformGesture
                )
            }
        }
    }
}

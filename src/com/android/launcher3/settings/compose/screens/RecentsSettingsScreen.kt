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
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material.icons.rounded.ViewStream
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.launcher3.R
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
    var meminfo by rememberBooleanPref("pref_recents_meminfo", false)
    var meminfoZram by rememberBooleanPref("pref_recents_meminfo_zram", false)
    var recentsOpacity by rememberIntPref("pref_recents_opacity", 40)
    var screenshotButton by rememberBooleanPref("pref_recents_screenshot", true)
    var clearAllButton by rememberBooleanPref("pref_recents_clear_all", true)
    var clearAllPreserveActive by rememberBooleanPref("pref_recents_clear_all_preserve_active", false)
    var lensButton by rememberBooleanPref("pref_recents_lens", false)
    var swipeDownToLock by rememberBooleanPref("pref_swipe_down_to_lock", false)

    var freeformGesture by rememberBooleanPref("pref_start_freeform_by_gesture", false)
    var freeformThreshold by rememberIntPref("pref_start_freeform_progress", 20)
    var gestureAction by rememberStringPref("pref_overview_gesture_action", "freeform")
    var lpnhFullWidth by rememberBooleanPref("pref_lpnh_full_width", true)

    val gestureActionChoices = listOf(
        ChoiceItem(
            value = "freeform",
            title = stringResource(R.string.overview_gesture_action_freeform),
            icon = Icons.Rounded.OpenInNew
        ),
        ChoiceItem(
            value = "split_screen",
            title = stringResource(R.string.overview_gesture_action_split_screen),
            icon = Icons.Rounded.VerticalSplit
        )
    )

    val styleChoices = listOf(
        ChoiceItem(
            value = "default",
            title = stringResource(R.string.recents_style_default),
            summary = "AOSP horizontal scrolling task carousel",
            icon = Icons.Rounded.ViewCarousel
        ),
        ChoiceItem(
            value = "stock",
            title = stringResource(R.string.recents_style_stock),
            icon = Icons.Rounded.ViewStream
        ),
        ChoiceItem(
            value = "staple",
            title = stringResource(R.string.recents_style_staple),
            icon = Icons.Rounded.ViewStream
        ),
        ChoiceItem(
            value = "ios",
            title = stringResource(R.string.recents_style_ios),
            icon = Icons.Rounded.Layers
        ),
        ChoiceItem(
            value = "oxygen",
            title = stringResource(R.string.recents_style_oxygen),
            icon = Icons.Rounded.Layers
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
                title = "Circle to Search",
                icon = Icons.Rounded.Search
            ) {
                SettingSwitch(
                    title = "Hold anywhere on the nav bar",
                    subtitle = "Touch and hold any part of the bottom gesture bar, not only the handle",
                    checked = lpnhFullWidth,
                    onCheckedChange = { lpnhFullWidth = it },
                    icon = Icons.Rounded.TouchApp
                )
            }

            SettingsCard(
                title = "Actions & telemetry",
                icon = Icons.Rounded.Memory
            ) {
                SettingSwitch(
                    title = "Memory info",
                    subtitle = "Show RAM usage in the recents bottom bar",
                    checked = meminfo,
                    onCheckedChange = { meminfo = it },
                    icon = Icons.Rounded.Memory
                )
                SettingSwitch(
                    title = "ZRAM info",
                    subtitle = "Add ZRAM usage to the memory info",
                    checked = meminfoZram,
                    onCheckedChange = { meminfoZram = it },
                    enabled = meminfo
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
                SettingSwitch(
                    title = stringResource(R.string.recents_clear_all_preserve_active_title),
                    subtitle = stringResource(R.string.recents_clear_all_preserve_active_summary),
                    checked = clearAllPreserveActive,
                    onCheckedChange = { clearAllPreserveActive = it },
                    enabled = clearAllButton
                )
                SettingSwitch(
                    title = stringResource(R.string.recents_lens_title),
                    subtitle = null,
                    checked = lensButton,
                    onCheckedChange = { lensButton = it },
                    icon = Icons.Rounded.CenterFocusStrong
                )
                SettingSwitch(
                    title = stringResource(R.string.swipe_down_to_lock_title),
                    subtitle = stringResource(R.string.swipe_down_to_lock_summary),
                    checked = swipeDownToLock,
                    onCheckedChange = { swipeDownToLock = it },
                    icon = Icons.Rounded.Lock
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
                title = stringResource(R.string.overview_gesture_title),
                icon = Icons.Rounded.OpenInNew
            ) {
                SettingSwitch(
                    title = stringResource(R.string.overview_gesture_title),
                    subtitle = stringResource(R.string.overview_gesture_summary),
                    checked = freeformGesture,
                    onCheckedChange = { freeformGesture = it },
                    icon = Icons.Rounded.TouchApp
                )
                ChoiceList(
                    choices = gestureActionChoices,
                    selected = gestureAction,
                    onSelect = { gestureAction = it },
                    enabled = freeformGesture
                )
                SettingSlider(
                    title = stringResource(R.string.overview_gesture_threshold_title),
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

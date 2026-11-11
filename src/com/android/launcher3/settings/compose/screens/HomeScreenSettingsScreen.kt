/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.settings.compose.screens

import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Feed
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.touch.HomeGestureActions
import com.android.launcher3.settings.compose.components.ChoiceItem
import com.android.launcher3.settings.compose.components.ChoiceList
import com.android.launcher3.settings.compose.components.ColorSettingRow
import com.android.launcher3.settings.compose.components.NavRow
import com.android.launcher3.settings.compose.components.ScreenScaffold
import com.android.launcher3.settings.compose.components.SettingSlider
import com.android.launcher3.settings.compose.components.SettingSwitch
import com.android.launcher3.settings.compose.components.SettingsCard
import com.android.launcher3.settings.compose.data.rememberBooleanPref
import com.android.launcher3.settings.compose.data.rememberIntPref
import com.android.launcher3.settings.compose.data.rememberStringPref

private const val OMNIJAWS_PACKAGE = "org.omnirom.omnijaws"

@Composable
fun HomeScreenSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Feed, dock search and its options only work with Google installed as a system app.
    val gsaEnabled = remember { Utilities.isGSAEnabled(context) }
    val weatherIntent = remember {
        Intent(Intent.ACTION_MAIN).setComponent(
            ComponentName(OMNIJAWS_PACKAGE, "$OMNIJAWS_PACKAGE.SettingsActivity")
        ).takeIf { it.resolveActivity(context.packageManager) != null }
    }

    var lockLayout by rememberBooleanPref("pref_workspace_lock", false)
    var fixedLandscape by rememberBooleanPref("pref_fixed_landscape_mode", false)
    var addIconToHome by rememberBooleanPref("pref_add_icon_to_home", true)
    var showStatusBar by rememberBooleanPref("pref_show_statusbar", true)
    var darkStatusBar by rememberBooleanPref("pref_dark_status_bar", false)
    var topShadow by rememberBooleanPref("pref_show_top_shadow", true)
    var desktopLabels by rememberBooleanPref("pref_desktop_show_labels", true)
    var autoHideDots by rememberBooleanPref("pref_auto_hide_dots", true)
    val drawerStyle by rememberStringPref("pref_app_drawer_style", "normal")
    val iosDrawer = drawerStyle == "ios"

    var sleepGesture by rememberBooleanPref("pref_sleep_gesture", true)
    var sleepGestureHaptic by rememberBooleanPref("pref_sleep_gesture_haptic", true)
    var doubleTapAction by rememberStringPref("pref_double_tap_action", "sleep")
    var pinchAction by rememberStringPref("pref_pinch_action", "none")
    var iconTapHaptic by rememberBooleanPref("pref_icon_tap_haptic", true)

    var wallpaperCarousel by rememberBooleanPref("pref_wallpaper_carousel", true)
    var carouselLockscreen by rememberBooleanPref("pref_wallpaper_carousel_lockscreen", true)
    var wallpaperScrolling by rememberBooleanPref("pref_allow_wallpaper_scrolling", false)
    var wallpaperZooming by rememberBooleanPref("pref_allow_wallpaper_zooming", false)
    var shortParallax by rememberBooleanPref("pref_short_parallax", false)
    var singlePageCenter by rememberBooleanPref("pref_single_page_center", false)

    var quickspace by rememberBooleanPref("pref_quickspace", true)
    var quickspaceAlt by rememberBooleanPref("pref_quickspace_alt", false)
    var quickspaceClock by rememberBooleanPref("pref_quickspace_clock", true)
    var quickspaceClockColor by rememberIntPref("pref_quickspace_clock_color", 0xFFFF0000.toInt())
    var quickspacePsa by rememberBooleanPref("pref_quickspace_psonality", true)
    var quickspaceNowPlaying by rememberBooleanPref("pref_quickspace_np", true)
    var quickspaceGoogle by rememberBooleanPref("pref_quickspace_google", true)
    var weather by rememberBooleanPref("pref_quickspace_weather", true)
    var weatherCity by rememberBooleanPref("pref_quickspace_weather_city", false)
    var weatherText by rememberBooleanPref("pref_quickspace_weather_text", true)

    var minusOne by rememberBooleanPref("pref_enable_minus_one", true)
    var dockSearch by rememberBooleanPref("pref_dock_search", true)
    var qsbOpacity by rememberIntPref("pref_qsb_outer_opacity", 50)
    var qsbSpace by rememberIntPref("pref_hotseat_qsb_space", 22)
    var musicSearch by rememberBooleanPref("pref_dock_music_search", false)
    var dockTheme by rememberBooleanPref("pref_dock_theme", false)
    var hotseatBg by rememberBooleanPref("pref_show_hotseat_bg", false)
    var hotseatOpacity by rememberIntPref("pref_hotseat_opacity", 40)

    val notSystemSummary = stringResource(R.string.google_app_not_system_summary)

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
                // AOSP's fixed landscape mode: its own 8 x 3 grid, and the portrait grid comes
                // back when it is turned off (InvariantDeviceProfile keeps its name)
                SettingSwitch(
                    title = stringResource(R.string.landscape_mode_title),
                    subtitle = "Keep the home screen in landscape with its own layout. " +
                        "Your portrait layout comes back when this is off",
                    checked = fixedLandscape,
                    onCheckedChange = { fixedLandscape = it },
                    icon = Icons.Rounded.ScreenRotation
                )
                SettingSwitch(
                    title = stringResource(R.string.auto_add_shortcuts_label),
                    subtitle = stringResource(
                        if (iosDrawer) R.string.auto_add_shortcuts_forced_ios_summary
                        else R.string.auto_add_shortcuts_description
                    ),
                    checked = addIconToHome || iosDrawer,
                    onCheckedChange = { addIconToHome = it },
                    enabled = !iosDrawer
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
                    title = stringResource(R.string.show_top_shadow),
                    subtitle = null,
                    checked = topShadow,
                    onCheckedChange = { topShadow = it }
                )
                SettingSwitch(
                    title = "Show icon labels",
                    subtitle = "Display text under app icons on workspace",
                    checked = desktopLabels,
                    onCheckedChange = { desktopLabels = it }
                )
                SettingSwitch(
                    title = stringResource(R.string.auto_hide_dots),
                    subtitle = stringResource(R.string.auto_hide_dots_summary),
                    checked = autoHideDots,
                    onCheckedChange = { autoHideDots = it }
                )
            }

            SettingsCard(
                title = "Gestures",
                icon = Icons.Rounded.TouchApp
            ) {
                SettingSwitch(
                    title = "Double tap",
                    subtitle = "Double tap blank home screen space to run the action below",
                    checked = sleepGesture,
                    onCheckedChange = { sleepGesture = it }
                )
                ChoiceList(
                    choices = homeGestureChoices(withNone = false),
                    selected = doubleTapAction,
                    onSelect = { doubleTapAction = it },
                    enabled = sleepGesture
                )
                SettingSwitch(
                    title = "Vibrate on double tap",
                    subtitle = "Provide tactile feedback when the double tap runs",
                    checked = sleepGestureHaptic,
                    onCheckedChange = { sleepGestureHaptic = it },
                    enabled = sleepGesture
                )
                SettingSwitch(
                    title = "Haptic on icon tap",
                    subtitle = "Light vibration when opening an app or folder from home, " +
                        "dock or app drawer",
                    checked = iconTapHaptic,
                    onCheckedChange = { iconTapHaptic = it }
                )
            }

            SettingsCard(
                title = "Pinch in",
                icon = Icons.Rounded.TouchApp
            ) {
                ChoiceList(
                    choices = homeGestureChoices(withNone = true),
                    selected = pinchAction,
                    onSelect = { pinchAction = it }
                )
            }

            SettingsCard(
                title = "Wallpaper",
                icon = Icons.Rounded.Wallpaper
            ) {
                SettingSwitch(
                    title = stringResource(R.string.wallpaper_carousel_title),
                    subtitle = stringResource(R.string.wallpaper_carousel_summary),
                    checked = wallpaperCarousel,
                    onCheckedChange = { wallpaperCarousel = it }
                )
                SettingSwitch(
                    title = stringResource(R.string.wallpaper_carousel_lockscreen_title),
                    subtitle = stringResource(R.string.wallpaper_carousel_lockscreen_summary),
                    checked = carouselLockscreen,
                    onCheckedChange = { carouselLockscreen = it },
                    enabled = wallpaperCarousel
                )
                SettingSwitch(
                    title = stringResource(R.string.allow_wallpaper_scrolling),
                    subtitle = stringResource(R.string.allow_wallpaper_scrolling_summary),
                    checked = wallpaperScrolling,
                    onCheckedChange = { wallpaperScrolling = it }
                )
                SettingSwitch(
                    title = stringResource(R.string.allow_wallpaper_zooming),
                    subtitle = stringResource(R.string.allow_wallpaper_zooming_summary),
                    checked = wallpaperZooming,
                    onCheckedChange = { wallpaperZooming = it }
                )
                SettingSwitch(
                    title = stringResource(R.string.short_parallax_title),
                    subtitle = stringResource(R.string.short_parallax_summary),
                    checked = shortParallax,
                    onCheckedChange = { shortParallax = it }
                )
                SettingSwitch(
                    title = stringResource(R.string.single_page_center_title),
                    subtitle = stringResource(R.string.single_page_center_summary),
                    checked = singlePageCenter,
                    onCheckedChange = { singlePageCenter = it }
                )
            }

            SettingsCard(
                title = stringResource(R.string.quickspace_category_title),
                icon = Icons.Rounded.ViewDay
            ) {
                SettingSwitch(
                    title = stringResource(R.string.show_quickspace_title),
                    subtitle = stringResource(R.string.show_quickspace_summary),
                    checked = quickspace,
                    onCheckedChange = { quickspace = it }
                )
                SettingSwitch(
                    title = stringResource(R.string.quickspace_alternate_style_title),
                    subtitle = stringResource(R.string.quickspace_alternate_style_summary),
                    checked = quickspaceAlt,
                    onCheckedChange = { quickspaceAlt = it },
                    enabled = quickspace
                )
                SettingSwitch(
                    title = stringResource(R.string.quickspace_clock_title),
                    subtitle = stringResource(R.string.quickspace_clock_summary),
                    checked = quickspaceClock,
                    onCheckedChange = { quickspaceClock = it },
                    enabled = quickspace
                )
                ColorSettingRow(
                    title = stringResource(R.string.quickspace_clock_color_title),
                    subtitle = stringResource(R.string.quickspace_clock_color_summary),
                    color = quickspaceClockColor,
                    onColorChange = { quickspaceClockColor = it },
                    enabled = quickspace && quickspaceClock
                )
                SettingSwitch(
                    title = stringResource(R.string.quickspace_psa_title),
                    subtitle = stringResource(R.string.quickspace_psa_summary),
                    checked = quickspacePsa,
                    onCheckedChange = { quickspacePsa = it },
                    enabled = quickspace
                )
                SettingSwitch(
                    title = stringResource(R.string.quickspace_now_playing_title),
                    subtitle = stringResource(R.string.quickspace_now_playing_summary),
                    checked = quickspaceNowPlaying,
                    onCheckedChange = { quickspaceNowPlaying = it },
                    enabled = quickspace
                )
                SettingSwitch(
                    title = "At a Glance from Google",
                    subtitle = "Calendar, timers, weather and more from Android System " +
                        "Intelligence, as on Pixel. Falls back to the items above without it",
                    checked = quickspaceGoogle,
                    onCheckedChange = { quickspaceGoogle = it },
                    enabled = quickspace
                )
                weatherIntent?.let { intent ->
                    NavRow(
                        title = stringResource(R.string.weather_settings_title),
                        summary = stringResource(R.string.weather_settings_summary),
                        icon = Icons.Rounded.Cloud,
                        onClick = { context.startActivity(intent) }
                    )
                }
                SettingSwitch(
                    title = stringResource(R.string.quick_event_ambient_weather_title),
                    subtitle = stringResource(R.string.quick_event_ambient_weather_summary),
                    checked = weather,
                    onCheckedChange = { weather = it },
                    enabled = quickspace,
                    icon = Icons.Rounded.WbSunny
                )
                SettingSwitch(
                    title = stringResource(R.string.quick_event_weather_city_title),
                    subtitle = stringResource(R.string.quick_event_weather_city_summary),
                    checked = weatherCity,
                    onCheckedChange = { weatherCity = it },
                    enabled = quickspace && weather
                )
                SettingSwitch(
                    title = stringResource(R.string.quick_event_weather_text_title),
                    subtitle = stringResource(R.string.quick_event_weather_text_summary),
                    checked = weatherText,
                    onCheckedChange = { weatherText = it },
                    enabled = quickspace && weather
                )
            }

            SettingsCard(
                title = "Search & feed",
                icon = Icons.Rounded.Search
            ) {
                SettingSwitch(
                    title = "Google Feed page",
                    subtitle = if (gsaEnabled) "Swipe left to open Google Discover (-1 screen)"
                    else notSystemSummary,
                    checked = minusOne,
                    onCheckedChange = { minusOne = it },
                    enabled = gsaEnabled,
                    icon = Icons.Rounded.Feed
                )
                SettingSwitch(
                    title = "Dock search bar",
                    subtitle = if (gsaEnabled) "Show Google search bar at the bottom hotseat"
                    else notSystemSummary,
                    checked = dockSearch,
                    onCheckedChange = { dockSearch = it },
                    enabled = gsaEnabled
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
                SettingSlider(
                    title = stringResource(R.string.hotseat_qsb_space_title),
                    value = qsbSpace,
                    min = 10,
                    max = 100,
                    onValueChange = { qsbSpace = it },
                    enabled = dockSearch
                )
                SettingSwitch(
                    title = "Music recognition button",
                    subtitle = "Quick sound search on dock bar",
                    checked = musicSearch,
                    onCheckedChange = { musicSearch = it },
                    enabled = gsaEnabled && dockSearch
                )
                SettingSwitch(
                    title = stringResource(R.string.dock_theme_title),
                    subtitle = stringResource(R.string.dock_theme_summary),
                    checked = dockTheme,
                    onCheckedChange = { dockTheme = it },
                    enabled = gsaEnabled
                )
            }

            SettingsCard(
                title = "Dock",
                icon = Icons.Rounded.Dashboard
            ) {
                SettingSwitch(
                    title = stringResource(R.string.hotseat_bg_title),
                    subtitle = stringResource(R.string.hotseat_bg_summary),
                    checked = hotseatBg,
                    onCheckedChange = { hotseatBg = it }
                )
                SettingSlider(
                    title = stringResource(R.string.background_opacity_title),
                    value = hotseatOpacity,
                    min = 0,
                    max = 100,
                    step = 5,
                    unit = "%",
                    onValueChange = { hotseatOpacity = it },
                    enabled = hotseatBg
                )
            }
        }
    }
}

/** Actions of HomeGestureActions, for the double tap and the pinch. */
private fun homeGestureChoices(withNone: Boolean): List<ChoiceItem<String>> {
    val choices = ArrayList<ChoiceItem<String>>()
    if (withNone) {
        choices.add(ChoiceItem(HomeGestureActions.NONE, "Nothing", icon = Icons.Rounded.Block))
    }
    choices.add(ChoiceItem(HomeGestureActions.SLEEP, "Sleep", "Turn the screen off",
        Icons.Rounded.Bedtime))
    choices.add(ChoiceItem(HomeGestureActions.NOTIFICATIONS, "Notifications",
        icon = Icons.Rounded.Notifications))
    choices.add(ChoiceItem(HomeGestureActions.QUICK_SETTINGS, "Quick settings",
        icon = Icons.Rounded.Tune))
    choices.add(ChoiceItem(HomeGestureActions.APP_DRAWER, "App drawer", icon = Icons.Rounded.Apps))
    choices.add(ChoiceItem(HomeGestureActions.RECENTS, "Recents", icon = Icons.Rounded.Layers))
    return choices
}

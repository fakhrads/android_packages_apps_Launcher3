/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.settings.compose.data

import android.content.Context
import com.android.launcher3.R
import com.android.launcher3.settings.compose.screens.SettingsSubscreen

/** One thing that can be found from the search field of the settings hub. */
data class SettingsSearchEntry(
    val screen: SettingsSubscreen,
    /** The title of [screen], shown with the section. */
    val screenName: String,
    /** The card it sits in on that screen, shown with the screen name. */
    val section: String,
    val title: String,
    val summary: String? = null,
    /** Words people would type for it that the title and summary do not carry. */
    val keywords: String = ""
)

/**
 * The searchable list of the settings. Written by hand because the screens are plain Compose and
 * only exist once they are opened, so there is nothing to walk. A setting added to a screen is only
 * found here after it gets a line in this list. Strings come from the same resources the screens
 * use, so the search follows the language of the settings.
 */
object SettingsSearchIndex {

    /** The title of [screen], the same the screen shows in its top bar or hub row. */
    fun screenName(context: Context, screen: SettingsSubscreen): String = when (screen) {
        SettingsSubscreen.HOMESCREEN -> context.getString(R.string.zl_settings_homescreen)
        SettingsSubscreen.ICONS -> context.getString(R.string.zl_settings_icons)
        SettingsSubscreen.APP_DRAWER -> context.getString(R.string.zl_settings_app_drawer)
        SettingsSubscreen.RECENTS -> context.getString(R.string.zl_settings_recents)
        SettingsSubscreen.MISC -> context.getString(R.string.zl_settings_misc)
        SettingsSubscreen.HIDDEN_APPS -> context.getString(R.string.zl_settings_hidden_apps)
        SettingsSubscreen.DRAWER_TABS -> context.getString(R.string.zl_settings_drawer_tabs)
        SettingsSubscreen.DRAWER_TAB_APPS -> context.getString(R.string.zl_settings_drawer_tabs)
        SettingsSubscreen.HUB -> "ZeroLauncher"
    }

    fun build(context: Context): List<SettingsSearchEntry> {
        fun s(id: Int) = context.getString(id)
        val list = ArrayList<SettingsSearchEntry>()
        fun add(
            screen: SettingsSubscreen,
            section: String,
            title: String,
            summary: String? = null,
            keywords: String = ""
        ) {
            list.add(
                SettingsSearchEntry(
                    screen, screenName(context, screen), section, title, summary, keywords
                )
            )
        }

        val home = SettingsSubscreen.HOMESCREEN
        val desktop = s(R.string.zl_home_desktop_layout)
        add(home, desktop, s(R.string.zl_home_lock_layout),
            s(R.string.zl_home_lock_layout_summary), "lock layout freeze")
        add(home, desktop, s(R.string.landscape_mode_title),
            s(R.string.zl_home_landscape_summary), "rotation rotate fixed")
        add(home, desktop, s(R.string.auto_add_shortcuts_label),
            s(R.string.auto_add_shortcuts_description), "new apps install home")
        add(home, desktop, s(R.string.zl_home_status_bar), s(R.string.zl_home_status_bar_summary),
            "statusbar clock battery")
        add(home, desktop, s(R.string.zl_home_dark_status_bar),
            s(R.string.zl_home_dark_status_bar_summary), "light")
        add(home, desktop, s(R.string.show_top_shadow), null, "shadow")
        add(home, desktop, s(R.string.zl_home_icon_labels),
            s(R.string.zl_home_icon_labels_summary), "names text")
        add(home, desktop, s(R.string.auto_hide_dots), s(R.string.auto_hide_dots_summary),
            "page indicator")
        val gestures = s(R.string.zl_home_gestures)
        add(home, gestures, s(R.string.zl_gesture_double_tap),
            s(R.string.zl_home_double_tap_summary),
            "sleep lock screen off notifications quick settings drawer recents")
        add(home, gestures, s(R.string.zl_home_double_tap_haptic),
            s(R.string.zl_home_double_tap_haptic_summary), "haptic")
        val pinch = s(R.string.zl_home_pinch_in)
        add(home, pinch, pinch, s(R.string.zl_home_pinch_in_summary),
            "gesture zoom sleep notifications quick settings drawer recents")
        add(home, gestures, s(R.string.zl_home_icon_tap_haptic),
            s(R.string.zl_home_icon_tap_haptic_summary), "vibrate")
        add(home, gestures, s(R.string.zl_drawer_open_haptic_title),
            s(R.string.zl_drawer_open_haptic_summary), "vibrate all apps swipe")
        val wallpaper = s(R.string.zl_home_wallpaper)
        add(home, wallpaper, s(R.string.wallpaper_carousel_title),
            s(R.string.wallpaper_carousel_summary), "rotate change")
        add(home, wallpaper, s(R.string.wallpaper_carousel_lockscreen_title),
            s(R.string.wallpaper_carousel_lockscreen_summary), "rotate change")
        add(home, wallpaper, s(R.string.allow_wallpaper_scrolling),
            s(R.string.allow_wallpaper_scrolling_summary), "parallax")
        add(home, wallpaper, s(R.string.allow_wallpaper_zooming),
            s(R.string.allow_wallpaper_zooming_summary), "zoom")
        add(home, wallpaper, s(R.string.short_parallax_title),
            s(R.string.short_parallax_summary), "scroll")
        add(home, wallpaper, s(R.string.single_page_center_title),
            s(R.string.single_page_center_summary), "scroll")
        val quickspace = s(R.string.quickspace_category_title)
        add(home, quickspace, s(R.string.show_quickspace_title),
            s(R.string.show_quickspace_summary), "at a glance widget date")
        add(home, quickspace, s(R.string.quickspace_alternate_style_title),
            s(R.string.quickspace_alternate_style_summary), "at a glance")
        add(home, quickspace, s(R.string.quickspace_clock_title),
            s(R.string.quickspace_clock_summary), "time at a glance")
        add(home, quickspace, s(R.string.quickspace_clock_color_title),
            s(R.string.quickspace_clock_color_summary), "colour")
        add(home, quickspace, s(R.string.quickspace_psa_title),
            s(R.string.quickspace_psa_summary), "messages personality")
        add(home, quickspace, s(R.string.quickspace_now_playing_title),
            s(R.string.quickspace_now_playing_summary), "music song")
        add(home, quickspace, s(R.string.zl_home_glance_google),
            s(R.string.zl_home_glance_google_summary), "smartspace pixel google events")
        val hasOmniJaws =
            android.content.Intent(android.content.Intent.ACTION_MAIN)
                .setComponent(
                    android.content.ComponentName(
                        "org.omnirom.omnijaws",
                        "org.omnirom.omnijaws.SettingsActivity",
                    )
                )
                .let { intent ->
                    val info = context.packageManager.resolveActivity(intent, 0)?.activityInfo
                    info != null && info.exported
                }
        if (hasOmniJaws) {
            add(home, quickspace, s(R.string.weather_settings_title),
                s(R.string.weather_settings_summary), "location provider")
        }
        add(home, quickspace, s(R.string.quick_event_ambient_weather_title),
            s(R.string.quick_event_ambient_weather_summary), "temperature")
        add(home, quickspace, s(R.string.quick_event_weather_city_title),
            s(R.string.quick_event_weather_city_summary), "location")
        add(home, quickspace, s(R.string.quick_event_weather_text_title),
            s(R.string.quick_event_weather_text_summary), "condition")
        val searchFeed = s(R.string.zl_home_search_feed)
        add(home, searchFeed, s(R.string.zl_home_google_feed),
            s(R.string.zl_home_google_feed_summary), "discover news")
        add(home, searchFeed, s(R.string.zl_home_dock_search),
            s(R.string.zl_home_dock_search_summary), "qsb")
        add(home, searchFeed, s(R.string.zl_home_search_opens_drawer),
            s(R.string.zl_home_search_opens_drawer_on), "qsb pixel search google app")
        add(home, searchFeed, s(R.string.zl_home_search_opacity), null, "transparency qsb")
        add(home, searchFeed, s(R.string.hotseat_qsb_space_title), null, "qsb spacing")
        add(home, searchFeed, s(R.string.zl_home_music_search),
            s(R.string.zl_home_music_search_summary), "shazam song")
        add(home, searchFeed, s(R.string.dock_theme_title), s(R.string.dock_theme_summary),
            "theme")
        val dock = s(R.string.zl_home_dock)
        add(home, dock, s(R.string.zl_home_dock_style), s(R.string.zl_home_dock_style_summary),
            "dock style glass liquid blur ios 18 floating pill search")
        add(home, dock, s(R.string.hotseat_bg_title), s(R.string.hotseat_bg_summary), "hotseat")
        add(home, dock, s(R.string.background_opacity_title), null, "hotseat transparency")

        val icons = SettingsSubscreen.ICONS
        val shape = s(R.string.zl_icons_shape)
        add(icons, shape, shape, s(R.string.zl_icons_shape_summary),
            "mask squircle circle rounded square teardrop")
        val scale = s(R.string.zl_icons_scale)
        add(icons, scale, s(R.string.icon_size_title), null, "scale")
        add(icons, scale, s(R.string.zl_icons_label_size), null, "text scale")
        val adaptive = s(R.string.zl_icons_adaptive)
        add(icons, adaptive, s(R.string.icon_pack_title), s(R.string.zl_icons_pack_summary),
            "theme")
        add(icons, adaptive, s(R.string.zl_settings_themed_icons),
            s(R.string.zl_icons_themed_summary), "material you monochrome")
        add(icons, adaptive, s(R.string.pref_grayscale_icon_map_lawn_title),
            s(R.string.pref_grayscale_icon_map_lawn_summary), "themed lawnicons")
        add(icons, adaptive, s(R.string.notification_dots_title),
            s(R.string.zl_icons_dots_summary), "badge")

        val drawer = SettingsSubscreen.APP_DRAWER
        add(drawer, s(R.string.drawer_style_title), s(R.string.drawer_style_title), null,
            "layout list grid fullscreen ios oneui")
        val organization = s(R.string.zl_drawer_organization)
        add(drawer, organization, s(R.string.zl_drawer_auto_organize),
            s(R.string.zl_drawer_auto_organize_summary), "categories folders")
        val hiddenApps = s(R.string.zl_settings_hidden_apps)
        add(SettingsSubscreen.HIDDEN_APPS, hiddenApps, hiddenApps,
            s(R.string.zl_drawer_hidden_apps_summary), "hide apps private invisible")
        val tabs = s(R.string.zl_tabs_card)
        add(SettingsSubscreen.DRAWER_TABS, tabs, s(R.string.zl_tabs_show),
            s(R.string.zl_tabs_show_summary), "drawer tabs groups filter")
        add(SettingsSubscreen.DRAWER_TABS, tabs, s(R.string.zl_tabs_category),
            s(R.string.zl_tabs_category_summary), "drawer tabs categories games social")
        add(SettingsSubscreen.DRAWER_TABS, s(R.string.zl_tabs_yours), s(R.string.zl_tabs_add),
            s(R.string.zl_tabs_add_summary), "drawer tabs custom new create")
        add(drawer, organization, s(R.string.pref_icon_customization_title),
            s(R.string.pref_icon_customization_summary), "edit icon")
        val sorting = s(R.string.zl_drawer_sorting)
        add(drawer, sorting, s(R.string.zl_drawer_sort_alpha),
            s(R.string.zl_drawer_sort_alpha_summary), "sort order")
        add(drawer, sorting, s(R.string.zl_drawer_sort_install),
            s(R.string.zl_drawer_sort_install_summary), "sort order")
        add(drawer, sorting, s(R.string.zl_drawer_sort_usage),
            s(R.string.zl_drawer_sort_usage_summary), "sort order")
        val placement = s(R.string.zl_drawer_search_placement)
        add(drawer, placement, s(R.string.zl_drawer_search_top),
            s(R.string.zl_drawer_search_top_summary), "search")
        add(drawer, placement, s(R.string.zl_drawer_search_bottom),
            s(R.string.zl_drawer_search_bottom_summary), "search")
        add(drawer, placement, s(R.string.drawer_search_location_hidden),
            s(R.string.zl_drawer_search_hidden_summary), "search")
        val dimensions = s(R.string.zl_drawer_dimensions)
        add(drawer, dimensions, s(R.string.background_opacity_title), null, "transparency")
        add(drawer, dimensions, s(R.string.pref_app_drawer_custom_color_title),
            s(R.string.pref_app_drawer_custom_color_summary), "colour background")
        add(drawer, dimensions, s(R.string.zl_drawer_row_height), null, "spacing")
        val behavior = s(R.string.zl_drawer_behavior)
        add(drawer, behavior, s(R.string.zl_drawer_scrollbar),
            s(R.string.zl_drawer_scrollbar_summary), "scroll")
        add(drawer, behavior, s(R.string.zl_drawer_labels), s(R.string.zl_drawer_labels_summary),
            "names")
        add(drawer, behavior, s(R.string.zl_drawer_two_line_labels),
            s(R.string.zl_drawer_two_line_labels_summary), "names")
        add(drawer, behavior, s(R.string.zl_drawer_dark_text),
            s(R.string.zl_drawer_dark_text_summary), "colour")
        add(drawer, behavior, s(R.string.zl_drawer_open_keyboard),
            s(R.string.zl_drawer_open_keyboard_summary), "search")

        val recents = SettingsSubscreen.RECENTS
        val layout = s(R.string.zl_recents_layout)
        add(recents, layout, layout, null, "style carousel stock staple ios oxygen")
        add(recents, "Circle to Search", s(R.string.zl_recents_hold_nav),
            s(R.string.zl_recents_hold_nav_summary), "circle to search lens handle")
        val corner = s(R.string.zl_recents_corner_swipe)
        add(recents, corner, corner, s(R.string.zl_recents_corner_swipe_summary),
            "assistant gemini corner")
        add(recents, corner, s(R.string.zl_recents_assistant_app),
            s(R.string.zl_recents_assistant_app_summary), "assistant default")
        val actions = s(R.string.zl_recents_actions)
        add(recents, actions, s(R.string.recents_meminfo_title),
            s(R.string.zl_recents_meminfo_summary), "ram meminfo")
        add(recents, actions, s(R.string.recents_meminfo_zram_title),
            s(R.string.zl_recents_zram_summary), "swap zram")
        add(recents, actions, s(R.string.zl_recents_scroll_haptic),
            s(R.string.zl_recents_scroll_haptic_summary), "vibrate")
        add(recents, actions, s(R.string.zl_recents_screenshot),
            s(R.string.zl_recents_screenshot_summary), "screenshot chip")
        add(recents, actions, s(R.string.zl_recents_clear_all),
            s(R.string.zl_recents_clear_all_summary), "chip close apps")
        add(recents, actions, s(R.string.recents_clear_all_preserve_active_title),
            s(R.string.recents_clear_all_preserve_active_summary), "clear all")
        add(recents, actions, s(R.string.recents_select_title),
            s(R.string.zl_recents_select_summary), "select text copy chip")
        add(recents, actions, s(R.string.recents_lens_title), null, "google lens chip")
        add(recents, actions, s(R.string.swipe_down_to_lock_title),
            s(R.string.swipe_down_to_lock_summary), "lock app")
        add(recents, actions, s(R.string.zl_recents_card_opacity), null, "transparency")
        add(recents, s(R.string.overview_gesture_title), s(R.string.overview_gesture_title),
            s(R.string.overview_gesture_summary), "freeform split screen window")
        add(recents, s(R.string.overview_gesture_title),
            s(R.string.overview_gesture_threshold_title), null, "freeform split screen")

        val misc = SettingsSubscreen.MISC
        val motion = s(R.string.zl_misc_motion)
        add(misc, motion, motion, s(R.string.zl_misc_motion_summary), "animation spring style")
        add(misc, motion, s(R.string.zl_misc_response), null, "animation speed")
        add(misc, motion, s(R.string.zl_misc_bouncy), s(R.string.zl_misc_bouncy_summary),
            "overscroll spring")
        add(misc, motion, s(R.string.zl_misc_bounce_strength), null, "overscroll spring")
        add(misc, motion, s(R.string.zl_misc_animation_speed), null, "bounce")
        val suggestions = s(R.string.suggestion_pref_screen_title)
        add(misc, suggestions, suggestions, s(R.string.suggestion_pref_screen_desc),
            "predictions suggested apps")
        add(misc, s(R.string.zl_misc_render_effects), s(R.string.zl_misc_blur_radius), null,
            "blur depth")
        add(misc, s(R.string.zl_misc_transparency), s(R.string.zl_misc_translucency), null,
            "transparent opacity")
        val oled = s(R.string.zl_misc_oled)
        add(misc, oled, s(R.string.zl_misc_burn_in), s(R.string.zl_misc_burn_in_summary),
            "oled pill")
        add(misc, oled, s(R.string.zl_misc_burn_in_interval), null, "burn-in oled")
        val maintenance = s(R.string.zl_misc_maintenance)
        add(misc, maintenance, s(R.string.zl_misc_backup), s(R.string.zl_misc_backup_summary),
            "backup export save")
        add(misc, maintenance, s(R.string.zl_misc_restore), s(R.string.zl_misc_restore_summary),
            "restore import load")
        add(misc, maintenance, s(R.string.zl_misc_restart), s(R.string.zl_misc_restart_summary),
            "reload reboot")
        return list
    }

    /**
     * The entries that match every word of the query, the ones with the words in their title
     * first. Case does not matter.
     */
    fun search(entries: List<SettingsSearchEntry>, query: String): List<SettingsSearchEntry> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return entries
            .mapNotNull { entry ->
                val title = entry.title.lowercase()
                val rest = listOfNotNull(
                    entry.summary, entry.section, entry.keywords, entry.screenName
                ).joinToString(" ").lowercase()
                if (words.any { it !in title && it !in rest }) return@mapNotNull null
                val inTitle = words.count { it in title }
                val prefix = if (title.startsWith(words[0])) 1 else 0
                entry to (inTitle * 2 + prefix)
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }
}

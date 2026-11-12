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

    fun screenName(screen: SettingsSubscreen): String = when (screen) {
        SettingsSubscreen.HOMESCREEN -> "Homescreen"
        SettingsSubscreen.ICONS -> "Icons & theming"
        SettingsSubscreen.APP_DRAWER -> "App drawer"
        SettingsSubscreen.RECENTS -> "Recents & multitasking"
        SettingsSubscreen.MISC -> "System & miscellaneous"
        SettingsSubscreen.HIDDEN_APPS -> "Hidden apps"
        SettingsSubscreen.DRAWER_TABS -> "Drawer tabs"
        SettingsSubscreen.DRAWER_TAB_APPS -> "Drawer tabs"
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
            list.add(SettingsSearchEntry(screen, section, title, summary, keywords))
        }

        val home = SettingsSubscreen.HOMESCREEN
        add(home, "Desktop layout", "Lock desktop layout",
            "Prevent moving or dropping icons and widgets", "lock layout freeze")
        add(home, "Desktop layout", s(R.string.landscape_mode_title),
            "Keep the home screen in landscape with its own layout", "rotation rotate fixed")
        add(home, "Desktop layout", s(R.string.auto_add_shortcuts_label),
            s(R.string.auto_add_shortcuts_description), "new apps install home")
        add(home, "Desktop layout", "Show status bar", "Display system icons on the top",
            "statusbar clock battery")
        add(home, "Desktop layout", "Dark status bar icons",
            "Force dark status bar glyphs on desktop", "light")
        add(home, "Desktop layout", s(R.string.show_top_shadow), null, "shadow")
        add(home, "Desktop layout", "Show icon labels",
            "Display text under app icons on workspace", "names text")
        add(home, "Desktop layout", s(R.string.auto_hide_dots), s(R.string.auto_hide_dots_summary),
            "page indicator")
        add(home, "Gestures", "Double tap",
            "Double tap blank home screen space to run an action",
            "sleep lock screen off notifications quick settings drawer recents")
        add(home, "Gestures", "Vibrate on double tap",
            "Provide tactile feedback when the double tap runs", "haptic")
        add(home, "Pinch in", "Pinch in",
            "Pinch in on blank home screen space to run an action",
            "gesture zoom sleep notifications quick settings drawer recents")
        add(home, "Gestures", "Haptic on icon tap",
            "Light vibration when opening an app or folder from home", "vibrate")
        add(home, "Wallpaper", s(R.string.wallpaper_carousel_title),
            s(R.string.wallpaper_carousel_summary), "rotate change")
        add(home, "Wallpaper", s(R.string.wallpaper_carousel_lockscreen_title),
            s(R.string.wallpaper_carousel_lockscreen_summary), "rotate change")
        add(home, "Wallpaper", s(R.string.allow_wallpaper_scrolling),
            s(R.string.allow_wallpaper_scrolling_summary), "parallax")
        add(home, "Wallpaper", s(R.string.allow_wallpaper_zooming),
            s(R.string.allow_wallpaper_zooming_summary), "zoom")
        add(home, "Wallpaper", s(R.string.short_parallax_title),
            s(R.string.short_parallax_summary), "scroll")
        add(home, "Wallpaper", s(R.string.single_page_center_title),
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
        add(home, quickspace, "At a Glance from Google",
            "Calendar, timers, weather and more from Android System Intelligence",
            "smartspace pixel google events")
        add(home, quickspace, s(R.string.weather_settings_title),
            s(R.string.weather_settings_summary), "location provider")
        add(home, quickspace, s(R.string.quick_event_ambient_weather_title),
            s(R.string.quick_event_ambient_weather_summary), "temperature")
        add(home, quickspace, s(R.string.quick_event_weather_city_title),
            s(R.string.quick_event_weather_city_summary), "location")
        add(home, quickspace, s(R.string.quick_event_weather_text_title),
            s(R.string.quick_event_weather_text_summary), "condition")
        add(home, "Search & feed", "Google Feed page",
            "Swipe left to open Google Discover (-1 screen)", "discover news")
        add(home, "Search & feed", "Dock search bar", "Show Google search bar at the bottom hotseat",
            "qsb")
        add(home, "Search & feed", "Search bar opens drawer search",
            "Search apps, settings, shortcuts and contacts in the app drawer",
            "qsb pixel search google app")
        add(home, "Search & feed", "Search bar opacity", null, "transparency qsb")
        add(home, "Search & feed", s(R.string.hotseat_qsb_space_title), null, "qsb spacing")
        add(home, "Search & feed", "Music recognition button", "Quick sound search on dock bar",
            "shazam song")
        add(home, "Search & feed", s(R.string.dock_theme_title), s(R.string.dock_theme_summary),
            "theme")
        add(home, "Dock", s(R.string.hotseat_bg_title), s(R.string.hotseat_bg_summary), "hotseat")
        add(home, "Dock", s(R.string.background_opacity_title), null, "hotseat transparency")

        val icons = SettingsSubscreen.ICONS
        add(icons, "Icon shape", "Icon shape", "Circle, squircle, teardrop and more",
            "mask squircle circle rounded square teardrop")
        add(icons, "Scale & dimensions", "Icon size", null, "scale")
        add(icons, "Scale & dimensions", "Label font size", null, "text scale")
        add(icons, "Adaptive styling", s(R.string.icon_pack_title),
            "Apply icons from an installed icon pack", "theme")
        add(icons, "Adaptive styling", "Themed icons",
            "Monochrome icons tinted with the wallpaper colors", "material you monochrome")
        add(icons, "Adaptive styling", s(R.string.pref_grayscale_icon_map_lawn_title),
            s(R.string.pref_grayscale_icon_map_lawn_summary), "themed lawnicons")
        add(icons, "Adaptive styling", s(R.string.notification_dots_title),
            "Managed in system notification settings", "badge")

        val drawer = SettingsSubscreen.APP_DRAWER
        add(drawer, s(R.string.drawer_style_title), s(R.string.drawer_style_title), null,
            "layout list grid fullscreen ios oneui")
        add(drawer, "Organization & layout", "Smart Auto-Organize",
            "Group apps into smart category folders (Games, Social, Media, Tools) at the top",
            "categories folders")
        add(SettingsSubscreen.HIDDEN_APPS, "Hidden apps", "Hidden apps",
            "Keep apps out of the app drawer and its search", "hide apps private invisible")
        add(SettingsSubscreen.DRAWER_TABS, "Tabs", "Show tabs",
            "A row of tabs under the app drawer search bar", "drawer tabs groups filter")
        add(SettingsSubscreen.DRAWER_TABS, "Tabs", "Category tabs",
            "One more tab per app category", "drawer tabs categories games social")
        add(SettingsSubscreen.DRAWER_TABS, "Your tabs", "Add a tab",
            "Name it, then pick its apps", "drawer tabs custom new create")
        add(drawer, "Organization & layout", s(R.string.pref_icon_customization_title),
            s(R.string.pref_icon_customization_summary), "edit icon")
        add(drawer, "App sorting", "Alphabetical (A–Z)", "Standard application listing sorted by name",
            "sort order")
        add(drawer, "App sorting", "Install date", "Newest installed apps first", "sort order")
        add(drawer, "App sorting", "Usage frequency", "Prioritize apps you launch most often",
            "sort order")
        add(drawer, "Search placement", "Top bar", "Classic Android application search placement",
            "search")
        add(drawer, "Search placement", "Bottom dock", "Comfortable one-handed thumb reach",
            "search")
        add(drawer, "Search placement", "Hidden", "No search bar in the app drawer", "search")
        add(drawer, "Dimensions & opacity", "Background opacity", null, "transparency")
        add(drawer, "Dimensions & opacity", s(R.string.pref_app_drawer_custom_color_title),
            s(R.string.pref_app_drawer_custom_color_summary), "colour background")
        add(drawer, "Dimensions & opacity", "Row vertical height", null, "spacing")
        add(drawer, "Behavior & typography", "Fast scrollbar", "Alphabet index on right edge",
            "scroll")
        add(drawer, "Behavior & typography", "Show application labels",
            "Text under application icons", "names")
        add(drawer, "Behavior & typography", "Allow two-line labels",
            "Wrap long app names across two lines", "names")
        add(drawer, "Behavior & typography", "Dark label text",
            "Force black text for high-contrast light backgrounds", "colour")
        add(drawer, "Behavior & typography", "Open keyboard on drawer swipe",
            "Instantly focus search field when opening drawer", "search")

        val recents = SettingsSubscreen.RECENTS
        add(recents, "Overview layout", "Overview layout", null,
            "style carousel stock staple ios oxygen")
        add(recents, "Circle to Search", "Hold anywhere on the nav bar",
            "Touch and hold any part of the bottom gesture bar, not only the handle",
            "circle to search lens handle")
        add(recents, "Swipe from bottom corner", "Swipe from bottom corner",
            "Assistant overlay or Circle to Search", "assistant gemini corner")
        add(recents, "Swipe from bottom corner", "Digital assistant app",
            "Pick Gemini, ChatGPT, Claude or another assistant in the default apps",
            "assistant default")
        add(recents, "Actions & telemetry", "Memory info", "Show RAM usage in the recents bottom bar",
            "ram meminfo")
        add(recents, "Actions & telemetry", "ZRAM info", "Add ZRAM usage to the memory info",
            "swap zram")
        add(recents, "Actions & telemetry", "Haptic feedback on scroll",
            "Gentle tick when snapping to each recent task", "vibrate")
        add(recents, "Actions & telemetry", "Quick screenshot action",
            "Capture current active task directly from overview", "screenshot chip")
        add(recents, "Actions & telemetry", "Clear all button",
            "Show button to dismiss all recent tasks", "chip close apps")
        add(recents, "Actions & telemetry", s(R.string.recents_clear_all_preserve_active_title),
            s(R.string.recents_clear_all_preserve_active_summary), "clear all")
        add(recents, "Actions & telemetry", s(R.string.recents_select_title),
            "Freeze the screen and pick its text, then copy, share or search it",
            "select text copy chip")
        add(recents, "Actions & telemetry", s(R.string.recents_lens_title), null,
            "google lens chip")
        add(recents, "Actions & telemetry", s(R.string.swipe_down_to_lock_title),
            s(R.string.swipe_down_to_lock_summary), "lock app")
        add(recents, "Actions & telemetry", "Card background opacity", null, "transparency")
        add(recents, s(R.string.overview_gesture_title), s(R.string.overview_gesture_title),
            s(R.string.overview_gesture_summary), "freeform split screen window")
        add(recents, s(R.string.overview_gesture_title),
            s(R.string.overview_gesture_threshold_title), null, "freeform split screen")

        val misc = SettingsSubscreen.MISC
        add(misc, "Motion", "Motion", "Calm, Standard or Expressive", "animation spring style")
        add(misc, "Motion", "Response", null, "animation speed")
        add(misc, "Motion", "Bouncy animations", "Rubber band overscroll", "overscroll spring")
        add(misc, "Motion", "Bounce strength", null, "overscroll spring")
        add(misc, "Motion", "Animation speed", null, "bounce")
        add(misc, s(R.string.suggestion_pref_screen_title), s(R.string.suggestion_pref_screen_title),
            s(R.string.suggestion_pref_screen_desc), "predictions suggested apps")
        add(misc, "Visual render effects", "Background blur radius", null, "blur depth")
        add(misc, "Transparency", "Surface translucency", null, "transparent opacity")
        add(misc, "OLED hardware care", "Navigation bar burn-in protection",
            "Micro-shift navbar pill pixels periodically to prevent OLED image retention",
            "oled pill")
        add(misc, "OLED hardware care", "Pixel shift interval", null, "burn-in oled")
        add(misc, "Maintenance", "Back up layout & settings",
            "Save the home screen layout and every ZeroLauncher setting to a file",
            "backup export save")
        add(misc, "Maintenance", "Restore from a backup",
            "Replace the layout and settings with a saved backup", "restore import load")
        add(misc, "Maintenance", "Restart ZeroLauncher",
            "Reinitialize workspace, reload icon caches, and flush memory", "reload reboot")
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
                    entry.summary, entry.section, entry.keywords, screenName(entry.screen)
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

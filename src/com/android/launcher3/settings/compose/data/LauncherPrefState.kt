/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.settings.compose.data

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherFiles
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.customization.IconDatabase
import com.android.launcher3.graphics.ThemeManager

/**
 * Reactive bridge to Launcher3's SharedPreferences ([LauncherFiles.SHARED_PREFERENCES_KEY]).
 */
object LauncherPrefsBridge {

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
    }

    fun restart(context: Context) {
        Toast.makeText(context, R.string.restarting_launcher, Toast.LENGTH_SHORT).show()
        Utilities.restart()
    }

    fun getGridSummary(context: Context): String {
        return try {
            val idp = LauncherAppState.getIDP(context)
            "${idp.numColumns} × ${idp.numRows}"
        } catch (_: Throwable) {
            "5 × 5"
        }
    }

    fun getIconPackSummary(context: Context): String {
        return IconDatabase.getGlobalLabel(context)
    }

    /** Short label for [style], sized to fit the hub metric tiles. */
    fun getRecentsStyleSummary(style: String): String {
        return when (style) {
            "stock" -> "Scale"
            "staple" -> "Kami"
            "ios" -> "iOS"
            "oxygen" -> "Oxygen"
            else -> "AOSP"
        }
    }
}

/**
 * State holder for the monochrome themed icons toggle, the same setting that
 * Wallpaper & style flips through the grid customization provider.
 */
@Suppress("DEPRECATION")
@Composable
fun rememberThemedIconsState(): MutableState<Boolean> {
    val context = LocalContext.current
    val themeManager = remember(context) { ThemeManager.INSTANCE.get(context) }
    val state = remember(themeManager) { mutableStateOf(themeManager.isMonoThemeEnabled) }

    return object : MutableState<Boolean> {
        override var value: Boolean
            get() = state.value
            set(newValue) {
                state.value = newValue
                themeManager.isMonoThemeEnabled = newValue
            }

        override fun component1(): Boolean = value
        override fun component2(): (Boolean) -> Unit = { value = it }
    }
}

/**
 * State holder for a Boolean preference that keeps Compose in sync with SharedPreferences.
 */
@Composable
fun rememberBooleanPref(key: String, defaultValue: Boolean): MutableState<Boolean> {
    val context = LocalContext.current
    val prefs = remember(context) { LauncherPrefsBridge.getPrefs(context) }
    val state = remember(key) { mutableStateOf(prefs.getBoolean(key, defaultValue)) }

    DisposableEffect(key, prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, changedKey ->
            if (changedKey == key) {
                state.value = sp.getBoolean(key, defaultValue)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return object : MutableState<Boolean> {
        override var value: Boolean
            get() = state.value
            set(newValue) {
                state.value = newValue
                prefs.edit().putBoolean(key, newValue).apply()
            }

        override fun component1(): Boolean = value
        override fun component2(): (Boolean) -> Unit = { value = it }
    }
}

/**
 * State holder for an Integer preference that keeps Compose in sync with SharedPreferences.
 */
@Composable
fun rememberIntPref(key: String, defaultValue: Int): MutableState<Int> {
    val context = LocalContext.current
    val prefs = remember(context) { LauncherPrefsBridge.getPrefs(context) }
    val state = remember(key) { mutableIntStateOf(prefs.getInt(key, defaultValue)) }

    DisposableEffect(key, prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, changedKey ->
            if (changedKey == key) {
                state.intValue = sp.getInt(key, defaultValue)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return object : MutableState<Int> {
        override var value: Int
            get() = state.intValue
            set(newValue) {
                state.intValue = newValue
                prefs.edit().putInt(key, newValue).apply()
            }

        override fun component1(): Int = value
        override fun component2(): (Int) -> Unit = { value = it }
    }
}

/**
 * State holder for a String preference that keeps Compose in sync with SharedPreferences.
 */
@Composable
fun rememberStringPref(key: String, defaultValue: String): MutableState<String> {
    val context = LocalContext.current
    val prefs = remember(context) { LauncherPrefsBridge.getPrefs(context) }
    val state = remember(key) { mutableStateOf(prefs.getString(key, defaultValue) ?: defaultValue) }

    DisposableEffect(key, prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, changedKey ->
            if (changedKey == key) {
                state.value = sp.getString(key, defaultValue) ?: defaultValue
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return object : MutableState<String> {
        override var value: String
            get() = state.value
            set(newValue) {
                state.value = newValue
                prefs.edit().putString(key, newValue).apply()
            }

        override fun component1(): String = value
        override fun component2(): (String) -> Unit = { value = it }
    }
}

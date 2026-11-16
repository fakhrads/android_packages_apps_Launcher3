/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.settings.compose.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.launcher3.R
import com.android.launcher3.settings.compose.components.ChoiceItem
import com.android.launcher3.settings.compose.components.ChoiceList
import com.android.launcher3.settings.compose.components.NavRow
import com.android.launcher3.settings.compose.components.ScreenScaffold
import com.android.launcher3.settings.compose.components.SettingSlider
import com.android.launcher3.settings.compose.components.SettingSwitch
import com.android.launcher3.settings.compose.components.SettingsCard
import com.android.launcher3.settings.compose.data.LauncherPrefsBridge
import com.android.launcher3.settings.compose.data.LayoutBackup
import com.android.launcher3.settings.compose.data.rememberBooleanPref
import com.android.launcher3.settings.compose.data.rememberIntPref
import com.android.launcher3.settings.compose.data.rememberStringPref
import com.android.systemui.shared.system.BlurUtils

private const val DPS_PACKAGE = "com.google.android.as"

@Composable
fun MiscSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var blurDepth by rememberIntPref("pref_blur_depth", 60)
    var burnInProtection by rememberBooleanPref("pref_navbar_burn_in_protection", false)
    var burnInInterval by rememberIntPref("pref_navbar_burn_in_interval", 60)
    var translucency by rememberIntPref("pref_surface_translucency", 100)
    var motionStyle by rememberStringPref("pref_motion_style", "standard")
    var motionResponse by rememberIntPref("pref_motion_response", 100)
    var bouncy by rememberBooleanPref("pref_bouncy_animations", false)
    var bouncyStrength by rememberIntPref("pref_bouncy_strength", 100)
    var bouncySpeed by rememberIntPref("pref_bouncy_speed", 100)

    var showRestartDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    val blurSupported = remember { BlurUtils.supportsBlursOnWindows() }
    // Suggestions are served by Device Personalization Services, like the legacy screen.
    val suggestionsAvailable = remember {
        try {
            context.packageManager.getApplicationInfo(DPS_PACKAGE, 0).enabled
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.misc_category_title),
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
                title = stringResource(R.string.zl_misc_motion),
                icon = Icons.Rounded.Animation
            ) {
                // The style decides how much a surface is allowed to overshoot; the response
                // decides how quickly it gets there. Both reach every spring in the launcher, so
                // this is the one place the whole feel is set.
                ChoiceList(
                    choices = listOf(
                        ChoiceItem(
                            value = "calm",
                            title = stringResource(R.string.zl_misc_motion_calm),
                            summary = stringResource(R.string.zl_misc_motion_calm_summary),
                            icon = Icons.Rounded.Tune
                        ),
                        ChoiceItem(
                            value = "standard",
                            title = stringResource(R.string.zl_misc_motion_standard),
                            summary = stringResource(R.string.zl_misc_motion_standard_summary),
                            icon = Icons.Rounded.Animation
                        ),
                        ChoiceItem(
                            value = "expressive",
                            title = stringResource(R.string.zl_misc_motion_expressive),
                            summary = stringResource(
                                R.string.zl_misc_motion_expressive_summary
                            ),
                            icon = Icons.Rounded.Bolt
                        )
                    ),
                    selected = motionStyle,
                    onSelect = { motionStyle = it }
                )
                SettingSlider(
                    title = stringResource(R.string.zl_misc_response),
                    value = motionResponse,
                    min = 50,
                    max = 200,
                    step = 10,
                    unit = "%",
                    onValueChange = { motionResponse = it }
                )
                SettingSwitch(
                    title = stringResource(R.string.zl_misc_bouncy),
                    subtitle = stringResource(R.string.zl_misc_bouncy_summary),
                    checked = bouncy,
                    onCheckedChange = { bouncy = it },
                    icon = Icons.Rounded.Animation
                )
                SettingSlider(
                    title = stringResource(R.string.zl_misc_bounce_strength),
                    value = bouncyStrength,
                    min = 25,
                    max = 200,
                    step = 5,
                    unit = "%",
                    onValueChange = { bouncyStrength = it },
                    enabled = bouncy
                )
                SettingSlider(
                    title = stringResource(R.string.zl_misc_animation_speed),
                    value = bouncySpeed,
                    min = 50,
                    max = 200,
                    step = 10,
                    unit = "%",
                    onValueChange = { bouncySpeed = it },
                    enabled = bouncy
                )
            }

            if (suggestionsAvailable) {
                SettingsCard(
                    title = stringResource(R.string.suggestion_pref_screen_title),
                    icon = Icons.Rounded.Lightbulb
                ) {
                    NavRow(
                        title = stringResource(R.string.suggestion_pref_screen_title),
                        summary = stringResource(R.string.suggestion_pref_screen_desc),
                        icon = Icons.Rounded.Lightbulb,
                        onClick = {
                            context.startActivity(
                                Intent("android.settings.ACTION_CONTENT_SUGGESTIONS_SETTINGS")
                            )
                        }
                    )
                }
            }

            if (blurSupported) {
                SettingsCard(
                    title = stringResource(R.string.zl_misc_render_effects),
                    icon = Icons.Rounded.BlurOn
                ) {
                    SettingSlider(
                        title = stringResource(R.string.zl_misc_blur_radius),
                        value = blurDepth,
                        min = 0,
                        max = 175,
                        step = 5,
                        unit = "px",
                        onValueChange = { blurDepth = it }
                    )
                }
            }

            SettingsCard(
                title = stringResource(R.string.zl_misc_transparency),
                icon = Icons.Rounded.BlurOn
            ) {
                // One scale on top of every per-surface opacity the launcher already had, so the
                // whole phone can be made lighter or more solid in one place without the drawer,
                // the hotseat and recents drifting apart.
                val solidLabel = stringResource(R.string.zl_misc_translucency_solid)
                val defaultLabel = stringResource(R.string.icon_pack_default_label)
                SettingSlider(
                    title = stringResource(R.string.zl_misc_translucency),
                    value = translucency,
                    min = 0,
                    max = 150,
                    step = 5,
                    unit = "%",
                    onValueChange = { translucency = it },
                    valueLabel = { value ->
                        when {
                            value == 0 -> solidLabel
                            value == 100 -> defaultLabel
                            value < 100 -> "$value%"
                            else -> "$value%"
                        }
                    }
                )
                Text(
                    text = stringResource(R.string.zl_misc_translucency_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            SettingsCard(
                title = stringResource(R.string.zl_misc_oled),
                icon = Icons.Rounded.Security
            ) {
                SettingSwitch(
                    title = stringResource(R.string.zl_misc_burn_in),
                    subtitle = stringResource(R.string.zl_misc_burn_in_summary),
                    checked = burnInProtection,
                    onCheckedChange = { burnInProtection = it }
                )
                SettingSlider(
                    title = stringResource(R.string.zl_misc_burn_in_interval),
                    value = burnInInterval,
                    min = 15,
                    max = 300,
                    step = 15,
                    unit = "s",
                    onValueChange = { burnInInterval = it },
                    enabled = burnInProtection
                )
            }

            SettingsCard(
                title = stringResource(R.string.zl_misc_maintenance),
                icon = Icons.Rounded.Tune
            ) {
                NavRow(
                    title = stringResource(R.string.zl_misc_backup),
                    summary = stringResource(R.string.zl_misc_backup_summary),
                    icon = Icons.Rounded.Save,
                    onClick = { context.findActivity()?.let { LayoutBackup.pickBackupFile(it) } }
                )
                NavRow(
                    title = stringResource(R.string.zl_misc_restore),
                    summary = stringResource(R.string.zl_misc_restore_summary),
                    icon = Icons.Rounded.Restore,
                    onClick = { showRestoreDialog = true }
                )
                NavRow(
                    title = stringResource(R.string.zl_misc_restart),
                    summary = stringResource(R.string.zl_misc_restart_summary),
                    icon = Icons.Rounded.Refresh,
                    onClick = { showRestartDialog = true }
                )
            }
        }

        if (showRestoreDialog) {
            AlertDialog(
                onDismissRequest = { showRestoreDialog = false },
                title = { Text(stringResource(R.string.zl_misc_restore_dialog_title)) },
                text = {
                    Text(
                        stringResource(R.string.zl_misc_restore_dialog_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showRestoreDialog = false
                            context.findActivity()?.let { LayoutBackup.pickRestoreFile(it) }
                        }
                    ) {
                        Text(
                            stringResource(R.string.zl_misc_restore_dialog_pick),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }

        if (showRestartDialog) {
            AlertDialog(
                onDismissRequest = { showRestartDialog = false },
                title = { Text(stringResource(R.string.zl_misc_restart_dialog_title)) },
                text = {
                    Text(
                        stringResource(R.string.zl_misc_restart_dialog_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showRestartDialog = false
                            LauncherPrefsBridge.restart(context)
                        }
                    ) {
                        Text(
                            stringResource(R.string.pref_restart_title),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestartDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context != null) {
        if (context is Activity) return context
        context = (context as? ContextWrapper)?.baseContext
    }
    return null
}

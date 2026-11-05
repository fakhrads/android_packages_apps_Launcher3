/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.settings.compose.screens

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Refresh
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
import com.android.launcher3.settings.compose.components.NavRow
import com.android.launcher3.settings.compose.components.ScreenScaffold
import com.android.launcher3.settings.compose.components.SettingSlider
import com.android.launcher3.settings.compose.components.SettingSwitch
import com.android.launcher3.settings.compose.components.SettingsCard
import com.android.launcher3.settings.compose.data.LauncherPrefsBridge
import com.android.launcher3.settings.compose.data.rememberBooleanPref
import com.android.launcher3.settings.compose.data.rememberIntPref
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

    var showRestartDialog by remember { mutableStateOf(false) }

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
        title = "Miscellaneous",
        onBack = onBack
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                    title = "Visual render effects",
                    icon = Icons.Rounded.BlurOn
                ) {
                    SettingSlider(
                        title = "Background blur radius",
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
                title = "OLED hardware care",
                icon = Icons.Rounded.Security
            ) {
                SettingSwitch(
                    title = "Navigation bar burn-in protection",
                    subtitle = "Micro-shift navbar pill pixels periodically to prevent OLED image retention",
                    checked = burnInProtection,
                    onCheckedChange = { burnInProtection = it }
                )
                SettingSlider(
                    title = "Pixel shift interval",
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
                title = "Maintenance",
                icon = Icons.Rounded.Tune
            ) {
                NavRow(
                    title = "Restart ZeroLauncher",
                    summary = "Reinitialize workspace, reload icon caches, and flush memory",
                    icon = Icons.Rounded.Refresh,
                    onClick = { showRestartDialog = true }
                )
            }
        }

        if (showRestartDialog) {
            AlertDialog(
                onDismissRequest = { showRestartDialog = false },
                title = { Text("Restart ZeroLauncher?") },
                text = {
                    Text(
                        "The launcher process will restart immediately to reload all resources and configurations.",
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
                        Text("Restart", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestartDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

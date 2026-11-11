/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.settings.compose.screens

import android.content.Intent
import android.os.Bundle
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.launcher3.R
import com.android.launcher3.customization.IconDatabase
import com.android.launcher3.icons.pack.IconPackSettingsActivity
import com.android.launcher3.settings.SettingsActivity
import com.android.launcher3.settings.compose.components.ICON_SHAPE_PREF_KEY
import com.android.launcher3.settings.compose.components.IconShapePicker
import com.android.launcher3.settings.compose.components.NavRow
import com.android.launcher3.settings.compose.components.iconShapeForKey
import com.android.launcher3.settings.compose.components.ScreenScaffold
import com.android.launcher3.settings.compose.components.SettingSlider
import com.android.launcher3.settings.compose.components.SettingSwitch
import com.android.launcher3.settings.compose.components.SettingsCard
import com.android.launcher3.settings.compose.data.rememberBooleanPref
import com.android.launcher3.settings.compose.data.LauncherPrefsBridge
import com.android.launcher3.settings.compose.data.rememberIntPref
import com.android.launcher3.settings.compose.data.rememberStringPref
import com.android.launcher3.settings.compose.data.rememberThemedIconsState

@Composable
fun IconsSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var iconSize by rememberIntPref("pref_custom_icon_size", 100)
    var fontSize by rememberIntPref("pref_custom_font_size", 100)
    var themedIcons by rememberThemedIconsState()
    var lawnIcons by rememberBooleanPref("pref_grayscale_icon_map_lawn", true)
    val iconPack by rememberStringPref(IconDatabase.KEY_ICON_PACK, IconDatabase.VALUE_DEFAULT)
    var iconShapeKey by rememberStringPref(ICON_SHAPE_PREF_KEY, "")
    val previewShape = remember(iconShapeKey) { iconShapeForKey(iconShapeKey) }
    val iconPackText = remember(iconPack) { LauncherPrefsBridge.getIconPackSummary(context) }

    val animatedIconScale by animateFloatAsState(
        targetValue = iconSize / 100f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "icon_scale_preview"
    )

    ScreenScaffold(
        title = "Icons & theming",
        onBack = onBack
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Interactive Preview Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Live preview",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PreviewIconItem(
                            label = "Phone",
                            icon = Icons.Rounded.Call,
                            scale = animatedIconScale,
                            themed = themedIcons,
                            fontSizePercent = fontSize,
                            shape = previewShape
                        )
                        PreviewIconItem(
                            label = "Messages",
                            icon = Icons.Rounded.Chat,
                            scale = animatedIconScale,
                            themed = themedIcons,
                            fontSizePercent = fontSize,
                            shape = previewShape
                        )
                        PreviewIconItem(
                            label = "Browser",
                            icon = Icons.Rounded.Language,
                            scale = animatedIconScale,
                            themed = themedIcons,
                            fontSizePercent = fontSize,
                            shape = previewShape
                        )
                    }
                }
            }

            SettingsCard(
                title = "Icon shape",
                icon = Icons.Rounded.Category
            ) {
                IconShapePicker(
                    selected = iconShapeKey,
                    onSelect = { iconShapeKey = it }
                )
            }

            // Section: Scaling Controls
            SettingsCard(
                title = "Scale & dimensions",
                icon = Icons.Rounded.Tune
            ) {
                SettingSlider(
                    title = "Icon size",
                    value = iconSize,
                    min = 50,
                    max = 150,
                    step = 5,
                    unit = "%",
                    onValueChange = { iconSize = it }
                )

                SettingSlider(
                    title = "Label font size",
                    value = fontSize,
                    min = 50,
                    max = 150,
                    step = 5,
                    unit = "%",
                    onValueChange = { fontSize = it }
                )
            }

            // Section: Appearance
            SettingsCard(
                title = "Adaptive styling",
                icon = Icons.Rounded.Palette
            ) {
                NavRow(
                    title = stringResource(R.string.icon_pack_title),
                    summary = "Apply icons from an installed icon pack",
                    icon = Icons.Rounded.Interests,
                    onClick = {
                        context.startActivity(Intent(context, IconPackSettingsActivity::class.java))
                    },
                    badgeText = iconPackText
                )
                SettingSwitch(
                    title = "Themed icons",
                    subtitle = "Monochrome icons tinted with the wallpaper colors",
                    checked = themedIcons,
                    onCheckedChange = { themedIcons = it },
                    icon = Icons.Rounded.Palette
                )
                SettingSwitch(
                    title = stringResource(R.string.pref_grayscale_icon_map_lawn_title),
                    subtitle = stringResource(R.string.pref_grayscale_icon_map_lawn_summary),
                    checked = lawnIcons,
                    onCheckedChange = { lawnIcons = it },
                    icon = Icons.Rounded.AutoAwesome
                )
                NavRow(
                    title = stringResource(R.string.notification_dots_title),
                    summary = "Managed in system notification settings",
                    icon = Icons.Rounded.Notifications,
                    onClick = {
                        // Same intent the legacy NotificationDotsPreference used.
                        val args = Bundle().apply {
                            putString(SettingsActivity.EXTRA_FRAGMENT_HIGHLIGHT_KEY, "notification_badging")
                        }
                        context.startActivity(
                            Intent("android.settings.NOTIFICATION_SETTINGS")
                                .putExtra(":settings:show_fragment_args", args)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun PreviewIconItem(
    label: String,
    icon: ImageVector,
    scale: Float,
    themed: Boolean,
    fontSizePercent: Int,
    shape: Shape
) {
    val colors = MaterialTheme.colorScheme
    val containerBg = if (themed) colors.surfaceContainerLowest else colors.primaryContainer
    val iconTint = if (themed) colors.onSurface else colors.onPrimaryContainer

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(shape)
                .background(containerBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = (12 * (fontSizePercent / 100f)).sp,
            fontWeight = FontWeight.Medium,
            color = colors.onSurface
        )
    }
}

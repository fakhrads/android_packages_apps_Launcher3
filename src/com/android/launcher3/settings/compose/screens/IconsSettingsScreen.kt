/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.settings.compose.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.launcher3.settings.compose.components.ScreenScaffold
import com.android.launcher3.settings.compose.components.SettingSlider
import com.android.launcher3.settings.compose.components.SettingSwitch
import com.android.launcher3.settings.compose.components.SettingsCard
import com.android.launcher3.settings.compose.data.rememberBooleanPref
import com.android.launcher3.settings.compose.data.rememberIntPref

@Composable
fun IconsSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    var iconSize by rememberIntPref("pref_custom_icon_size", 100)
    var fontSize by rememberIntPref("pref_custom_font_size", 100)
    var themedIcons by rememberBooleanPref("pref_grayscale_icon_map_lawn", true)
    var notificationDots by rememberBooleanPref("pref_icon_badging", true)

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
                            fontSizePercent = fontSize
                        )
                        PreviewIconItem(
                            label = "Messages",
                            icon = Icons.Rounded.Chat,
                            scale = animatedIconScale,
                            themed = themedIcons,
                            fontSizePercent = fontSize
                        )
                        PreviewIconItem(
                            label = "Browser",
                            icon = Icons.Rounded.Language,
                            scale = animatedIconScale,
                            themed = themedIcons,
                            fontSizePercent = fontSize
                        )
                    }
                }
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
                SettingSwitch(
                    title = "Monochrome themed icons",
                    subtitle = "Generate adaptive themed icons for all apps",
                    checked = themedIcons,
                    onCheckedChange = { themedIcons = it },
                    icon = Icons.Rounded.Palette
                )
                SettingSwitch(
                    title = "Notification badges",
                    subtitle = "Show notification dot indicators on app icons",
                    checked = notificationDots,
                    onCheckedChange = { notificationDots = it },
                    icon = Icons.Rounded.Notifications
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
    fontSizePercent: Int
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
                .clip(CircleShape)
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

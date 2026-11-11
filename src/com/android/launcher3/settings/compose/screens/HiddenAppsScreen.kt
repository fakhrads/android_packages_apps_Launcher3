/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.settings.compose.screens

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Process
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.launcher3.AppFilter
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.settings.compose.components.ScreenScaffold
import java.text.Collator

private class HiddenAppEntry(val info: LauncherActivityInfo, val label: String) {
    val component: ComponentName = info.componentName
    val key: String = component.flattenToShortString()
}

private fun loadApps(context: Context): List<HiddenAppEntry> {
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    val collator = Collator.getInstance()
    val apps = ArrayList<HiddenAppEntry>()
    for (info in launcherApps.getActivityList(null, Process.myUserHandle())) {
        apps.add(HiddenAppEntry(info, info.label.toString()))
    }
    apps.sortWith { a, b -> collator.compare(a.label, b.label) }
    return apps
}

private fun iconBitmap(info: LauncherActivityInfo, sizePx: Int): ImageBitmap {
    val drawable = info.getIcon(0)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, sizePx, sizePx)
    drawable.draw(Canvas(bitmap))
    return bitmap.asImageBitmap()
}

/** Apps hidden from the app drawer and its search. They stay where they are on the home screen. */
@Composable
fun HiddenAppsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val apps = remember { loadApps(context) }
    var hidden by remember { mutableStateOf(LauncherPrefs.get(context).get(LauncherPrefs.HIDDEN_APPS)) }
    val iconSizePx = with(LocalDensity.current) { 40.dp.roundToPx() }

    ScreenScaffold(
        title = "Hidden apps",
        onBack = onBack
    ) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item(key = "header", contentType = null) {
                Text(
                    text = "Checked apps are gone from the app drawer and its search. " +
                        "Their icons on the home screen stay.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            items(apps, key = { it.key }) { app ->
                val isHidden = hidden.contains(app.key)
                val icon = remember(app.key) { iconBitmap(app.info, iconSizePx) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            AppFilter.setAppHidden(context, app.component, !isHidden)
                            hidden = LauncherPrefs.get(context).get(LauncherPrefs.HIDDEN_APPS)
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = isHidden,
                        onCheckedChange = { checked ->
                            AppFilter.setAppHidden(context, app.component, checked)
                            hidden = LauncherPrefs.get(context).get(LauncherPrefs.HIDDEN_APPS)
                        }
                    )
                }
            }
        }
    }
}

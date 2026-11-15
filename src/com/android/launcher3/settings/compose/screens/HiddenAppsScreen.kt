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
import android.os.UserHandle
import android.os.UserManager
import androidx.annotation.WorkerThread
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.android.launcher3.R
import com.android.launcher3.settings.compose.components.ScreenScaffold
import java.text.Collator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A launchable app, for the app lists of the settings. */
internal class LauncherAppEntry(
    val info: LauncherActivityInfo,
    val label: String,
    /** The name of the app's profile, null for the personal one. */
    val profile: String? = null,
) {
    val component: ComponentName = info.componentName
    /** Hidden apps and drawer tabs are stored by component, for every profile. */
    val key: String = component.flattenToShortString()
    /** One row per profile, for list keys. */
    val rowKey: String = key + "#" + info.user.hashCode()
}

/**
 * The launchable apps sorted by label: the personal profile's, then, with [allProfiles], those of
 * every other profile that is running (a work profile, an unlocked private space). Slow, so not
 * on the main thread.
 */
@WorkerThread
internal fun loadLauncherApps(context: Context, allProfiles: Boolean = false): List<LauncherAppEntry> {
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    val userManager = context.getSystemService(UserManager::class.java)
    val collator = Collator.getInstance()
    val me = Process.myUserHandle()
    val users = ArrayList<UserHandle>()
    users.add(me)
    if (allProfiles) userManager.userProfiles.filterTo(users) { it != me }
    val apps = ArrayList<LauncherAppEntry>()
    for (user in users) {
        if (user != me && userManager.isQuietModeEnabled(user)) continue
        val profile = if (user == me) null else profileName(context, launcherApps, user)
        val section = ArrayList<LauncherAppEntry>()
        for (info in launcherApps.getActivityList(null, user)) {
            section.add(LauncherAppEntry(info, info.label.toString(), profile))
        }
        section.sortWith { a, b -> collator.compare(a.label, b.label) }
        apps.addAll(section)
    }
    return apps
}

private fun profileName(context: Context, launcherApps: LauncherApps, user: UserHandle): String {
    val type = try {
        launcherApps.getLauncherUserInfo(user)?.userType
    } catch (e: RuntimeException) {
        null
    }
    return context.getString(
        when (type) {
            UserManager.USER_TYPE_PROFILE_MANAGED -> R.string.zl_profile_work
            UserManager.USER_TYPE_PROFILE_PRIVATE -> R.string.zl_profile_private
            else -> R.string.zl_profile_other
        }
    )
}

@WorkerThread
internal fun appIconBitmap(info: LauncherActivityInfo, sizePx: Int): ImageBitmap {
    // Badged, so apps of a work profile carry its badge
    val drawable = info.getBadgedIcon(0)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, sizePx, sizePx)
    drawable.draw(Canvas(bitmap))
    return bitmap.asImageBitmap()
}

/** The apps of [loadLauncherApps], loaded off the main thread; null until they are there. */
@Composable
internal fun rememberLauncherApps(allProfiles: Boolean = false): List<LauncherAppEntry>? {
    val context = LocalContext.current
    val apps by produceState<List<LauncherAppEntry>?>(null, allProfiles) {
        value = withContext(Dispatchers.IO) { loadLauncherApps(context, allProfiles) }
    }
    return apps
}

/** An app's icon, drawn off the main thread; null until it is there. */
@Composable
internal fun rememberAppIcon(app: LauncherAppEntry, sizePx: Int): ImageBitmap? {
    val icon by produceState<ImageBitmap?>(null, app.rowKey, sizePx) {
        value = withContext(Dispatchers.IO) {
            try {
                appIconBitmap(app.info, sizePx)
            } catch (e: RuntimeException) {
                null
            }
        }
    }
    return icon
}

/** The icon of an app row, or the space for it while it loads. */
@Composable
internal fun AppRowIcon(app: LauncherAppEntry, sizePx: Int) {
    val icon = rememberAppIcon(app, sizePx)
    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
    } else {
        Spacer(modifier = Modifier.size(40.dp))
    }
}

/** Apps hidden from the app drawer and its search. They stay where they are on the home screen. */
@Composable
fun HiddenAppsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val apps = rememberLauncherApps(allProfiles = true)
    var hidden by remember { mutableStateOf(AppFilter.hiddenApps(context)) }
    // The model reloads once when the list closes, not for every switch
    var changed by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { if (changed) AppFilter.reloadForHiddenApps() }
    }
    val iconSizePx = with(LocalDensity.current) { 40.dp.roundToPx() }

    fun setHidden(app: LauncherAppEntry, hide: Boolean) {
        if (AppFilter.saveAppHidden(context, app.component, hide)) changed = true
        hidden = AppFilter.hiddenApps(context)
    }

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
            for ((profile, profileApps) in apps.orEmpty().groupBy { it.profile }) {
                if (profile != null) {
                    item(key = "profile:$profile", contentType = "profile") {
                        Text(
                            text = profile,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp)
                        )
                    }
                }
                items(profileApps, key = { it.rowKey }) { app ->
                    val isHidden = hidden.contains(app.key)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { setHidden(app, !isHidden) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        AppRowIcon(app, iconSizePx)
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
                            onCheckedChange = { checked -> setHidden(app, checked) }
                        )
                    }
                }
            }
        }
    }
}

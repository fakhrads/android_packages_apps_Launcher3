/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.settings.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.android.launcher3.R
import com.android.launcher3.settings.compose.components.NavRow
import com.android.launcher3.settings.compose.components.SettingsCard
import com.android.launcher3.settings.compose.data.SettingsSearchIndex

private fun screenIcon(screen: SettingsSubscreen): ImageVector = when (screen) {
    SettingsSubscreen.HOMESCREEN -> Icons.Rounded.Home
    SettingsSubscreen.ICONS -> Icons.Rounded.Palette
    SettingsSubscreen.APP_DRAWER -> Icons.Rounded.Apps
    SettingsSubscreen.RECENTS -> Icons.Rounded.Layers
    SettingsSubscreen.MISC -> Icons.Rounded.Tune
    SettingsSubscreen.HIDDEN_APPS -> Icons.Rounded.Apps
    SettingsSubscreen.DRAWER_TABS -> Icons.Rounded.Apps
    SettingsSubscreen.DRAWER_TAB_APPS -> Icons.Rounded.Apps
    SettingsSubscreen.HUB -> Icons.Rounded.Settings
}

/** The search field at the top of the settings hub. */
@Composable
fun SettingsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focus: FocusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.zl_settings_search_hint)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(
                            R.string.home_screen_files_rename_dialog_clear_button
                        )
                    )
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        modifier = modifier.fillMaxWidth()
    )
}

/** What the query found. Opening a result opens the screen the setting is on. */
@Composable
fun SettingsSearchResults(
    query: String,
    onNavigate: (SettingsSubscreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val index = remember(context) { SettingsSearchIndex.build(context) }
    val results = remember(index, query) { SettingsSearchIndex.search(index, query) }

    if (results.isEmpty()) {
        Text(
            text = stringResource(R.string.zl_settings_search_no_match, query.trim()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 16.dp)
        )
        return
    }
    SettingsCard(
        title = pluralStringResource(
            R.plurals.zl_settings_search_results, results.size, results.size
        ),
        icon = Icons.Rounded.Search,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            results.forEach { entry ->
                NavRow(
                    title = entry.title,
                    summary = entry.screenName + " › " + entry.section,
                    icon = screenIcon(entry.screen),
                    onClick = {
                        focus.clearFocus()
                        onNavigate(entry.screen)
                    }
                )
            }
        }
    }
}

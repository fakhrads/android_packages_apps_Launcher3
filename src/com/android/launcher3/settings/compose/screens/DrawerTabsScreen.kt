/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.settings.compose.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.launcher3.allapps.DrawerTabs
import com.android.launcher3.settings.compose.components.NavRow
import com.android.launcher3.settings.compose.components.ScreenScaffold
import com.android.launcher3.settings.compose.components.SettingSwitch
import com.android.launcher3.settings.compose.components.SettingsCard
import com.android.launcher3.settings.compose.data.rememberBooleanPref

/** The drawer tabs: on or off, category tabs, and the user's own tabs. */
@Composable
fun DrawerTabsScreen(
    onBack: () -> Unit,
    onEditTab: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showTabs by rememberBooleanPref("pref_drawer_tabs", false)
    var categoryTabs by rememberBooleanPref("pref_drawer_tabs_auto", true)
    val tabs = remember { DrawerTabs.loadCustomTabs(context) }
    var showAddDialog by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = "Drawer tabs",
        onBack = onBack
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsCard(
                title = "Tabs",
                icon = Icons.Rounded.Tab
            ) {
                SettingSwitch(
                    title = "Show tabs",
                    subtitle = "A row of tabs under the app drawer search bar. " +
                        "Long press a tab to come back here.",
                    checked = showTabs,
                    onCheckedChange = { showTabs = it }
                )
                SettingSwitch(
                    title = "Category tabs",
                    subtitle = "One more tab per app category, the same categories " +
                        "Smart Auto-Organize uses",
                    checked = categoryTabs,
                    onCheckedChange = { categoryTabs = it },
                    enabled = showTabs
                )
            }

            SettingsCard(
                title = "Your tabs",
                icon = Icons.Rounded.Folder
            ) {
                for (tab in tabs) {
                    val count = tab.apps.size
                    NavRow(
                        title = tab.name,
                        summary = if (count == 1) "1 app" else "$count apps",
                        icon = Icons.Rounded.Folder,
                        onClick = { onEditTab(tab.id) }
                    )
                }
                NavRow(
                    title = "Add a tab",
                    summary = "Name it, then pick its apps",
                    icon = Icons.Rounded.Add,
                    onClick = { showAddDialog = true }
                )
            }
        }
    }

    if (showAddDialog) {
        TabNameDialog(
            title = "New tab",
            initialName = "",
            confirmLabel = "Add",
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                showAddDialog = false
                onEditTab(DrawerTabs.addCustomTab(context, name).id)
            }
        )
    }
}

/** One of the user's tabs: its apps, and renaming, moving and deleting it. */
@Composable
fun DrawerTabAppsScreen(
    tabId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tab = remember { DrawerTabs.findCustomTab(context, tabId) }
    if (tab == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var name by remember { mutableStateOf(tab.name) }
    var members by remember { mutableStateOf(tab.apps) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val apps = remember { loadLauncherApps(context) }
    val iconSizePx = with(LocalDensity.current) { 40.dp.roundToPx() }

    fun setMember(app: LauncherAppEntry, inTab: Boolean) {
        DrawerTabs.setAppInTab(context, tabId, app.component, inTab)
        members = DrawerTabs.findCustomTab(context, tabId)?.apps ?: emptySet()
    }

    ScreenScaffold(
        title = name,
        onBack = onBack
    ) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item(key = "actions", contentType = null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp)
                ) {
                    TextButton(onClick = { showRenameDialog = true }) { Text("Rename") }
                    TextButton(onClick = { DrawerTabs.moveCustomTab(context, tabId, -1) }) {
                        Text("Move left")
                    }
                    TextButton(onClick = { DrawerTabs.moveCustomTab(context, tabId, 1) }) {
                        Text("Move right")
                    }
                    TextButton(onClick = { showDeleteDialog = true }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            items(apps, key = { it.key }) { app ->
                val inTab = members.contains(app.key)
                val icon = remember(app.key) { appIconBitmap(app.info, iconSizePx) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { setMember(app, !inTab) }
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
                        checked = inTab,
                        onCheckedChange = { checked -> setMember(app, checked) }
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        TabNameDialog(
            title = "Rename tab",
            initialName = name,
            confirmLabel = "Rename",
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                showRenameDialog = false
                DrawerTabs.renameCustomTab(context, tabId, newName)
                name = DrawerTabs.findCustomTab(context, tabId)?.name ?: newName
            }
        )
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete \"$name\"?") },
            text = {
                Text(
                    "The tab goes away. Its apps stay in the app drawer.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    DrawerTabs.deleteCustomTab(context, tabId)
                    onBack()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TabNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                singleLine = true,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/*
 * Copyright 2025-2026 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.quickstep.util

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.UserHandle
import android.util.Log
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.LooperExecutor
import java.lang.Runnable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject

/**
 * Caches per-package App Lock state for recents and notifies listeners (on the UI thread) when it
 * changes.
 *
 * The state comes from the platform App Lock (ApplicationInfo#isAppLockEnabled, filled in by
 * LauncherApps because the launcher holds LOCK_APPS). LauncherAppsService reports App Lock
 * enable/disable as onPackageChanged, which is what refreshes the cache here.
 */
@LauncherAppSingleton
class AxSandboxState
@Inject
constructor(
    @ApplicationContext context: Context,
    @Ui private val uiExecutor: LooperExecutor,
    lifecycle: DaggerSingletonTracker,
) {
    private val launcherApps: LauncherApps? = context.getSystemService(LauncherApps::class.java)
    private val lockCache = ConcurrentHashMap<String, Boolean>()
    private val listeners = CopyOnWriteArrayList<Runnable>()

    private val packageCallback =
        object : LauncherApps.Callback() {
            override fun onPackageChanged(packageName: String, user: UserHandle) =
                refresh(packageName, user)

            override fun onPackageAdded(packageName: String, user: UserHandle) =
                refresh(packageName, user)

            override fun onPackageRemoved(packageName: String, user: UserHandle) =
                refresh(packageName, user)

            override fun onPackagesAvailable(
                packageNames: Array<String>,
                user: UserHandle,
                replacing: Boolean,
            ) = packageNames.forEach { refresh(it, user) }

            override fun onPackagesUnavailable(
                packageNames: Array<String>,
                user: UserHandle,
                replacing: Boolean,
            ) = packageNames.forEach { refresh(it, user) }
        }

    init {
        if (appLockSupported()) {
            launcherApps?.let { apps ->
                apps.registerCallback(packageCallback, uiExecutor.handler)
                lifecycle.addCloseable { apps.unregisterCallback(packageCallback) }
            }
        }
    }

    fun addChangeListener(listener: Runnable) {
        listeners.addIfAbsent(listener)
    }

    fun removeChangeListener(listener: Runnable) {
        listeners.remove(listener)
    }

    /** May be called from any thread; the first lookup per package is a binder call. */
    fun hasAppLock(packageName: String?, userId: Int): Boolean {
        if (packageName.isNullOrBlank() || !appLockSupported()) return false
        val cacheKey = cacheKey(packageName, userId)
        lockCache[cacheKey]?.let { return it }
        val hasLock = queryAppLock(packageName, userId)
        lockCache[cacheKey] = hasLock
        return hasLock
    }

    /** Only packages recents already asked about are re-queried; the rest stay uncached. */
    private fun refresh(packageName: String, user: UserHandle) {
        val cacheKey = cacheKey(packageName, user.identifier)
        val old = lockCache[cacheKey] ?: return
        val now = queryAppLock(packageName, user.identifier)
        lockCache[cacheKey] = now
        if (now != old) dispatchChanged()
    }

    private fun queryAppLock(packageName: String, userId: Int): Boolean {
        val apps = launcherApps ?: return false
        return try {
            apps.getApplicationInfo(packageName, 0, UserHandle.of(userId)).isAppLockEnabled
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: RuntimeException) {
            Log.w(TAG, "queryAppLock failed for $packageName", e)
            false
        }
    }

    private fun dispatchChanged() {
        if (listeners.isEmpty()) return
        uiExecutor.execute { listeners.forEach { it.run() } }
    }

    private fun appLockSupported() = android.security.Flags.appLockApis()

    private companion object {
        const val TAG = "AxSandboxState"

        fun cacheKey(packageName: String, userId: Int) = "$packageName:$userId"
    }
}

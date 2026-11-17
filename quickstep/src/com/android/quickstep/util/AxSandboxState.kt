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
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.LooperExecutor
import java.lang.Runnable
import javax.inject.Inject

/**
 * Per-package app lock state for recents. On AxionOS it came from AxSandboxManager, which DerpFest
 * does not have: DerpFest's app lock is AOSP's (AppLockController and AppLockOverlayController in
 * the window manager), which covers a locked app on its own, and its launcher reads no lock state
 * either. So no task is reported locked here and no change is ever dispatched; the class keeps its
 * API so RecentTasksList and the Dagger graph stay as they are.
 */
@LauncherAppSingleton
class AxSandboxState
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @Ui private val uiExecutor: LooperExecutor,
    lifecycle: DaggerSingletonTracker,
) {
    fun addChangeListener(listener: Runnable) {}

    fun removeChangeListener(listener: Runnable) {}

    fun hasAppLock(packageName: String?, userId: Int): Boolean = false
}

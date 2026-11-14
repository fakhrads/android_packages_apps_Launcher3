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

import com.android.launcher3.dagger.LauncherAppSingleton
import java.lang.Runnable
import javax.inject.Inject

/**
 * Per-package app lock state for recents.
 *
 * AxionOS backs this with its framework AxSandboxManager. Caduceus has no framework app lock, so
 * no app is ever locked and the state never changes. The API stays the same so recents keeps
 * working unchanged.
 */
@LauncherAppSingleton
class AxSandboxState @Inject constructor() {

    fun addChangeListener(listener: Runnable) {}

    fun removeChangeListener(listener: Runnable) {}

    /** May be called from any thread. */
    fun hasAppLock(packageName: String?, userId: Int): Boolean = false
}

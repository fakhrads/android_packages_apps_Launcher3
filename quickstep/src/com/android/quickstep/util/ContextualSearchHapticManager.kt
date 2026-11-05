/*
 * Copyright (C) 2024 The Android Open Source Project
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
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition
import android.os.Vibrator
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.VibratorWrapper
import com.android.quickstep.DeviceConfigWrapper.Companion.get
import com.android.quickstep.dagger.QuickstepBaseAppComponent
import javax.inject.Inject
import kotlin.math.pow

/** Manages haptics relating to Contextual Search invocations. */
@LauncherAppSingleton
class ContextualSearchHapticManager
@Inject
internal constructor(
    @ApplicationContext private val context: Context,
    private val contextualSearchStateManager: ContextualSearchStateManager,
    private val vibratorWrapper: VibratorWrapper,
) {

    private var searchEffect = createSearchEffect()

    private fun createSearchEffect() =
        if (
            context
                .getSystemService(Vibrator::class.java)!!
                .areAllPrimitivesSupported(Composition.PRIMITIVE_TICK)
        ) {
            VibrationEffect.startComposition()
                .addPrimitive(Composition.PRIMITIVE_TICK, 1f)
                .compose()
        } else {
            // fallback for devices without composition support
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        }

    /** Indicates that search has been invoked. */
    fun vibrateForSearch() {
        searchEffect.let { vibratorWrapper.vibrate(it) }
    }

    /** Indicates that search will be invoked if the current gesture is maintained. */
    fun vibrateForSearchHint() {
        // Disabled pre-trigger rapid-fire haptic hint on touch down.
        // Firing 50 consecutive low-tick vibration primitives on raw ACTION_DOWN before gesture
        // disambiguation causes aggressive haptic chatter ("machine-gun rattle") whenever the user
        // performs a standard swipe-up gesture to go home or recents.
        // Crisp haptic feedback is preserved on actual search commitment via vibrateForSearch().
        return
    }

    companion object {
        @JvmField
        val INSTANCE =
            DaggerSingletonObject(QuickstepBaseAppComponent::getContextualSearchHapticManager)
    }
}

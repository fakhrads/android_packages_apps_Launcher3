/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.anim

import android.content.Context
import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator
import com.android.launcher3.LauncherPrefs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Optional springy motion for launcher surfaces (pref_bouncy_animations). The values below are
 * starting points meant to be tuned on device.
 */
object BouncyAnimations {

    /** Damping for the folder open springs, lower than the stock 0.8 so the folder overshoots. */
    const val FOLDER_OPEN_DAMPING = 0.62f

    /** Long press popup scale-in duration; the spring curve needs more time than the stock one. */
    const val POPUP_OPEN_DURATION = 420

    @JvmField val POPUP_OPEN_INTERPOLATOR: Interpolator = DampedSpringInterpolator(0.5f, 1.6f)

    /** Home page snap; lands slightly past the target page and settles back. */
    @JvmField val PAGE_SNAP_INTERPOLATOR: Interpolator = OvershootInterpolator(1.2f)

    @JvmStatic
    fun isEnabled(context: Context): Boolean =
        try {
            LauncherPrefs.BOUNCY_ANIMATIONS.get(context)
        } catch (_: RuntimeException) {
            // Preview contexts (e.g. the wallpaper picker grid preview) may lack launcher prefs.
            false
        }
}

/**
 * Time-based curve of an underdamped spring moving from 0 to 1, for animators that need a fixed
 * duration.
 *
 * @param dampingRatio below 1 for overshoot
 * @param cycles undamped oscillations over the whole duration; higher settles faster
 */
class DampedSpringInterpolator(dampingRatio: Float, cycles: Float) : Interpolator {
    private val zeta = dampingRatio.toDouble()
    private val omega = 2 * Math.PI * cycles
    private val omegaD = omega * sqrt(1 - zeta * zeta)

    override fun getInterpolation(input: Float): Float {
        if (input >= 1f) return 1f
        val t = input.toDouble()
        val decay = exp(-zeta * omega * t)
        return (1 - decay * (cos(omegaD * t) + zeta * omega / omegaD * sin(omegaD * t))).toFloat()
    }
}

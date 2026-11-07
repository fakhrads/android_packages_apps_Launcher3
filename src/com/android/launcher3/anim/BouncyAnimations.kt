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
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Optional springy motion for launcher surfaces (pref_bouncy_animations). Every value derives from
 * two user sliders: bounce strength (pref_bouncy_strength) and speed (pref_bouncy_speed), both in
 * percent with 100 as the tuned default.
 */
object BouncyAnimations {

    private const val POPUP_OPEN_DURATION = 420
    private const val PAGE_SNAP_TENSION = 1.2f

    @JvmStatic
    fun isEnabled(context: Context): Boolean = read(false) {
        LauncherPrefs.BOUNCY_ANIMATIONS.get(context)
    }

    /** Bounce strength, 1 = default. Higher means less damping and larger overshoot. */
    @JvmStatic
    fun strength(context: Context): Float = read(1f) {
        LauncherPrefs.BOUNCY_STRENGTH.get(context) / 100f
    }

    /** Animation speed, 1 = default. Springs get stiffer and fixed durations shorter. */
    @JvmStatic
    fun speed(context: Context): Float = read(1f) {
        LauncherPrefs.BOUNCY_SPEED.get(context) / 100f
    }

    /** Damping ratio for overscroll and other generic bounces. */
    @JvmStatic
    fun bounceDamping(context: Context): Float =
        (1f - 0.45f * strength(context)).coerceIn(0.15f, 1f)

    /** Multiplier for spring stiffness; frequency scales with speed. */
    @JvmStatic
    fun stiffnessScale(context: Context): Float = speed(context).let { it * it }

    /** Damping for the folder open springs (stock is 0.8). */
    @JvmStatic
    fun folderOpenDamping(context: Context): Float =
        (0.8f - 0.18f * strength(context)).coerceIn(0.3f, 0.8f)

    /**
     * How long the popup spring runs.
     *
     * Runs through [Motion] first, so it carries the style and the system animation scale like
     * every other surface, and then through this class's own speed slider, which stays the finer
     * control on top of the style.
     */
    @JvmStatic
    fun popupOpenDuration(context: Context): Int =
        (Motion.duration(context, POPUP_OPEN_DURATION) / speed(context)).roundToInt()

    @JvmStatic
    fun popupOpenInterpolator(context: Context): Interpolator =
        // Damping comes through Motion so the style can add or remove overshoot; the strength
        // slider keeps working on top of it.
        DampedSpringInterpolator(Motion.damping(context, 0.55f), 1.6f)

    /**
     * Home page snap; lands slightly past the target page and settles back.
     *
     * The overshoot comes from the style's bounce as well as the strength slider, and is capped:
     * an overshoot past 25 percent of the tension reads as the page missing its target rather than
     * as a settle.
     */
    @JvmStatic
    fun pageSnapInterpolator(context: Context): Interpolator {
        val styleBounce = Motion.style(context).bounceGain
        val tension = PAGE_SNAP_TENSION * strength(context) * styleBounce
        return OvershootInterpolator(tension.coerceIn(0f, PAGE_SNAP_TENSION * 2.5f))
    }

    /**
     * Multiplier for page snap durations.
     *
     * The system animation scale is applied explicitly here, unlike every other duration in the
     * launcher: the page snap ends up in an [android.widget.OverScroller], which the framework does
     * not scale, so this is the one place that has to do it itself.
     *
     * Floored above zero: the scroller reads 0 as "work out a duration from the distance" rather
     * than "do not animate", so a system scale of 0 would make the snap slower instead of instant.
     */
    @JvmStatic
    fun durationScale(context: Context): Float =
        (Motion.durationScale(context) * Motion.systemScale(context) / speed(context))
            .coerceAtLeast(0.05f)

    private inline fun <T> read(fallback: T, block: () -> T): T =
        try {
            block()
        } catch (_: RuntimeException) {
            // Preview contexts (e.g. the wallpaper picker grid preview) may lack launcher prefs.
            fallback
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

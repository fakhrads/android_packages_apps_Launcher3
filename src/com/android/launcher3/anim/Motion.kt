/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.anim

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.animation.Interpolator
import com.android.launcher3.LauncherPrefs
import kotlin.math.roundToInt

/**
 * One place that decides how everything in the launcher moves.
 *
 * Before this, every surface carried its own numbers: the app drawer opened over 600 ms with a
 * bezier, the long press popup over 200 ms with an overshoot added afterwards, an icon press over
 * 110/170 ms in two separate tweens. Three speeds for three things that should feel like one
 * material, and none of them respected the user's animation speed setting beyond a narrow hook in
 * the page scroller.
 *
 * Everything here answers two questions the same way for every caller:
 *
 *  - how stiff is the spring, which comes from the style the user picked and from
 *    [BouncyAnimations] so the existing strengths keep meaning what they meant;
 *  - how long does it take, which is the surface's own base duration scaled by the response
 *    setting and, on top of that, by the system animation scale.
 *
 * The system scale is what makes the difference outside this app: with animator duration at 0.5x
 * in developer options, the drawer, the popup, the page snap and the icon press all run at half
 * duration without any of them being told separately.
 */
object Motion {

    /**
     * How much overshoot the launcher is willing to show. Calm keeps every curve near critically
     * damped so nothing springs back; expressive lets surfaces carry past their target.
     */
    enum class Style {
        CALM,
        STANDARD,
        EXPRESSIVE;

        /** Multiplier for spring stiffness. Firmer settles faster without changing the shapes. */
        val stiffnessGain: Float
            get() = when (this) {
                CALM -> 0.72f
                STANDARD -> 1f
                EXPRESSIVE -> 1.28f
            }

        /** Multiplier for duration, the inverse of stiffness in feel. */
        val durationGain: Float
            get() = when (this) {
                CALM -> 1.12f
                STANDARD -> 1f
                EXPRESSIVE -> 0.88f
            }

        /** Multiplier for how far a spring is allowed to travel past its target. */
        val bounceGain: Float
            get() = when (this) {
                CALM -> 0.45f
                STANDARD -> 1f
                EXPRESSIVE -> 1.45f
            }

        companion object {
            @JvmStatic
            fun of(name: String?): Style =
                entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: STANDARD
        }
    }

    private const val SYSTEM_KEY = Settings.Global.ANIMATOR_DURATION_SCALE

    /**
     * Cached system animation scale.
     *
     * Settings.Global reads are a binder call, and setPressed runs on every touch of every icon on
     * the workspace, so an uncached read here would put a synchronised IPC in the middle of touch
     * handling. The value is fetched once and kept live by a content observer instead.
     */
    @Volatile private var cachedSystemScale = -1f
    @Volatile private var observerRegistered = false

    /** The style the user picked, read once per call so a change needs no restart. */
    @JvmStatic
    fun style(context: Context): Style = read(Style.STANDARD) {
        Style.of(LauncherPrefs.MOTION_STYLE.get(context))
    }

    /**
     * The system animation speed, from developer options, defaulting to 1.
     *
     * Animator duration scale is the one setting that promises to slow down every animation on the
     * device, so it is the honest lever for "follows the system". A value of 0 turns animation off
     * entirely, which is why the floor is 0 rather than a small number.
     */
    @JvmStatic
    fun systemScale(context: Context): Float {
        val cached = cachedSystemScale
        if (cached >= 0f) return cached
        return read(1f) {
            val value =
                Settings.Global.getFloat(context.contentResolver, SYSTEM_KEY, 1f).coerceIn(0f, 3f)
            cachedSystemScale = value
            watchSystemScale(context)
            value
        }
    }

    /** Keeps [cachedSystemScale] current when developer options change while the launcher runs. */
    private fun watchSystemScale(context: Context) {
        if (observerRegistered) return
        observerRegistered = true
        runCatching {
            val app = context.applicationContext
            app.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(SYSTEM_KEY),
                false,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        cachedSystemScale =
                            Settings.Global.getFloat(app.contentResolver, SYSTEM_KEY, 1f)
                                .coerceIn(0f, 3f)
                    }
                }
            )
        }.onFailure {
            // Without an observer the value simply stays as first read, which is the behaviour
            // before this optimisation rather than a crash on a touch event.
            observerRegistered = false
        }
    }

    /** The user's response setting: 100 is the tuned default, lower is snappier. */
    @JvmStatic
    fun response(context: Context): Float = read(1f) {
        LauncherPrefs.MOTION_RESPONSE.get(context) / 100f
    }

    /**
     * Combined multiplier a caller applies to a base duration in milliseconds.
     *
     * Deliberately excludes the system animation scale: every duration handed to a
     * [android.animation.ValueAnimator] is already multiplied by it by the framework, so folding it
     * in here would square it — a device on 0.5x would animate at 0.25x. [systemScale] is for the
     * callers that drive a [android.widget.OverScroller] instead, which the framework does not
     * touch.
     */
    @JvmStatic
    fun durationScale(context: Context): Float =
        (response(context) * style(context).durationGain).coerceIn(0f, 4f)

    /**
     * A base duration in ms, scaled for the current style and settings.
     *
     * Returns 0 when the system asks for no animation, so callers can hand the result straight to
     * an animator without a second check.
     */
    @JvmStatic
    fun duration(context: Context, baseMs: Int): Int =
        (baseMs * durationScale(context)).roundToInt().coerceAtLeast(0)

    /**
     * Spring stiffness for a caller that has its own tuned base.
     *
     * A spring has no duration of its own, so the response setting reaches it as stiffness: asking
     * for a snappier launcher multiplies the frequency, which is what makes a real spring settle
     * sooner, rather than shortening a curve after the fact.
     */
    @JvmStatic
    fun stiffness(context: Context, base: Float): Float {
        val responseGain = 1f / response(context).coerceAtLeast(0.25f)
        return base * style(context).stiffnessGain * BouncyAnimations.stiffnessScale(context) *
                responseGain
    }

    /**
     * Damping ratio for a caller that has its own tuned base, never outside the range the spring
     * solver accepts.
     *
     * A style that allows bounce multiplies the baseline bounce of [BouncyAnimations], so the
     * strength slider keeps working in every style instead of being overridden by it.
     */
    @JvmStatic
    fun damping(context: Context, base: Float): Float {
        val bounce = BouncyAnimations.strength(context) * style(context).bounceGain
        val damped = base / bounce.coerceAtLeast(0.25f)
        return damped.coerceIn(0.15f, 0.99f)
    }

    /**
     * How far to blur the surface behind a floating element (a folder, the long press popup, the
     * app drawer), as a multiplier of the radius a surface already uses.
     *
     * Before this, the "background blur radius" slider only reached the wallpaper blur in overview:
     * folders and the popup kept a fixed 20 dp each and the drawer had no radius of its own, so
     * moving the slider changed one of the four surfaces that read as blurred and it was not
     * possible to make the launcher consistently soft or consistently crisp.
     *
     * The default of 60 in the preference is the calibration point, so an untouched device keeps
     * exactly the radii it shipped with.
     */
    @JvmStatic
    fun blurScale(context: Context): Float = read(1f) {
        (LauncherPrefs.BLUR_DEPTH.get(context) / 60f).coerceIn(0f, 3f)
    }

    /** A fixed-duration spring curve, for animators that cannot run a real spring. */
    @JvmStatic
    fun interpolator(context: Context, baseDamping: Float = 0.55f, cycles: Float = 1.7f): Interpolator =
        DampedSpringInterpolator(damping(context, baseDamping), cycles)

    private inline fun <T> read(fallback: T, block: () -> T): T =
        try {
            block()
        } catch (_: RuntimeException) {
            // Preview and widget-picker contexts have no launcher prefs; a crash there would take
            // the picker down for a motion curve.
            fallback
        }
}

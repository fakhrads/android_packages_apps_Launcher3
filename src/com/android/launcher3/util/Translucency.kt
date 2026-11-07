/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.util

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.android.launcher3.LauncherPrefs

/**
 * One knob behind every translucent surface in the launcher.
 *
 * The launcher already had a separate opacity for the app drawer, the hotseat and recents, which
 * is right: each surface has its own legibility budget. What it did not have was a way to say "I
 * want this phone to feel lighter" without visiting three screens and finding three numbers by
 * eye. [scale] is that statement: 100 leaves every surface exactly at its own setting, below 100
 * makes them all more see-through, above 100 makes them all more solid, and 0 makes the launcher
 * opaque — which is also the honest reading of "no transparency".
 *
 * The per-surface values are still applied on top, so the relationship between the drawer and the
 * hotseat never inverts; only the whole scale moves.
 */
object Translucency {

    /** The tuned default: no change to any surface. */
    const val DEFAULT = 100

    @JvmStatic
    fun scale(context: Context): Float = try {
        (LauncherPrefs.SURFACE_TRANSLUCENCY.get(context) / DEFAULT.toFloat()).coerceIn(0f, 1.5f)
    } catch (_: RuntimeException) {
        // Preview and picker contexts have no launcher prefs.
        1f
    }

    /**
     * Applies the scale to a 0..100 per-surface opacity and returns the result, still 0..100.
     *
     * Clamped rather than wrapped: an opacity above 100 is not a thing a view can draw, so asking
     * for more than full opacity simply gives full opacity instead of wrapping around to nothing.
     */
    @JvmStatic
    fun opacity(context: Context, perSurface: Int): Int =
        (perSurface * scale(context)).toInt().coerceIn(0, 100)

    /** Same, for a colour's alpha channel. */
    @JvmStatic
    @JvmOverloads
    fun apply(context: Context, color: Int, perSurface: Int = 100): Int =
        ColorUtils.setAlphaComponent(color, opacity(context, perSurface) * 255 / 100)

    /**
     * How visible a scrim should be, given the per-surface opacity.
     *
     * A scrim exists to keep text legible over whatever is behind it. If the surface itself is
     * nearly transparent, a heavy scrim makes the transparency pointless; if the surface is nearly
     * opaque, no scrim is needed. This keeps the two moving together instead of leaving the scrim
     * pinned at full strength while the user drags the opacity to zero.
     */
    @JvmStatic
    fun scrimAlpha(context: Context, perSurface: Int): Float {
        val opacity = opacity(context, perSurface) / 100f
        return (1f - opacity).coerceIn(0f, 1f) * 0.35f
    }

    /** Fully transparent, for callers that want to skip drawing at zero opacity. */
    @JvmStatic
    fun isInvisible(context: Context, perSurface: Int): Boolean =
        Color.alpha(apply(context, Color.BLACK, perSurface)) == 0
}

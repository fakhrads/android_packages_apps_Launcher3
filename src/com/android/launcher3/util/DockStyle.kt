/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import kotlin.math.roundToInt

/**
 * Presentation styles for the home screen dock and search bar.
 *
 * - [DEFAULT]: stock Pixel full-width search bar below the dock icons.
 * - [GLASS]: iOS 26 / Liquid Glass floating capsule dock + compact search pill above it,
 *   with crisp lighter live blur and a luminous specular glass rim.
 * - [BLUR]: iOS 18 frosted blur floating capsule dock + compact search pill above it,
 *   with heavier soft frosted backdrop blur and borderless frosted tint.
 */
object DockStyle {

    const val DEFAULT = "default"
    const val GLASS = "glass"
    const val BLUR = "blur"

    @JvmField
    val ALL: Set<String> = setOf(DEFAULT, GLASS, BLUR)

    @JvmStatic
    fun get(context: Context): String {
        val style = try {
            LauncherPrefs.DOCK_STYLE.get(context)
        } catch (_: RuntimeException) {
            DEFAULT
        }
        return if (isSupported(style)) style else DEFAULT
    }

    @JvmStatic
    fun isSupported(style: String?): Boolean = style != null && style in ALL

    @JvmStatic
    fun isGlass(style: String?): Boolean = GLASS == style

    @JvmStatic
    fun isGlass(context: Context): Boolean = isGlass(get(context))

    @JvmStatic
    fun isBlur(style: String?): Boolean = BLUR == style

    @JvmStatic
    fun isBlur(context: Context): Boolean = isBlur(get(context))

    /** Both [GLASS] and [BLUR] use the floating capsule dock + compact top search pill layout. */
    @JvmStatic
    fun isPill(style: String?): Boolean = GLASS == style || BLUR == style

    @JvmStatic
    fun isPill(context: Context): Boolean = isPill(get(context))

    /** True when the wallpaper behind the home screen is light and needs dark text/glyphs. */
    @JvmStatic
    fun isLightWallpaper(context: Context): Boolean =
        Themes.getAttrBoolean(context, R.attr.isWorkspaceDarkText)

    /** Text and search icon color for the compact search pill. */
    @JvmStatic
    @ColorInt
    fun pillContentColor(context: Context): Int =
        if (isLightWallpaper(context)) 0xE6111318.toInt() else 0xF2FFFFFF.toInt()

    /** Base unscaled blur radius in pixels for [style]. */
    @JvmStatic
    fun baseBlurRadiusPx(context: Context, style: String): Int {
        val dimen = if (isBlur(style)) R.dimen.dock_pill_blur_radius
        else R.dimen.dock_pill_glass_blur_radius
        return context.resources.getDimensionPixelSize(dimen)
    }

    /**
     * Builds the translucent surface layer (fill + specular rim for [GLASS], frosted fill only
     * for [BLUR]) drawn above the cross-window blur drawable (or standalone when blur is off).
     */
    @JvmStatic
    fun createSurfaceDrawable(
        context: Context,
        style: String,
        cornerRadiusPx: Float,
        blurEnabled: Boolean,
    ): Drawable {
        val lightWallpaper = isLightWallpaper(context)
        val fillColor = resolveFillColor(context, style, lightWallpaper, blurEnabled)
        val fill = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(fillColor)
        }
        if (!isGlass(style)) {
            return fill
        }
        val rim = GlassRimDrawable(
            strokeWidthPx = context.resources.displayMetrics.density * 1f,
            cornerRadiusPx = cornerRadiusPx,
            lightWallpaper = lightWallpaper,
        )
        return LayerDrawable(arrayOf(fill, rim))
    }

    /** Updates the corner radius on a drawable returned by [createSurfaceDrawable] or a wrapper. */
    @JvmStatic
    fun updateSurfaceCornerRadius(drawable: Drawable?, cornerRadiusPx: Float) {
        when (drawable) {
            is GradientDrawable -> drawable.cornerRadius = cornerRadiusPx
            is GlassRimDrawable -> drawable.setCornerRadius(cornerRadiusPx)
            is LayerDrawable -> {
                for (i in 0 until drawable.numberOfLayers) {
                    updateSurfaceCornerRadius(drawable.getDrawable(i), cornerRadiusPx)
                }
            }
        }
    }

    @ColorInt
    private fun resolveFillColor(
        context: Context,
        style: String,
        lightWallpaper: Boolean,
        blurEnabled: Boolean,
    ): Int {
        val baseColor: Int
        val baseAlphaPct: Int
        if (isBlur(style)) {
            // iOS 18 frosted blur: milkier, softer translucent tint.
            if (lightWallpaper) {
                baseColor = 0xFFF4F5F8.toInt()
                baseAlphaPct = if (blurEnabled) 50 else 78
            } else {
                baseColor = 0xFF24262C.toInt()
                baseAlphaPct = if (blurEnabled) 46 else 74
            }
        } else {
            // Liquid Glass: clearer, crisper low-opacity glass tint so wallpaper glows through.
            if (lightWallpaper) {
                baseColor = Color.WHITE
                baseAlphaPct = if (blurEnabled) 26 else 60
            } else {
                baseColor = 0xFF121418.toInt()
                baseAlphaPct = if (blurEnabled) 28 else 58
            }
        }
        val scaledPct = Translucency.opacity(context, baseAlphaPct)
        val alpha = (scaledPct * 255f / 100f).roundToInt().coerceIn(0, 255)
        return ColorUtils.setAlphaComponent(baseColor, alpha)
    }

    /**
     * Draws a crisp specular glass rim stroke around a capsule pill with a vertical light-catching
     * gradient cached on bounds changes (zero per-frame allocations).
     */
    class GlassRimDrawable(
        private val strokeWidthPx: Float,
        private var cornerRadiusPx: Float,
        private val lightWallpaper: Boolean,
    ) : Drawable() {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
        }
        private val rect = RectF()
        private var currentAlpha = 255

        fun setCornerRadius(radiusPx: Float) {
            if (cornerRadiusPx != radiusPx) {
                cornerRadiusPx = radiusPx
                invalidateSelf()
            }
        }

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            val inset = strokeWidthPx / 2f
            rect.set(
                bounds.left + inset,
                bounds.top + inset,
                bounds.right - inset,
                bounds.bottom - inset,
            )
            if (rect.height() > 0f) {
                val colors = if (lightWallpaper) {
                    intArrayOf(0xB3FFFFFF.toInt(), 0x4DFFFFFF, 0x29000000)
                } else {
                    intArrayOf(0x66FFFFFF, 0x24FFFFFF, 0x42FFFFFF)
                }
                paint.shader = LinearGradient(
                    0f,
                    rect.top,
                    0f,
                    rect.bottom,
                    colors,
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
        }

        override fun draw(canvas: Canvas) {
            if (rect.width() <= 0f || rect.height() <= 0f || currentAlpha == 0) return
            val clampedRadius = cornerRadiusPx.coerceAtMost(minOf(rect.width(), rect.height()) / 2f)
            canvas.drawRoundRect(rect, clampedRadius, clampedRadius, paint)
        }

        override fun setAlpha(alpha: Int) {
            currentAlpha = alpha
            paint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}

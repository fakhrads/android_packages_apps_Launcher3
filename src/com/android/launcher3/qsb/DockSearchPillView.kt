/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.qsb

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.android.launcher3.R
import com.android.launcher3.Reorderable
import com.android.launcher3.util.DockStyle
import com.android.launcher3.util.MultiTranslateDelegate

/**
 * Compact floating search pill rendered above the capsule dock in [DockStyle.GLASS] and
 * [DockStyle.BLUR] modes. Tapping opens the same drawer search or search app as [QsbLayout].
 */
class DockSearchPillView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr), Reorderable {

    private val translateDelegate = MultiTranslateDelegate(this)
    private var scaleForReorderBounce = 1f
    private var pressed = false

    override fun onFinishInflate() {
        super.onFinishInflate()
        val contentColor = DockStyle.pillContentColor(context)
        findViewById<ImageView>(R.id.dock_search_pill_icon)?.apply {
            imageTintList = ColorStateList.valueOf(contentColor)
        }
        findViewById<TextView>(R.id.dock_search_pill_label)?.apply {
            setTextColor(contentColor)
        }
        setOnClickListener {
            QsbLayout.openSearch(context)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> setPressedVisual(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> setPressedVisual(false)
        }
        return super.onTouchEvent(event)
    }

    private fun setPressedVisual(isPressedNow: Boolean) {
        if (pressed == isPressedNow) return
        pressed = isPressedNow
        val target = if (isPressedNow) 0.96f else 1f
        animate()
            .scaleX(target * scaleForReorderBounce)
            .scaleY(target * scaleForReorderBounce)
            .setDuration(if (isPressedNow) 80L else 140L)
            .start()
    }

    override fun getTranslateDelegate(): MultiTranslateDelegate = translateDelegate

    override fun setReorderBounceScale(scale: Float) {
        scaleForReorderBounce = scale
        super.setScaleX(scale)
        super.setScaleY(scale)
    }

    override fun getReorderBounceScale(): Float = scaleForReorderBounce
}

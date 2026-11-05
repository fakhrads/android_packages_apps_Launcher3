/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.anim

import android.content.Context
import android.graphics.Canvas
import android.widget.EdgeEffect
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.util.EdgeEffectCompat
import java.util.function.IntSupplier
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Overscroll effect that moves the content with rubber band resistance and springs it back,
 * instead of drawing the platform stretch. Keeps the [EdgeEffect] pull/absorb/release contract so
 * PagedView and RecyclerView drive it unchanged; the owner renders [OffsetListener] values.
 *
 * @param direction sign of the content offset while this edge is pulled (+1 away from the edge)
 * @param size length of the scrolling axis in px, used to scale the pull
 */
class BounceEdgeEffect(
    context: Context,
    private val direction: Float,
    private val size: IntSupplier,
    private val listener: OffsetListener,
) : EdgeEffectCompat(context) {

    fun interface OffsetListener {
        fun onOffsetChanged(offset: Float)
    }

    private var pullDistance = 0f
    private var offset = 0f

    // Sliders are read once; changing them restarts the launcher.
    private val strength = BouncyAnimations.strength(context)
    private val stiffness = STIFFNESS * BouncyAnimations.stiffnessScale(context)
    private val maxOffsetFraction = (MAX_OFFSET_FRACTION * strength).coerceAtMost(0.3f)

    private val springAnim =
        SpringAnimation(FloatValueHolder()).apply {
            spring =
                SpringForce(0f)
                    .setStiffness(stiffness)
                    .setDampingRatio(BouncyAnimations.bounceDamping(context))
            addUpdateListener { _, value, _ -> setOffset(value) }
            // Report once more after the spring ends, so the owner sees isFinished() == true.
            addEndListener { _, _, _, _ -> listener.onOffsetChanged(offset) }
        }

    private fun maxOffset() = max(size.asInt, 1) * maxOffsetFraction

    private fun offsetFor(distance: Float) =
        direction * maxOffset() * (1f - exp(-RESISTANCE * distance))

    private fun distanceFor(value: Float): Float {
        val ratio = min(abs(value) / maxOffset(), 0.99f)
        return -ln(1f - ratio) / RESISTANCE
    }

    private fun setOffset(value: Float) {
        offset = value
        listener.onOffsetChanged(value)
    }

    override fun onPullDistance(deltaDistance: Float, displacement: Float): Float {
        if (springAnim.isRunning) {
            // Catch the content mid-bounce and keep pulling from where it is.
            springAnim.cancel()
            pullDistance = if (offset * direction > 0f) distanceFor(offset) else 0f
        }
        val consumed = if (deltaDistance >= 0f) deltaDistance else max(deltaDistance, -pullDistance)
        pullDistance += consumed
        setOffset(offsetFor(pullDistance))
        return consumed
    }

    override fun onPull(deltaDistance: Float) {
        onPullDistance(deltaDistance, 0.5f)
    }

    override fun onPull(deltaDistance: Float, displacement: Float) {
        onPullDistance(deltaDistance, displacement)
    }

    override fun getDistance(): Float = pullDistance

    override fun onRelease() {
        if (pullDistance != 0f || offset != 0f) {
            pullDistance = 0f
            startSpring(0f)
        }
    }

    override fun onAbsorb(velocity: Int) {
        pullDistance = 0f
        // Peak displacement of the spring is roughly v / omega; cap it to the pull limit.
        val maxVelocity = maxOffset() * sqrt(stiffness)
        startSpring(direction * min(abs(velocity) * ABSORB_VELOCITY_SCALE * strength, maxVelocity))
    }

    override fun onFlingVelocity(velocity: Int) {}

    override fun isFinished(): Boolean = pullDistance == 0f && !springAnim.isRunning

    override fun finish() {
        springAnim.cancel()
        pullDistance = 0f
        setOffset(0f)
    }

    override fun draw(canvas: Canvas): Boolean = false

    private fun startSpring(velocity: Float) {
        springAnim.cancel()
        springAnim.setStartValue(offset)
        springAnim.setStartVelocity(velocity)
        springAnim.start()
    }

    companion object {
        // Values at 100% strength and speed.
        private const val STIFFNESS = 320f
        private const val MAX_OFFSET_FRACTION = 0.15f
        private const val RESISTANCE = 3f
        private const val ABSORB_VELOCITY_SCALE = 0.25f
    }
}

/** [RecyclerView.EdgeEffectFactory] that bounces the list by translating it. */
class BounceEdgeEffectFactory : RecyclerView.EdgeEffectFactory() {
    override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
        val vertical = direction == DIRECTION_TOP || direction == DIRECTION_BOTTOM
        val sign = if (direction == DIRECTION_TOP || direction == DIRECTION_LEFT) 1f else -1f
        return BounceEdgeEffect(
            view.context,
            sign,
            { if (vertical) view.height else view.width },
        ) { offset ->
            if (vertical) view.translationY = offset else view.translationX = offset
        }
    }
}

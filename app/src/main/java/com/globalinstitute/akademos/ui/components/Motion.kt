package com.globalinstitute.akademos.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import kotlinx.coroutines.launch
import kotlin.math.PI

/**
 * One motion vocabulary for the whole app, in Apple's terms: a spring is described by its
 * damping ratio (1 = no overshoot) and its response (roughly how long it takes to get there).
 * Springs start from wherever the thing is right now, so every animation can be interrupted.
 */
object Motion {
    /** Response in seconds → Compose stiffness. */
    fun stiffness(response: Float): Float = ((2 * PI / response) * (2 * PI / response)).toFloat()

    fun <T> spring(damping: Float = 1f, response: Float = 0.4f, threshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = damping, stiffness = stiffness(response), visibilityThreshold = threshold)

    /** Default for things moving on screen: settles smoothly, never bounces. */
    val Default: SpringSpec<Float> = spring(1f, 0.4f)
    /** Feedback the instant a finger lands. */
    val Press: SpringSpec<Float> = spring(1f, 0.12f)
    /** Letting go: a touch of life, because the finger just gave it energy. */
    val Release: SpringSpec<Float> = spring(0.72f, 0.32f)
    /** A tap that changed something (a mark, a tick appearing). */
    val Pop: SpringSpec<Float> = spring(0.6f, 0.3f)
    /** Whole screens. */
    val Page: FiniteAnimationSpec<IntOffset> = spring(1f, 0.42f, IntOffset(1, 1))
    val PageFade: FiniteAnimationSpec<Float> = spring(1f, 0.3f)
}

/** True when the phone's "Remove animations" (or animator scale 0) is on: use cross-fades, not slides. */
val LocalReduceMotion = compositionLocalOf { false }

@Composable
fun rememberReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
}

/**
 * Shrinks the element the moment a finger touches it (not after the tap is recognised) and
 * springs it back on release or cancel. Doesn't consume anything, so clicks still work.
 *
 * Written as a Modifier.Node: no per-item composition cost in long lists, and the element only
 * gets its own drawing layer while it is actually scaled, so idle cards cost nothing to draw.
 */
fun Modifier.pressScale(pressed: Float = 0.96f, enabled: Boolean = true): Modifier =
    if (!enabled) this else this.then(PressScaleElement(pressed))

private data class PressScaleElement(val target: Float) : ModifierNodeElement<PressScaleNode>() {
    override fun create() = PressScaleNode(target)
    override fun update(node: PressScaleNode) { node.target = target }
}

private class PressScaleNode(var target: Float) : Modifier.Node(), PointerInputModifierNode, LayoutModifierNode {
    private val scale = Animatable(1f)
    private var down = false

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Final) return  // after scrolling has had its say, so a scroll cancels the press
        val anyPressed = pointerEvent.changes.any { it.pressed }
        when {
            !down && pointerEvent.type == PointerEventType.Press -> { down = true; animate(target, Motion.Press) }
            down && (!anyPressed || pointerEvent.changes.any { it.isConsumed && it.positionChanged() }) -> { down = false; animate(1f, Motion.Release) }
        }
    }

    override fun onCancelPointerInput() { if (down) { down = false; animate(1f, Motion.Release) } }

    private fun animate(to: Float, spec: androidx.compose.animation.core.AnimationSpec<Float>) {
        coroutineScope.launch { scale.animateTo(to, spec) { invalidatePlacement() } }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val p = measurable.measure(constraints)
        return layout(p.width, p.height) {
            val v = scale.value
            if (v == 1f) p.place(0, 0) else p.placeWithLayer(0, 0) { scaleX = v; scaleY = v }
        }
    }
}

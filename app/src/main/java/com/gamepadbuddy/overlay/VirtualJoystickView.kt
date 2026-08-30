package com.gamepadbuddy.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.hypot

/**
 * Widget joystick ảo (file 03 - Bước 3).
 * - Play Mode: đầu vào từ gamepad (file 02) → gọi thẳng daemon tiêm toạ độ vào vị trí thật
 *   của joystick trong GAME (không vẽ chạm thật của người dùng lên overlay).
 * - Edit Mode: kéo để đổi vị trí; cũng có thể bấm/keo bằng ngón tay (hoạt động như joystick cảm ứng).
 */
class VirtualJoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var editMode: Boolean = false
    var onMove: ((normX: Float, normY: Float) -> Unit)? = null
    var onRelease: (() -> Unit)? = null
    var onPositionChanged: ((Int, Int) -> Unit)? = null

    private val radius = 80f
    private var knobX = 0f
    private var knobY = 0f
    private var dragging = false
    private var lastX = 0f
    private var lastY = 0f

    private val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255) }
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160, 80, 200, 255) }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.YELLOW; strokeWidth = 4f
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val d = (radius * 2 + 20).toInt()
        setMeasuredDimension(d, d)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        canvas.drawCircle(cx, cy, radius, base)
        if (editMode) canvas.drawCircle(cx, cy, radius, border)
        canvas.drawCircle(cx + knobX, cy + knobY, radius / 2.5f, knob)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cx = width / 2f; val cy = height / 2f
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                if (editMode) { lastX = event.rawX; lastY = event.rawY; return true }
                updateKnob(event, cx, cy)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (editMode && dragging) {
                    val dx = (event.rawX - lastX).toInt()
                    val dy = (event.rawY - lastY).toInt()
                    lastX = event.rawX; lastY = event.rawY
                    val lp = layoutParams as? FrameLayout.LayoutParams ?: return true
                    lp.leftMargin += dx; lp.topMargin += dy
                    layoutParams = lp
                    onPositionChanged?.invoke(lp.leftMargin, lp.topMargin)
                    parent?.requestLayout()
                    return true
                }
                if (dragging) { updateKnob(event, cx, cy); return true }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                knobX = 0f; knobY = 0f
                onRelease?.invoke()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateKnob(event: MotionEvent, cx: Float, cy: Float) {
        var dx = event.x - cx
        var dy = event.y - cy
        val dist = hypot(dx, dy)
        if (dist > radius) { dx = dx / dist * radius; dy = dy / dist * radius }
        knobX = dx; knobY = dy
        onMove?.invoke(dx / radius, dy / radius)
        invalidate()
    }
}

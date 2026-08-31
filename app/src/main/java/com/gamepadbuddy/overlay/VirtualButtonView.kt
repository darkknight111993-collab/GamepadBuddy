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

/**
 * Widget nút ảo (file 03 - Bước 3).
 * - Ở Play Mode: chỉ là marker; gamepad bấm nút tương ứng sẽ tiêm chạm qua daemon (file 05).
 * - Ở Edit Mode: kéo-thả để đổi vị trí, cập nhật toạ độ vào Profile.
 */
class VirtualButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var label: String = ""
    var editMode: Boolean = false
    var onPositionChanged: ((Int, Int) -> Unit)? = null
    /** Chạm (không kéo) ở Edit Mode -> mở panel gán phím (xem FloatingBindOverlay). */
    var onTap: (() -> Unit)? = null

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 60, 120, 255) }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.YELLOW; strokeWidth = 4f
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 36f
    }

    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var moved = false

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension(120, 120)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f; val r = width / 2f - 4f
        canvas.drawCircle(cx, cy, r, bg)
        if (editMode) canvas.drawCircle(cx, cy, r, border)
        canvas.drawText(label, cx, cy + 12f, text)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!editMode) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.rawX; lastY = event.rawY
                downX = event.rawX; downY = event.rawY
                moved = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - lastX).toInt()
                val dy = (event.rawY - lastY).toInt()
                lastX = event.rawX; lastY = event.rawY
                if (kotlin.math.abs(event.rawX - downX) > 12 || kotlin.math.abs(event.rawY - downY) > 12) {
                    moved = true
                }
                val lp = layoutParams as? FrameLayout.LayoutParams ?: return true
                lp.leftMargin += dx; lp.topMargin += dy
                layoutParams = lp
                onPositionChanged?.invoke(lp.leftMargin, lp.topMargin)
                parent?.requestLayout()
            }
            MotionEvent.ACTION_UP -> {
                // Chạm mà không kéo -> coi là tap, mở panel gán phím.
                if (!moved) { onTap?.invoke(); performClick() }
            }
        }
        return true
    }
}

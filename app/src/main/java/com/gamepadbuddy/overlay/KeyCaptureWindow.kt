package com.gamepadbuddy.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * Cửa sổ ẩn 1x1px, focusable, để "hứng" KeyEvent/axis từ tay cầm (file 02 - lưu ý focus).
 *
 * Overlay chính dùng FLAG_NOT_FOCUSABLE nên KHÔNG nhận được KeyEvent (để game bên dưới vẫn
 * nhận touch bình thường). KeyEvent đi theo focus, touch đi theo toạ độ — nên tách riêng cửa
 * sổ nhỏ này chỉ để bắt input tay cầm, không xung đột với game.
 */
class KeyCaptureWindow(
    private val context: Context,
    private val onButton: (Int, Boolean) -> Unit,
    private val onAxis: (Float, Float) -> Unit
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val view = object : View(context) {
        override fun dispatchKeyEvent(e: KeyEvent): Boolean {
            if (isGamepadSource(e.source)) {
                onButton(e.keyCode, e.action == KeyEvent.ACTION_DOWN)
                return true
            }
            return super.dispatchKeyEvent(e)
        }

        override fun dispatchGenericMotionEvent(e: MotionEvent): Boolean {
            if (e.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) {
                onAxis(e.getAxisValue(MotionEvent.AXIS_X), e.getAxisValue(MotionEvent.AXIS_Y))
                return true
            }
            return super.dispatchGenericMotionEvent(e)
        }
    }

    private val params = WindowManager.LayoutParams(
        1, 1,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // focusable (KHÔNG set FLAG_NOT_FOCUSABLE) + không chặn touch (FLAG_NOT_TOUCHABLE)
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSPARENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    fun show() { runCatching { wm.addView(view, params) } }
    fun hide() { runCatching { wm.removeView(view) } }

    private fun isGamepadSource(source: Int): Boolean =
        source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
        source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
}

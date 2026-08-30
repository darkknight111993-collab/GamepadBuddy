package com.gamepadbuddy.input

import android.content.Context
import android.util.AttributeSet
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View

/**
 * View bắt sự kiện tay cầm, dùng cho lớp overlay sau này (file 02 - Bước 1).
 *
 * Lưu ý cốt lõi: 1 View overlay thường (TYPE_APPLICATION_OVERLAY) không tự nhận KeyEvent
 * trừ khi có focusable và hệ thống cấp focus — nhưng nếu chiếm focus, người dùng sẽ không
 * thao tác được game bên dưới. Cách xử lý (sẽ làm ở file 03): tạo riêng 1 cửa sổ ẩn 1x1px,
 * focusable=true, type=TYPE_APPLICATION_OVERLAY ở góc màn hình chỉ để "hứng" KeyEvent,
 * vì KeyEvent đi theo focus, còn touch đi theo toạ độ nên không xung đột.
 */
class GamepadInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var listener: ((GamepadEvent) -> Unit)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isGamepadSource(event.source)) {
            val down = event.action == KeyEvent.ACTION_DOWN
            listener?.invoke(GamepadEvent.Button(event.keyCode, down))
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) {
            val lx = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_X))
            val ly = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_Y))
            val rx = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_Z))
            val ry = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_RZ))
            val lt = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_LTRIGGER))
            val rt = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_RTRIGGER))
            listener?.invoke(GamepadEvent.Axis(lx, ly, rx, ry, lt, rt))
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun isGamepadSource(source: Int): Boolean =
        source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
        source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
}

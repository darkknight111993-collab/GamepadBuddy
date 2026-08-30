package com.gamepadbuddy.input

import android.view.KeyEvent

/**
 * Model chuẩn hoá sự kiện tay cầm dùng chung trong toàn app (theo file 02 - Bước 2).
 */
sealed class GamepadEvent {
    data class Button(val keyCode: Int, val isDown: Boolean) : GamepadEvent()
    data class Axis(
        val lx: Float, val ly: Float,
        val rx: Float, val ry: Float,
        val lt: Float, val rt: Float
    ) : GamepadEvent()
}

/**
 * Danh sách keyCode tối thiểu cần hỗ trợ (Android KeyEvent chuẩn).
 */
object GamepadKeys {
    val SUPPORTED: Set<Int> = setOf(
        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_X,
        KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_BUTTON_L1,
        KeyEvent.KEYCODE_BUTTON_R1,
        KeyEvent.KEYCODE_BUTTON_L2,
        KeyEvent.KEYCODE_BUTTON_R2,
        KeyEvent.KEYCODE_BUTTON_THUMBL,
        KeyEvent.KEYCODE_BUTTON_THUMBR,
        KeyEvent.KEYCODE_BUTTON_START,
        KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT
    )
}

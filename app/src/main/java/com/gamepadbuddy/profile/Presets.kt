package com.gamepadbuddy.profile

import android.content.Context
import android.view.KeyEvent

/**
 * Preset mẫu (file 06 - Bước 4). Toạ độ định nghĩa trong không gian 1080x2400,
 * sau đó scale sang px thật theo màn hình thiết bị khi tạo Profile.
 */
object Presets {

    fun moba(context: Context): Profile {
        val dm = context.resources.displayMetrics
        val sx = dm.widthPixels / 1080f
        val sy = dm.heightPixels / 2400f
        val left = MappedWidget.Joystick("js_left", 260f * sx, 1750f * sy, 130f * ((sx + sy) / 2), AxisGroup.LEFT_STICK)
        val a = MappedWidget.Button("btn_a", 860f * sx, 1780f * sy, KeyEvent.KEYCODE_BUTTON_A)
        val b = MappedWidget.Button("btn_b", 960f * sx, 1680f * sy, KeyEvent.KEYCODE_BUTTON_B)
        val x = MappedWidget.Button("btn_x", 760f * sx, 1680f * sy, KeyEvent.KEYCODE_BUTTON_X)
        val y = MappedWidget.Button("btn_y", 960f * sx, 1580f * sy, KeyEvent.KEYCODE_BUTTON_Y)
        return Profile("preset_moba", "", "MOBA preset", listOf(left, a, b, x, y))
    }

    fun fps(context: Context): Profile {
        val dm = context.resources.displayMetrics
        val sx = dm.widthPixels / 1080f
        val sy = dm.heightPixels / 2400f
        val left = MappedWidget.Joystick("js_left", 260f * sx, 1750f * sy, 130f * ((sx + sy) / 2), AxisGroup.LEFT_STICK)
        val right = MappedWidget.Joystick("js_right", 820f * sx, 1750f * sy, 120f * ((sx + sy) / 2), AxisGroup.RIGHT_STICK)
        val fire = MappedWidget.Button("btn_fire", 960f * sx, 1650f * sy, KeyEvent.KEYCODE_BUTTON_A)
        val jump = MappedWidget.Button("btn_jump", 760f * sx, 1650f * sy, KeyEvent.KEYCODE_BUTTON_B)
        return Profile("preset_fps", "", "FPS preset", listOf(left, right, fire, jump))
    }
}

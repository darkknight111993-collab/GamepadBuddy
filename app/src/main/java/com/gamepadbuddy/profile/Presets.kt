package com.gamepadbuddy.profile

import android.content.Context
import android.view.KeyEvent

/**
 * Preset mẫu (file 06 - Bước 4).
 *
 * Bug fix: bản cũ tính toạ độ theo canvas GỐC 1080x2400 (dọc) rồi scale bằng
 * displayMetrics.widthPixels/heightPixels TẠI THỜI ĐIỂM bấm "Add game" — thời điểm đó app
 * đang ở chế độ DỌC, nên toạ độ được lưu vào Profile là toạ độ cho không gian dọc.
 * Nhưng widget lại được vẽ ra (OverlayRootView) khi GAME đang chạy — luôn ở chế độ NGANG.
 * Kết quả: joystick/nút bị lệch hẳn vị trí, có khi nằm ngoài màn hình (vd y=1750 trong khi
 * chiều cao thật lúc ngang chỉ còn ~1080px).
 *
 * Cách sửa: luôn build preset theo canvas NGANG (2400x1080), lấy max/min của
 * widthPixels/heightPixels để suy ra đúng cạnh dài/cạnh ngắn vật lý của máy — không phụ
 * thuộc app đang ở chế độ dọc hay ngang lúc tạo profile.
 */
object Presets {

    private const val BASE_W = 2400f // canvas ngang, cạnh dài
    private const val BASE_H = 1080f // canvas ngang, cạnh ngắn

    private fun landscapeScale(context: Context): Pair<Float, Float> {
        val dm = context.resources.displayMetrics
        val landscapeW = maxOf(dm.widthPixels, dm.heightPixels).toFloat()
        val landscapeH = minOf(dm.widthPixels, dm.heightPixels).toFloat()
        return (landscapeW / BASE_W) to (landscapeH / BASE_H)
    }

    fun moba(context: Context): Profile {
        val (sx, sy) = landscapeScale(context)
        val left = MappedWidget.Joystick("js_left", 300f * sx, 760f * sy, 130f * ((sx + sy) / 2), AxisGroup.LEFT_STICK)
        val a = MappedWidget.Button("btn_a", 2080f * sx, 880f * sy, KeyEvent.KEYCODE_BUTTON_A)
        val b = MappedWidget.Button("btn_b", 2200f * sx, 760f * sy, KeyEvent.KEYCODE_BUTTON_B)
        val x = MappedWidget.Button("btn_x", 1960f * sx, 760f * sy, KeyEvent.KEYCODE_BUTTON_X)
        val y = MappedWidget.Button("btn_y", 2080f * sx, 640f * sy, KeyEvent.KEYCODE_BUTTON_Y)
        return Profile("preset_moba", "", "MOBA preset", listOf(left, a, b, x, y))
    }

    fun fps(context: Context): Profile {
        val (sx, sy) = landscapeScale(context)
        val left = MappedWidget.Joystick("js_left", 300f * sx, 760f * sy, 130f * ((sx + sy) / 2), AxisGroup.LEFT_STICK)
        val right = MappedWidget.Joystick("js_right", 2050f * sx, 700f * sy, 120f * ((sx + sy) / 2), AxisGroup.RIGHT_STICK)
        val fire = MappedWidget.Button("btn_fire", 2200f * sx, 650f * sy, KeyEvent.KEYCODE_BUTTON_A)
        val jump = MappedWidget.Button("btn_jump", 1950f * sx, 650f * sy, KeyEvent.KEYCODE_BUTTON_B)
        return Profile("preset_fps", "", "FPS preset", listOf(left, right, fire, jump))
    }
}

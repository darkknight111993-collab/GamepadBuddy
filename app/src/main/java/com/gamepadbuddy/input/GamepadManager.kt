package com.gamepadbuddy.input

import android.view.InputDevice

/**
 * Liệt kê các tay cầm đang kết nối (file 02 - Bước 4).
 * Dùng để hiển thị "N Gamepad(s) Connected" như UI Mantis.
 */
fun listConnectedGamepads(): List<InputDevice> =
    InputDevice.getDeviceIds().toList()
        .mapNotNull { id -> InputDevice.getDevice(id) }
        .filter { dev -> (dev.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD }

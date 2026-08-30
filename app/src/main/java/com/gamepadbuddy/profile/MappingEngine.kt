package com.gamepadbuddy.profile

import com.gamepadbuddy.daemon.DaemonClient
import com.gamepadbuddy.input.GamepadEvent
import kotlin.math.hypot
import kotlin.math.min

/**
 * Mapping Engine — trung tâm dịch input tay cầm → lệnh daemon (file 06 - Bước 3).
 * - Dùng tracking id riêng cho mỗi widget để hỗ trợ multi-touch (nhiều nút/joystick cùng lúc).
 * - Áp deadzone cho joystick.
 * - Scale toạ độ px thật → không gian daemon (1080x2400) qua CoordinateMapper.
 */
class MappingEngine(
    private val daemon: DaemonClient,
    private var profile: Profile,
    private val mapper: CoordinateMapper
) {
    private val touchIds = mutableMapOf<String, Int>()
    private var nextId = 0
    private val DEADZONE = 0.05f
    private val MAX_SLOTS = 10

    fun setProfile(p: Profile) {
        profile = p
        touchIds.clear()
    }

    fun getProfile(): Profile = profile

    fun onGamepadEvent(e: GamepadEvent) {
        when (e) {
            is GamepadEvent.Button -> handleButton(e)
            is GamepadEvent.Axis -> handleAxis(e)
        }
    }

    private fun handleButton(e: GamepadEvent.Button) {
        val btn = profile.widgets.filterIsInstance<MappedWidget.Button>()
            .firstOrNull { it.boundKeyCode == e.keyCode } ?: return
        if (e.isDown) {
            val id = nextId++ % MAX_SLOTS
            touchIds[btn.id] = id
            val (x, y) = mapper.toDaemon(btn.x, btn.y)
            daemon.dragStart(id, x, y)
        } else {
            touchIds.remove(btn.id)?.let { daemon.dragEnd(it) }
        }
    }

    private fun handleAxis(e: GamepadEvent.Axis) {
        profile.widgets.filterIsInstance<MappedWidget.Joystick>().forEach { js ->
            val (dx, dy) = when (js.axisGroup) {
                AxisGroup.LEFT_STICK -> e.lx to e.ly
                AxisGroup.RIGHT_STICK -> e.rx to e.ry
            }
            val mag = min(1f, hypot(dx, dy))
            if (mag < DEADZONE) {
                touchIds.remove(js.id)?.let { daemon.dragEnd(it) }
                return@forEach
            }
            val id = touchIds.getOrPut(js.id) {
                (nextId++ % MAX_SLOTS).also {
                    val (cx, cy) = mapper.toDaemon(js.x, js.y)
                    daemon.dragStart(it, cx, cy)
                }
            }
            val (tx, ty) = mapper.toDaemon(js.x + dx * js.radius, js.y + dy * js.radius)
            daemon.dragMove(id, tx, ty)
        }
    }
}

/** Chuyển toạ độ px thật → không gian toạ độ của daemon (file 03/05). */
interface CoordinateMapper {
    fun toDaemon(x: Float, y: Float): Pair<Int, Int>
}

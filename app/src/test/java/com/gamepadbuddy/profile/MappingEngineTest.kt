package com.gamepadbuddy.profile

import com.gamepadbuddy.daemon.DaemonClient
import com.gamepadbuddy.input.GamepadEvent
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit test MappingEngine (file 06 + file 09): dịch input tay cầm → lệnh daemon.
 * Chạy trên JVM (không cần device). Dùng mockk để mock DaemonClient.
 */
class MappingEngineTest {

    // Keycode thô (tránh phụ thuộc Android framework trong JVM test): A=96, B=97.
    private val KEY_A = 96
    private val KEY_B = 97

    // Mapper identity (không scale) để test toạ độ gốc.
    private val identityMapper = object : CoordinateMapper {
        override fun toDaemon(x: Float, y: Float) = x.toInt() to y.toInt()
    }

    @Test
    fun buttonDown_sendsDragStart_thenUp_sendsDragEnd() {
        val daemon = mockk<DaemonClient>(relaxed = true)
        val profile = Profile("p", "com.x", "t",
            listOf(MappedWidget.Button("b", 100f, 200f, KEY_A)))
        val engine = MappingEngine(daemon, profile, identityMapper)

        engine.onGamepadEvent(GamepadEvent.Button(KEY_A, true))
        verify { daemon.dragStart(any(), 100, 200) }

        engine.onGamepadEvent(GamepadEvent.Button(KEY_A, false))
        verify { daemon.dragEnd(any()) }
    }

    @Test
    fun unboundKey_doesNotInject() {
        val daemon = mockk<DaemonClient>(relaxed = true)
        val profile = Profile("p", "com.x", "t",
            listOf(MappedWidget.Button("b", 1f, 1f, KEY_A)))
        val engine = MappingEngine(daemon, profile, identityMapper)

        engine.onGamepadEvent(GamepadEvent.Button(KEY_B, true))
        verify(inverse = true) { daemon.dragStart(any(), any(), any()) }
    }

    @Test
    fun twoButtons_useDistinctTrackingIds_forMultitouch() {
        val daemon = mockk<DaemonClient>(relaxed = true)
        val profile = Profile("p", "com.x", "t", listOf(
            MappedWidget.Button("b1", 10f, 10f, KEY_A),
            MappedWidget.Button("b2", 20f, 20f, KEY_B)))
        val engine = MappingEngine(daemon, profile, identityMapper)

        engine.onGamepadEvent(GamepadEvent.Button(KEY_A, true))
        engine.onGamepadEvent(GamepadEvent.Button(KEY_B, true))

        verify { daemon.dragStart(0, 10, 10) }
        verify { daemon.dragStart(1, 20, 20) }
    }

    @Test
    fun joystickBelowDeadzone_releases_andAbove_moves() {
        val daemon = mockk<DaemonClient>(relaxed = true)
        val profile = Profile("p", "com.x", "t",
            listOf(MappedWidget.Joystick("j", 50f, 50f, 100f, AxisGroup.LEFT_STICK)))
        val engine = MappingEngine(daemon, profile, identityMapper)

        // Dưới deadzone 0.05 -> không tiêm.
        engine.onGamepadEvent(GamepadEvent.Axis(0.01f, 0.01f, 0f, 0f, 0f, 0f))
        verify(inverse = true) { daemon.dragStart(any(), any(), any()) }

        // Kéo hết trục X -> tay cầm ấn tại tâm (50,50) rồi kéo tới (150,50).
        engine.onGamepadEvent(GamepadEvent.Axis(1f, 0f, 0f, 0f, 0f, 0f))
        verify { daemon.dragStart(any(), 50, 50) }
        verify { daemon.dragMove(any(), 150, 50) }
    }

    @Test
    fun mapperScalesCoordinates_fromScreenToDaemonSpace() {
        // Daemon không gian 1080x2400; màn hình giả 2160x4800 -> scale 0.5.
        val mapper = object : CoordinateMapper {
            override fun toDaemon(x: Float, y: Float) = (x * 0.5f).toInt() to (y * 0.5f).toInt()
        }
        val daemon = mockk<DaemonClient>(relaxed = true)
        val profile = Profile("p", "com.x", "t",
            listOf(MappedWidget.Button("b", 200f, 400f, KEY_A)))
        val engine = MappingEngine(daemon, profile, mapper)

        engine.onGamepadEvent(GamepadEvent.Button(KEY_A, true))
        verify { daemon.dragStart(any(), 100, 200) }
    }
}

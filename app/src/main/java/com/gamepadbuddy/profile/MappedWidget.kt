package com.gamepadbuddy.profile

/** Nhóm trục analog được ánh xạ (file 06 - Bước 1). */
enum class AxisGroup { LEFT_STICK, RIGHT_STICK }

/**
 * Widget ảo được ánh xạ (file 06). Thay thế cho model tạm ở WidgetConfig.
 * Toạ độ (x, y) theo px màn hình thật — MappingEngine sẽ scale sang không gian daemon.
 */
sealed class MappedWidget {
    abstract val id: String
    abstract var x: Float
    abstract var y: Float

    data class Button(
        override val id: String,
        override var x: Float,
        override var y: Float,
        val boundKeyCode: Int
    ) : MappedWidget()

    data class Joystick(
        override val id: String,
        override var x: Float,
        override var y: Float,
        val radius: Float,
        val axisGroup: AxisGroup
    ) : MappedWidget()
}

/**
 * Profile ánh xạ cho 1 game (file 06). Lưu trữ bền vững qua ProfileRepository.
 */
data class Profile(
    val id: String,
    val packageName: String,
    val name: String,
    val widgets: List<MappedWidget>
)

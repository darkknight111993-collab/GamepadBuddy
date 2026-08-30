# 06 — Hệ thống Profile & Mapping Engine

## Mục tiêu
Lưu trữ, tải, và áp dụng cấu hình ánh xạ (nút vật lý ↔ toạ độ ảo) riêng cho từng game.

## Bước 1: Data model

```kotlin
data class Profile(
    val id: String,
    val packageName: String,       // vd "com.garena.game.kgvn"
    val name: String,               // "Liên Quân - MOBA preset"
    val widgets: List<MappedWidget>
)

sealed class MappedWidget {
    abstract val id: String
    abstract val x: Float
    abstract val y: Float

    data class Button(
        override val id: String, override val x: Float, override val y: Float,
        val boundKeyCode: Int          // KeyEvent.KEYCODE_BUTTON_A, v.v.
    ) : MappedWidget()

    data class Joystick(
        override val id: String, override val x: Float, override val y: Float,
        val radius: Float,
        val axisGroup: AxisGroup        // LEFT_STICK hoặc RIGHT_STICK
    ) : MappedWidget()
}

enum class AxisGroup { LEFT_STICK, RIGHT_STICK }
```

## Bước 2: Lưu trữ (Room database khuyến nghị)

```kotlin
@Entity data class ProfileEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val name: String,
    val widgetsJson: String   // serialize List<MappedWidget> bằng kotlinx.serialization
)

@Dao interface ProfileDao {
    @Query("SELECT * FROM ProfileEntity WHERE packageName = :pkg")
    suspend fun getForPackage(pkg: String): List<ProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(profile: ProfileEntity)
}
```

## Bước 3: Mapping Engine — trung tâm xử lý logic

```kotlin
class MappingEngine(
    private val daemonClient: DaemonClient,
    private var activeProfile: Profile
) {
    private val activeTouchIds = mutableMapOf<String, Int>() // widgetId -> uinput trackingId
    private var nextTrackingId = 0

    fun onGamepadEvent(event: GamepadEvent) {
        when (event) {
            is GamepadEvent.Button -> handleButton(event)
            is GamepadEvent.Axis -> handleAxis(event)
        }
    }

    private fun handleButton(event: GamepadEvent.Button) {
        val widget = activeProfile.widgets
            .filterIsInstance<MappedWidget.Button>()
            .find { it.boundKeyCode == event.keyCode } ?: return

        if (event.isDown) {
            val trackId = nextTrackingId++
            activeTouchIds[widget.id] = trackId
            daemonClient.dragStart(trackId, widget.x.toInt(), widget.y.toInt())
        } else {
            activeTouchIds.remove(widget.id)?.let { daemonClient.dragEnd(it) }
        }
    }

    private fun handleAxis(event: GamepadEvent.Axis) {
        activeProfile.widgets.filterIsInstance<MappedWidget.Joystick>().forEach { js ->
            val (dx, dy) = when (js.axisGroup) {
                AxisGroup.LEFT_STICK -> event.lx to event.ly
                AxisGroup.RIGHT_STICK -> event.rx to event.ry
            }
            val magnitude = min(1f, hypot(dx, dy))
            val targetX = js.x + dx * js.radius
            val targetY = js.y + dy * js.radius

            val trackId = activeTouchIds.getOrPut(js.id) {
                (nextTrackingId++).also {
                    daemonClient.dragStart(it, js.x.toInt(), js.y.toInt())
                }
            }
            if (magnitude < 0.05f) {
                // về gần tâm -> nhả joystick
                daemonClient.dragEnd(trackId); activeTouchIds.remove(js.id)
            } else {
                daemonClient.dragMove(trackId, targetX.toInt(), targetY.toInt())
            }
        }
    }
}
```

## Bước 4: Preset dựng sẵn (MOBA / FPS / Battle Royale)

Định nghĩa các file JSON mẫu trong `assets/presets/moba.json`, `assets/presets/fps.json`...
Khi người dùng bấm nút preset "MOBA" (giống ảnh bạn gửi), app chỉ đơn giản load JSON này thành
`Profile` mặc định, cho phép chỉnh sửa tiếp trong Editor (file 03).

## Checklist
- [ ] Tạo/sửa/xoá Profile, lưu bền vững qua Room.
- [ ] MappingEngine chuyển đúng Button event → tap tại đúng toạ độ đã map.
- [ ] MappingEngine chuyển đúng Axis event → di chuyển joystick ảo mượt, nhả đúng lúc.
- [ ] Preset mẫu load được và áp dụng ngay.

Tiếp theo: **07-TU-DONG-NHAN-DIEN-GAME.md**

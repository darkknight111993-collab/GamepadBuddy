# 02 — Đọc input từ tay cầm vật lý

## Mục tiêu
Bắt được sự kiện nút bấm + trục analog từ tay cầm Bluetooth/USB (Xbox, PS, 8BitDo, generic HID),
chuẩn hoá thành model dùng chung trong toàn app.

## Bước 1: Bắt sự kiện ở tầng Activity/Service

Android gửi input từ gamepad qua `dispatchKeyEvent` (nút bấm) và
`dispatchGenericMotionEvent` (trục analog). Vì overlay của bạn sẽ là 1 `Service` vẽ `View` hệ
thống (WindowManager), bạn cần bắt sự kiện ngay trong `View` gốc của overlay, hoặc dùng
`InputManager.InputDeviceListener` kết hợp một `View` vô hình luôn có focus để lắng nghe.

```kotlin
class GamepadInputView(context: Context) : View(context) {
    var listener: ((GamepadEvent) -> Unit)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) {
            val down = event.action == KeyEvent.ACTION_DOWN
            listener?.invoke(GamepadEvent.Button(event.keyCode, down))
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) {
            val lx = event.getAxisValue(MotionEvent.AXIS_X)
            val ly = event.getAxisValue(MotionEvent.AXIS_Y)
            val rx = event.getAxisValue(MotionEvent.AXIS_Z)
            val ry = event.getAxisValue(MotionEvent.AXIS_RZ)
            val lt = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
            val rt = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)
            listener?.invoke(GamepadEvent.Axis(lx, ly, rx, ry, lt, rt))
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }
}
```

> **Lưu ý quan trọng**: một `View` overlay thường (loại `TYPE_APPLICATION_OVERLAY`) **không tự
> nhận được KeyEvent** trừ khi nó có `focusable` và hệ thống cấp focus cho nó — nhưng nếu nó
> chiếm focus, người dùng sẽ không thao tác được với game bên dưới. Đây là vấn đề kỹ thuật cốt
> lõi bạn sẽ phải giải quyết — cách phổ biến nhất:
> - Dùng `WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE` cho các nút overlay để game vẫn nhận
>   touch bình thường, **nhưng** tạo riêng 1 cửa sổ ẩn `1x1 px`, `focusable=true`,
>   `type=TYPE_APPLICATION_OVERLAY` đặt ở góc màn hình chỉ để "hứng" KeyEvent từ gamepad — vì
>   KeyEvent đi theo focus, còn touch đi theo toạ độ nên không xung đột.

## Bước 2: Model dữ liệu chuẩn hoá

```kotlin
sealed class GamepadEvent {
    data class Button(val keyCode: Int, val isDown: Boolean) : GamepadEvent()
    data class Axis(val lx: Float, val ly: Float, val rx: Float, val ry: Float,
                     val lt: Float, val rt: Float) : GamepadEvent()
}
```

Danh sách `keyCode` cần hỗ trợ tối thiểu (Android `KeyEvent` chuẩn):
`BUTTON_A, BUTTON_B, BUTTON_X, BUTTON_Y, BUTTON_L1, BUTTON_R1, BUTTON_L2, BUTTON_R2,
BUTTON_THUMBL, BUTTON_THUMBR, BUTTON_START, BUTTON_SELECT, DPAD_UP/DOWN/LEFT/RIGHT`.

## Bước 3: Xử lý deadzone cho trục analog

```kotlin
fun applyDeadzone(value: Float, threshold: Float = 0.15f): Float =
    if (abs(value) < threshold) 0f else value
```

## Bước 4: Nhận diện & liệt kê tay cầm đang kết nối

```kotlin
fun listConnectedGamepads(): List<InputDevice> =
    InputDevice.getDeviceIds()
        .mapNotNull { InputDevice.getDevice(it) }
        .filter { it.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD }
```
Dùng để hiển thị "1 Gamepad(s) Connected" như trong UI Mantis.

## Checklist
- [ ] Bắt được `Button` event khi nhấn từng nút trên tay cầm thật (log ra Logcat để test).
- [ ] Bắt được `Axis` event khi đẩy joystick trái/phải, có áp deadzone.
- [ ] Giải quyết được vấn đề focus (cửa sổ ẩn 1x1 hứng KeyEvent).
- [ ] Test với ít nhất 2 loại tay cầm khác nhau (vd: Xbox Bluetooth + 1 tay generic).

Tiếp theo: **03-OVERLAY-EDITOR.md**

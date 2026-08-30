# 03 — Floating Overlay + Editor kéo-thả

## Mục tiêu
Vẽ được lớp UI (nút ảo, joystick ảo) đè lên bất kỳ app/game nào, và cho phép người dùng kéo-thả
để tuỳ chỉnh vị trí — giống màn hình "Choose ThumbStick" trong Mantis.

## Bước 1: Xin quyền overlay

```kotlin
if (!Settings.canDrawOverlays(context)) {
    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"))
    startActivityForResult(intent, REQUEST_OVERLAY)
}
```

## Bước 2: Tạo `OverlayService` (Foreground Service) quản lý WindowManager

```kotlin
class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var rootView: View? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildPersistentNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlay()
    }

    private fun showOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        rootView = OverlayRootView(this) // chứa các nút/joystick ảo
        windowManager.addView(rootView, params)
    }
}
```

`FLAG_NOT_FOCUSABLE` là bắt buộc — nếu không, overlay sẽ chặn hết touch của game bên dưới.

## Bước 3: Widget nút ảo & joystick ảo

Mỗi widget là 1 `View` con trong `OverlayRootView`, có toạ độ `(x, y)` lưu trong Profile
(xem file 06). Ví dụ widget joystick:

```kotlin
class VirtualJoystickView(context: Context) : View(context) {
    var onMove: ((dx: Float, dy: Float) -> Unit)? = null
    var onRelease: (() -> Unit)? = null
    // vẽ 2 vòng tròn (nền + núm), xử lý kéo bằng onTouchEvent như joystick game thường thấy
}
```

Widget này phục vụ **2 chiều dữ liệu**:
1. Người dùng chạm trực tiếp joystick ảo bằng ngón tay (khi không dùng gamepad) → hoạt động như
   joystick cảm ứng bình thường, không cần daemon.
2. Khi có input từ gamepad (file 02) → **không vẽ chạm thật của người dùng**, mà gọi thẳng xuống
   `daemon` (file 05) để tiêm toạ độ tương ứng vào **vị trí thật của joystick trong GAME**
   (không phải vị trí overlay — 2 cái này trùng nhau về toạ độ theo thiết kế).

## Bước 4: Chế độ Editor (kéo-thả chỉnh layout)

- Toggle "Edit Mode": khi bật, mỗi widget hiện viền + có thể kéo bằng `onTouchEvent` với
  `ACTION_MOVE`, cập nhật `layoutParams.x/y`, và có nút xoá/thêm.
- Khi thoát Edit Mode → lưu toạ độ từng widget vào Profile hiện tại (file 06).
- Có "preset" dựng sẵn theo thể loại (MOBA, FPS, Battle Royale) như bạn thấy trong ảnh Mantis
  (mục Scope / Order / Gesture / MOBA) — đây chỉ là các bộ layout mẫu, hoàn toàn là dữ liệu tĩnh
  (JSON) bạn định nghĩa trước, không cần logic đặc biệt.

## Bước 5: Thanh công cụ nổi (floating toolbar)

Ảnh Mantis cho thấy 1 thanh nhỏ nổi luôn hiện (icon +, layers, gamepad, settings) để mở nhanh
editor mà không cần thoát game. Đây chỉ là 1 `LinearLayout` nhỏ, luôn ở top-level overlay,
`draggable` để người dùng dời sang cạnh màn hình khi không dùng.

## Checklist
- [ ] Overlay hiện được đè lên 1 app khác (test với app bất kỳ).
- [ ] Nút ảo/joystick ảo không chặn touch thật xuống game bên dưới khi không ở Edit Mode.
- [ ] Kéo-thả đổi vị trí widget hoạt động, lưu được toạ độ.
- [ ] Floating toolbar mở/đóng editor được.

Tiếp theo: **04-ADB-WIRELESS-PAIRING.md**

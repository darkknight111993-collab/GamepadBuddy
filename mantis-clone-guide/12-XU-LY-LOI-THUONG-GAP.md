# 12 — Xử lý lỗi thường gặp

## Lỗi: Bong bóng không hiện dù overlay + daemon đều báo "sẵn sàng" (xác nhận từ code)

### Nguyên nhân gốc
`AppDetector.getForegroundPackage()` (file 07) đọc app đang mở qua `UsageStatsManager`, phụ
thuộc **hoàn toàn** vào quyền Usage Access (`PACKAGE_USAGE_STATS`). Quyền này:
- **Không nằm trong `onboardingSteps`** (file 01b) — Wizard không bao giờ dẫn người dùng qua
  bước này.
- **Không được Launchpad kiểm tra** trước khi bật nút Launch — checklist chỉ xét Floating widget
  + Keymapping, báo "sẵn sàng" dù thiếu quyền này.

Hậu quả: `getForegroundPackage()` trả về `null` vĩnh viễn → `OverlayService.onForegroundChanged()`
không bao giờ tìm thấy Profile khớp → `root.visibility` luôn là `INVISIBLE` → bong bóng/toolbar
không bao giờ xuất hiện khi mở game, **kể cả khi mọi quyền/daemon khác đều đúng**.

### Cách khắc phục (đã vá trong code, xem patch)
1. Thêm bước `usage_access` vào `onboardingSteps` (`OnboardingStep.kt`), dùng
   `isUsageAccessGranted()` (mới thêm vào `Permissions.kt`, đọc qua `AppOpsManager`).
2. Thêm dòng kiểm tra thứ 3 trong `LaunchpadBottomSheet` (`ivDetectStatus`/`btnFixDetect`), gộp
   vào điều kiện `bothOk` để nút Launch/Edit chỉ bật khi quyền này cũng đã có.
3. Người dùng đã cài bản cũ (trước bản vá): vào Cài đặt → Ứng dụng → Quyền đặc biệt → Usage
   Access (hoặc bấm "Cấp quyền" ngay trong Settings tab của app, nút `btnUsage`) → bật cho
   GamepadBuddy → không cần khởi động lại app, `AppDetector` đọc trạng thái theo thời gian thực.

### Cách phân biệt với lỗi MIUI overlay (mục dưới)
- Lỗi MIUI overlay: bong bóng hiện được ở Home/app thường nhưng **biến mất/đen khi vào đúng
  game** → do Game Turbo hoặc quyền overlay riêng của MIUI.
- Lỗi Usage Access (mục này): bong bóng **không hiện ở bất kỳ đâu, kể cả khi mở app từ Launchpad
  với checklist báo "sẵn sàng"** → luôn kiểm tra quyền Usage Access trước tiên vì đây là nguyên
  nhân dễ bị bỏ sót nhất (không có dấu hiệu lỗi rõ ràng nào từ phía UI cũ).

---

## Lỗi: Không thấy bong bóng/nút ảo hiện lên khi mở game + màn hình đen (đặc biệt trên Xiaomi/MIUI)

### Nguyên nhân gốc
MIUI có **2 lớp quyền overlay tách biệt nhau**, khác với Android chuẩn:
1. `SYSTEM_ALERT_WINDOW` — quyền Android chuẩn, xin qua `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`
   (đã làm ở file 01b, Bước "overlay").
2. **"Hiển thị cửa sổ bật lên khi chạy nền"** (Display pop-up windows while running in
   background) — quyền **riêng của MIUI**, không có API chuẩn để xin, người dùng phải tự bật thủ
   công trong Settings.

Quyền #1 chỉ cho phép vẽ overlay khi app **đang ở foreground**. Nhưng thực tế lúc người chơi mở
game, app GamepadBuddy đã bị đẩy xuống **nền** — đúng lúc đó MIUI chặn `addView()` nếu quyền #2
chưa bật, dẫn tới bong bóng không bao giờ xuất hiện. Ngoài ra, **Game Turbo** (Trò chơi tăng tốc)
có thể tự động chặn mọi cửa sổ nổi khi vào chế độ chơi game toàn màn hình, gây thêm hiệu ứng
màn hình đen khi chuyển cảnh.

### Cách khắc phục (làm đúng thứ tự)

1. **Bật "Hiển thị cửa sổ bật lên khi chạy nền"**
   Cài đặt → Ứng dụng → Quản lý ứng dụng → [tên app] → Quyền → bật mục này. Đây là bước quan
   trọng nhất, khắc phục phần lớn trường hợp bong bóng không hiện.

2. **Xác nhận "Hiển thị trên các ứng dụng khác" vẫn đang bật**
   Cùng màn hình Quyền ở trên — quyền Android chuẩn (file 01b) phải đang bật song song với quyền
   #1, thiếu 1 trong 2 đều không đủ.

3. **Tắt chặn overlay trong Game Turbo cho đúng game đang test**
   Mở app Game Turbo có sẵn trên máy → chọn đúng game → vào cài đặt riêng của game đó → tắt các
   tùy chọn "Chặn cửa sổ nổi" / "Không làm phiền" / "Tạm ẩn thông báo".

4. **Bật Autostart + tắt tối ưu pin cho app**
   Cài đặt → Ứng dụng → Quản lý ứng dụng → [tên app] → bật "Tự khởi động", đặt Tiết kiệm pin
   thành "Không giới hạn". Nếu bỏ qua bước này, `OverlayService` dễ bị MIUI kill ngầm ngay khi
   app bị đẩy xuống nền lúc vào game — bong bóng hiện được vài giây rồi biến mất.

5. **Tắt "Tối ưu hóa MIUI" trong Developer Options (khi đang test qua ADB)**
   Cần khởi động lại máy sau khi tắt. Tùy chọn này giới hạn hành vi nền/overlay của các app
   không cài từ Xiaomi GetApps — ảnh hưởng trực tiếp trong giai đoạn phát triển/debug qua ADB.

### Cách xác nhận đã fix đúng nguyên nhân
Test overlay ở màn hình Home hoặc 1 app thường (không phải game) trước — nếu hiện bình thường ở
đó nhưng vẫn lỗi khi vào đúng game cụ thể, quay lại kiểm tra riêng Game Turbo (bước 3) vì có thể
game đó có whitelist/blacklist riêng trong Game Turbo.

### Lưu ý code — phòng lỗi tương tự ở ROM khác
Dù đã fix được bằng cấu hình máy, nên chủ động thêm các việc sau vào code (áp dụng chung, không
riêng MIUI):
- Không bọc `windowManager.addView()` trong try-catch rỗng — luôn log lại
  `SecurityException`/`BadTokenException` nếu có, để không lặp lại tình trạng "lỗi âm thầm không
  dấu vết" như đã gặp.
- Trong `OverlayService.onCreate()`, gọi `Settings.canDrawOverlays(context)` và log kết quả mỗi
  lần khởi động — nếu `false`, chủ động hiện thông báo hướng dẫn người dùng thay vì để bong bóng
  im lặng không xuất hiện.
- Cân nhắc thêm 1 màn hình "Kiểm tra tương thích thiết bị" trong app, phát hiện ROM MIUI/ColorOS/
  FuntouchOS qua `Build.MANUFACTURER` và tự hiện hướng dẫn riêng cho từng hãng thay vì hướng dẫn
  chung chung — vì tên menu Settings khác nhau khá nhiều giữa các hãng.

---
Ghi chú thêm khi phát hiện lỗi tương tự cho hãng máy khác (Samsung/Oppo/Vivo) vào phần dưới đây
theo cùng cấu trúc, để file này trở thành sổ tay tra cứu chung.

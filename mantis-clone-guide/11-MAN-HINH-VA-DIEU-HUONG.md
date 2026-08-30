# 11 — Danh sách màn hình & cấu trúc điều hướng

File này tổng kết lại toàn bộ số lượng màn hình (form) của app và cách chúng điều hướng với
nhau, dựa trên các quyết định thiết kế đã chốt ở các file trước (đặc biệt 01b, 03, 04, 04b, 06).

## Tổng số: 9 màn hình/form

| # | Màn hình | Loại | Mở từ đâu | File hướng dẫn liên quan |
|---|---|---|---|---|
| 1 | Onboarding Wizard | Activity, gate 1 lần duy nhất | Lần đầu mở app | 01b |
| 2 | Trang chủ (Home) | Fragment, tab 1 của Bottom Nav | Bottom nav | 07 |
| 3 | Tay cầm (Controllers) | Fragment, tab 2 của Bottom Nav | Bottom nav | 02 |
| 4 | Cài đặt (Settings) | Fragment, tab 3 của Bottom Nav | Bottom nav | 08 |
| 5 | Thêm game | Modal / BottomSheet | Nút "+" ở tab Home | 07 |
| 6 | Launchpad | Modal / BottomSheet | Chạm vào 1 game trong danh sách Home | 06 |
| 7 | Ánh xạ nút (Mapping editor) | Activity | Nút "Edit mapping" trong Launchpad | 06 |
| 8 | Ghép nối lại (Pairing) | Activity | Tab Settings, hoặc tự mở khi daemon mất kết nối | 04, 04b |
| 9 | Lớp overlay nổi | **Window riêng qua WindowManager — không thuộc Activity stack** | Tự bật khi bấm "Launch Game" trong Launchpad | 03, 05 |

## Cấu trúc điều hướng (2 cây song song)

```
Onboarding Wizard (1 lần)
        │
        ▼
┌─────────────────────────────────────────┐
│         Ứng dụng chính (Bottom Nav)      │
│  ┌──────────┐ ┌────────────┐ ┌────────┐ │
│  │ Trang chủ│ │  Tay cầm   │ │Cài đặt │ │
│  └────┬─────┘ └────────────┘ └───┬────┘ │
└───────┼───────────────────────────┼──────┘
        │                           │
        ▼                           ▼
  [+] → Thêm game            Ghép nối lại (Pairing)
        │
        ▼
  Chạm game → Launchpad
        │
        ├──→ "Edit mapping" → Ánh xạ nút
        │
        └──→ "Launch Game" → ╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌
                              ┆ Lớp overlay nổi      ┆
                              ┆ (WindowManager riêng, ┆
                              ┆  đè lên game, sống    ┆
                              ┆  độc lập Activity     ┆
                              ┆  stack)               ┆
                              ╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌
```

## Nguyên tắc điều hướng quan trọng nhất

**Overlay (#9) không nằm trong back stack của 8 màn hình còn lại.** `OverlayService` (file 03)
vẽ nó trực tiếp qua `WindowManager`, đè lên bất kỳ app nào đang chạy ở foreground — kể cả khi
toàn bộ 8 màn hình kia đã bị đẩy xuống nền (người dùng đang ở trong game, không phải trong app
GamepadBridge). Đây là lý do người dùng vừa chơi game vừa thấy overlay: **2 cây điều hướng chạy
song song, không giao nhau** — cây Activity/Fragment bình thường, và cây Window hệ thống của
overlay.

## Vì sao Onboarding Wizard là "gate" chứ không phải màn hình thường

- Chỉ hiện **đúng 1 lần** cho tới khi cả 9 quyền ở file 01b được cấp đủ.
- `onResume()` của Wizard luôn re-check toàn bộ trạng thái quyền thật từ hệ thống — nếu người
  dùng tắt bớt quyền nào đó trong Settings sau này, Wizard **tự động hiện lại** ở lần mở app kế
  tiếp cho tới khi đủ quyền trở lại. Không lưu cờ "đã hoàn thành" tĩnh trong SharedPreferences,
  vì quyền có thể bị thu hồi bất cứ lúc nào ngoài tầm kiểm soát của app.

## Vì sao Pairing (#8) có 2 điểm vào khác nhau

1. **Chủ động**: người dùng tự vào Settings → "Ghép nối lại" khi muốn đổi thiết bị hoặc gỡ lỗi.
2. **Bị động/tự động**: `WirelessDebugListenerService` (file 04b) phát hiện daemon mất kết nối
   (do đổi Wi-Fi, khởi động lại máy...) → tự động điều hướng thẳng tới màn hình này thay vì chờ
   người dùng tự nhận ra và tìm đường vào Settings.

## Vì sao Add Game (#5) và Launchpad (#6) là Modal/BottomSheet chứ không phải Activity riêng

Cả hai là các thao tác ngắn, mang tính "dừng lại xác nhận rồi quay về" — dùng BottomSheet giữ
được ngữ cảnh (người dùng vẫn thấy mờ mờ danh sách Home phía sau), tránh chuyển màn hình đột ngột
kiểu Activity, và tránh phải quản lý thêm 1 back-stack entry không cần thiết.

---
File này nên được đọc sau khi đã hiểu rõ nội dung chi tiết của 01b, 03, 04, 04b, 06, 07 — nó chỉ
là bản tổng hợp giúp hình dung toàn cảnh, không thay thế nội dung kỹ thuật ở các file đó.

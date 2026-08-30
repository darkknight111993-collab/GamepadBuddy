# 09 — Testing & Đóng gói bản Release

## Bước 1: Ma trận test bắt buộc

| Hạng mục | Cần test |
|---|---|
| Tay cầm | Xbox Bluetooth, PS4/PS5 Bluetooth, tay generic HID giá rẻ, tay có dây USB-OTG |
| Máy Android | Ít nhất 1 máy Samsung (OneUI), 1 máy Xiaomi/Redmi (MIUI), 1 máy Pixel/AOSP thuần |
| Android version | 11, 12, 13, 14+ (API pairing 6 số chỉ có từ 11) |
| Game | Ít nhất 2–3 game MOBA/Battle Royale khác nhau để đảm bảo mapping engine tổng quát |
| Mạng | Wi-Fi đổi IP giữa chừng → xác nhận reconnect daemon không bị treo |

## Bước 2: Test độ trễ & độ ổn định daemon
- Đo thời gian từ lúc nhấn nút vật lý → chạm thật xuất hiện trong game (quay video 60fps rồi đếm
  frame là cách đơn giản để đo).
- Test daemon sống sót qua: khoá màn hình/mở lại, chuyển app nền/foreground nhiều lần, MIUI
  "dọn RAM" tự động.

## Bước 3: Xử lý riêng cho từng hãng ROM
- **MIUI**: cần hướng dẫn tắt "MIUI Optimization", thêm app vào Autostart, tắt Battery Saver cho
  app (đúng như 2 ảnh chụp `com.miui.securitycore` bạn gửi — đây thực chất là bước Mantis dẫn
  người dùng đi qua để tránh bị Xiaomi kill service).
- **Samsung/OneUI**: thêm app vào "Never sleeping apps" trong Battery settings.

## Bước 4: Build release
```bash
./gradlew assembleRelease
# Ký APK bằng keystore riêng, bật R8/ProGuard nhưng nhớ giữ rule cho JNI/uinput binary
```
- Đảm bảo binary daemon (assets) đúng ABI cho từng kiến trúc CPU, tránh chỉ build 1 ABI rồi lỗi
  trên máy khác.

## Bước 5: Cân nhắc trước khi đăng Google Play
- Quyền `SYSTEM_ALERT_WINDOW` + hành vi "tiêm input" dễ bị Play Store review kỹ hoặc từ chối nếu
  không giải thích rõ mục đích chính đáng (hỗ trợ tiếp cận cho người chơi dùng gamepad).
- Cân nhắc phát hành ngoài Play Store (APK trực tiếp) ở giai đoạn đầu để tránh rủi ro bị gỡ khi
  đang thử nghiệm.

## Checklist
- [ ] Test đủ ma trận thiết bị/tay cầm ở Bước 1.
- [ ] Đo độ trễ chấp nhận được (< 50ms lý tưởng).
- [ ] Daemon sống sót qua các kịch bản kill app ở Bước 3.
- [ ] Build release ký thành công, cài đặt sạch trên máy thật.

Tiếp theo: **10-LO-TRINH-MVP.md**

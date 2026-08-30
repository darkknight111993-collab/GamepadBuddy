# 00 — Tổng quan kiến trúc (đọc trước tiên)
phần mềm tên GamepadBuddy


## Mục tiêu sản phẩm
Một app Android cho phép người dùng dùng **tay cầm vật lý (Bluetooth/USB)** để điều khiển
các game **không hỗ trợ gamepad sẵn** (MOBA/Battle Royale cảm ứng như Liên Quân, PUBG Mobile...),
bằng cách "map" nút tay cầm → toạ độ chạm ảo đè lên màn hình game.

## Các module chính (đọc theo đúng thứ tự file)

| # | File | Nội dung |
|---|------|----------|
| 1 | 01-SETUP-DU-AN.md | Khởi tạo project Android Studio, cấu trúc module |
| 1b | 01b-LUONG-XIN-QUYEN-DAY-DU.md | Wizard xin đủ 9 quyền/cấu hình (giống QuickStart của Mantis) |
| 2 | 02-DOC-INPUT-TAY-CAM.md | Đọc sự kiện tay cầm (nút, joystick) |
| 3 | 03-OVERLAY-EDITOR.md | Vẽ UI đè lên game + editor kéo-thả |
| 4 | 04-ADB-WIRELESS-PAIRING.md | Luồng bật Developer Options → pairing ADB không dây |
| 4b | 04b-GHEP-NOI-QUA-THONG-BAO.md | Tự động hoá pairing qua Notification Listener + mDNS discovery |
| 5 | 05-DAEMON-TIEM-TOUCH.md | "MantisBuddy" — daemon tiêm sự kiện chạm độ trễ thấp |
| 6 | 06-HE-THONG-PROFILE.md | Lưu/áp mapping theo từng game |
| 7 | 07-TU-DONG-NHAN-DIEN-GAME.md | Tự phát hiện app đang mở để load đúng profile |
| 8 | 08-BACKEND-TAI-KHOAN.md | (Tuỳ chọn) Tài khoản, credit, chia sẻ config cộng đồng |
| 9 | 09-TESTING-DONG-GOI.md | Test trên nhiều tay cầm/game, build release |
| 10 | 10-LO-TRINH-MVP.md | Lộ trình rút gọn nếu chỉ muốn ra MVP nhanh |
| 11 | 11-MAN-HINH-VA-DIEU-HUONG.md | Tổng kết danh sách màn hình & cấu trúc điều hướng |

## Sơ đồ luồng dữ liệu (runtime)

```
[Tay cầm vật lý] --Bluetooth/USB-->  [Android InputDevice API]
                                            |
                                   InputListenerService
                                            |
                               Mapping Engine (đọc Profile
                               hiện tại của game đang mở)
                                            |
                              "Nút X vật lý" -> "toạ độ (540,1200)"
                                            |
                         Gửi lệnh qua Local Socket (localhost)
                                            |
                     MantisBuddy Daemon (chạy bằng quyền `shell`,
                     được cấp qua ADB Wireless Debugging)
                                            |
                     Tạo touch event bằng uinput virtual device
                                            |
                              [Game đang chạy nhận được chạm]
```

## Vì sao bắt buộc phải qua ADB / quyền shell?

Android **chặn app thường (quyền `normal`) tiêm touch event vào app khác** — đây là giới hạn
bảo mật cấp hệ điều hành, không phải do thiếu code. Quyền `shell` (uid 2000, cấp qua ADB) thì
**được phép** dùng `/dev/uinput` hoặc lệnh `input`. Vì vậy toàn bộ nhóm app dạng này
(Mantis, Panda Gamepad Pro, Octopus, GameSir...) đều cần một bước "pairing" để tự cấp quyền
shell cho chính mình thông qua Wireless Debugging (Android 11+) — không cần root máy.

## Lưu ý pháp lý/ToS (đọc kỹ trước khi làm)
- Nhiều nhà phát hành (Garena, Tencent, MiHoYo...) coi việc tiêm input tự động là công cụ hỗ trợ
  trái phép trong ToS, có thể dẫn đến khoá tài khoản người chơi cuối — rủi ro này thuộc về người
  dùng cuối, nhưng bạn nên ghi rõ cảnh báo trong app.
- Không dùng kỹ thuật này để tạo **auto-aim, auto-farm, script**: đó là gian lận (cheat) và có thể
  vi phạm pháp luật ở một số nơi. Phạm vi của app này chỉ là "ánh xạ nút cứng → toạ độ chạm",
  người chơi vẫn tự điều khiển 100%.

## Ngăn xếp công nghệ đề xuất
- **Ngôn ngữ**: Kotlin (Android native) — bắt buộc nếu cần tương tác input-level thấp.
- **Daemon**: C (biên dịch tĩnh, chạy qua `adb shell`) hoặc Kotlin/Java chạy qua `app_process`.
- **Local IPC**: `LocalSocket` (android.net.LocalSocket) — nhanh, không cần mở port mạng.
- **Lưu trữ profile**: Room database (SQLite) hoặc file JSON trong `filesDir`.
- **Backend (nếu có)**: Firebase (nhanh gọn cho MVP) hoặc Node.js/Express + Postgres nếu cần tuỳ biến.

Đọc tiếp file **01-SETUP-DU-AN.md**.

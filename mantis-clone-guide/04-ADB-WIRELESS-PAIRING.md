# 04 — Luồng bật Developer Options & Pairing ADB không dây

## Mục tiêu
Tái tạo đúng luồng 6 bước bạn thấy trong QuickStart của Mantis, để app tự cấp cho mình quyền
`shell` — nền tảng cho phép daemon (file 05) tiêm được touch event.

## Bước 1: Hướng dẫn người dùng bật Developer Options

Không có API để tự động bật — chỉ có thể mở đúng màn hình Settings và hướng dẫn bằng UI:

```kotlin
fun openDeveloperOptionsHint(context: Context) {
    // Không thể bật hộ; chỉ mở Settings và hiển thị hướng dẫn "bấm 7 lần vào Số Bản Dựng"
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
}
```
Nếu Developer Options chưa bật, intent trên sẽ đưa thẳng tới màn hình About Phone —
bạn hiển thị overlay hướng dẫn "bấm liên tục vào Số hiệu bản dựng (Build Number) 7 lần".

## Bước 2: Mở đúng màn hình Wireless Debugging

```kotlin
fun openWirelessDebuggingSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
    // Từ Android 11, trong màn hình Developer Options sẽ có mục "Wireless debugging"
}
```

## Bước 3: Hướng dẫn "Pair device with pairing code"

Đây là tính năng có sẵn của Android (không phải bạn tự viết) — người dùng vào
**Wireless debugging → Pair device with pairing code**, hệ thống hiện:
- Mã 6 số (pairing code)
- IP:Port riêng cho phiên pairing (khác với IP:Port dùng để connect chính thức)

## Bước 4: App của bạn thực hiện `adb pair` bằng giao thức ADB, không gọi binary `adb`

Vì máy Android không có sẵn binary `adb` chạy trên chính nó theo cách thông thường, cách khả thi
nhất **là dùng thư viện Java/Kotlin cài đặt lại giao thức ADB (mDNS/TLS pairing)** thay vì shell
ra ngoài gọi `adb`. Có 2 hướng:

**Hướng A (khuyến nghị, ổn định)** — dùng thư viện mã nguồn mở đã cài đặt sẵn ADB protocol:
- `dadb` (Google/JetBrains liên quan tới Kotlin) hay `adb-android` — cho phép pairing + connect
  + push file + shell command hoàn toàn bằng Kotlin, không cần binary `adb` ngoài.
- Flow: người dùng nhập mã 6 số hiển thị trên máy → app gọi `AdbPair.pair(host, port, pairCode)`
  → nhận về `AdbConnection` đã authenticated → dùng connection này `push` file daemon (file 05)
  và `shell` lệnh khởi động nó.

**Hướng B (đơn giản hơn để làm nhanh MVP)** — bắt người dùng tự dùng máy tính:
- Hướng dẫn người dùng cắm cáp USB, chạy `adb pair` / `adb connect` từ **máy tính**, rồi
  `adb push mantisbuddy /data/local/tmp/` + `adb shell` khởi động — bỏ qua việc tự động hoá
  hoàn toàn trong app. Đây là cách nhiều app "amateur"/mã nguồn mở làm ở bản đầu tiên.

> Với sản phẩm thương mại như Mantis, họ chắc chắn đi theo Hướng A (toàn bộ pairing xảy ra
> ngay trong app, không cần máy tính) — nhưng Hướng B giúp bạn có MVP nhanh hơn nhiều.

## Bước 5: Lưu lại kết nối để không phải pair lại mỗi lần

- Sau khi pairing thành công lần đầu, ADB cấp 1 cặp khoá RSA để "connect" lại **không cần pairing
  code nữa**, miễn là dùng đúng `IP:Port` (port kết nối chính thức, hiển thị riêng như trong ảnh
  `192.168.10.34:39833`).
- Lưu khoá này (thư viện adb-protocol tự quản lý keystore) trong `filesDir` để tái sử dụng.
- Cần xử lý trường hợp đổi mạng Wi-Fi → IP đổi → phải nhắc người dùng pair lại hoặc dùng
  mDNS discovery (`_adb-tls-connect._tcp`) để tự tìm IP mới.

## Checklist
- [ ] Mở đúng màn hình Settings cho từng bước (Developer Options, Wireless Debugging).
- [ ] Nhập được pairing code trong app, pairing thành công (test bằng thư viện dadb hoặc tương đương).
- [ ] Lưu được khoá để lần sau không cần nhập lại mã.
- [ ] Có màn hình fallback hướng dẫn dùng máy tính (Hướng B) cho máy Android < 11.

Tiếp theo: **05-DAEMON-TIEM-TOUCH.md**

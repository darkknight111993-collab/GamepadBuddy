# 01 — Khởi tạo dự án Android

## Mục tiêu
Có một project Android Studio biên dịch chạy được, với cấu trúc module sẵn sàng cho các bước sau.

## Bước 1: Tạo project
1. Android Studio → New Project → **Empty Activity** (Kotlin, không dùng Compose lúc đầu để đơn
   giản hoá phần overlay — có thể chuyển sang Compose sau).
2. `minSdk = 26` (Android 8.0) — nhưng lưu ý **Wireless Debugging pairing** chỉ có từ Android 11
   (API 30) trở lên, nên cần xử lý fallback (USB debugging) cho máy cũ hơn.
3. `targetSdk` = bản mới nhất hiện có.

## Bước 2: Cấu trúc module đề xuất

```
app/
 ├─ src/main/java/com/yourapp/gamepadbridge/
 │   ├─ input/              # đọc sự kiện tay cầm (file 02)
 │   ├─ overlay/             # floating UI + editor (file 03)
 │   ├─ adb/                 # pairing & connect ADB (file 04)
 │   ├─ daemon/              # giao tiếp với MantisBuddy qua LocalSocket (file 05)
 │   ├─ profile/             # model + lưu trữ mapping (file 06)
 │   ├─ detector/             # nhận diện app foreground (file 07)
 │   ├─ ui/                  # màn hình chính, danh sách game, settings
 │   └─ MainActivity.kt
 ├─ src/main/assets/
 │   └─ mantisbuddy           # binary daemon (build ở bước 05), đóng gói sẵn trong APK
 └─ AndroidManifest.xml
```

## Bước 3: Khai báo quyền cần thiết trong `AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
    tools:ignore="ProtectedPermissions" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

Ghi chú:
- `SYSTEM_ALERT_WINDOW`: bắt buộc cho overlay (file 03).
- `PACKAGE_USAGE_STATS`: dùng để nhận diện app đang mở foreground (file 07) — quyền này phải
  được người dùng cấp thủ công trong Settings, không thể xin qua dialog thường.
- Không cần khai báo quyền đặc biệt nào cho ADB — việc "pairing" nằm ở tầng ứng dụng (file 04),
  không phải permission Android chuẩn.

## Bước 4: Khai báo Foreground Service (chạy nền ổn định)

```xml
<service
    android:name=".overlay.OverlayService"
    android:foregroundServiceType="specialUse"
    android:exported="false" />
```

Service này sẽ là "trái tim" chạy nền: giữ overlay hiển thị + giữ kết nối tới MantisBuddy daemon
trong khi người dùng chơi game.

## Bước 5: Checklist hoàn thành bước này
- [ ] Project build & chạy được (Empty Activity hiện "Hello World").
- [ ] Các package con đã tạo (input, overlay, adb, daemon, profile, detector, ui).
- [ ] Quyền đã khai báo trong Manifest.
- [ ] `OverlayService` rỗng đã tạo, khởi động được từ MainActivity (chưa cần vẽ gì).

Tiếp theo: **02-DOC-INPUT-TAY-CAM.md**

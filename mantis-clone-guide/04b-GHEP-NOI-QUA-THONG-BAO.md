# 04b — Ghép nối qua thanh thông báo (Notification-driven pairing)

Đọc file này SAU file 04, trước khi code phần daemon (file 05). File này thay thế cách nhập tay
IP/mã pairing bằng luồng tự động hơn, giống Mantis: người dùng chỉ cần bật Wireless debugging,
phần còn lại app tự lo qua notification + tự dò mạng.

## Làm rõ giới hạn kỹ thuật trước khi code

`NotificationListenerService` chỉ đọc được **những gì hệ thống đã hiển thị trên thanh thông báo**
— tiêu đề, nội dung text, package nguồn. Thông báo "Wireless debugging Connected" của Android
**không chứa IP:Port hay mã pairing** trong text, nên listener chỉ dùng để biết **trạng thái
bật/tắt**, không dùng để "đọc trộm" IP. Việc tự động lấy IP:Port phải làm qua **mDNS discovery**
(Bước 3 dưới đây) — đây là cách thật sự đáng tin cậy và cũng là cách các tool tương tự làm.

## Bước 1: Khai báo & xin quyền Notification Listener

`AndroidManifest.xml`:
```xml
<service
    android:name=".notification.WirelessDebugListenerService"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
    android:exported="false">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

Quyền này **không xin được qua dialog thường** — phải điều hướng người dùng:
```kotlin
fun requestNotificationAccess(context: Context) {
    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    // Hiển thị overlay hướng dẫn: "Tìm 'GamepadBridge' trong danh sách và bật lên"
}

fun isNotificationAccessGranted(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver,
        "enabled_notification_listeners") ?: ""
    return enabled.contains(context.packageName)
}
```

## Bước 2: Lắng nghe trạng thái Wireless debugging

```kotlin
class WirelessDebugListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.android.settings" &&
            sbn.packageName != "android") return

        val title = sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (title.contains("Wireless debugging", ignoreCase = true) ||
            text.contains("Wireless debugging", ignoreCase = true)) {
            // Chỉ biết được: đang BẬT. Không có IP/port trong đây.
            PairingStateBus.onWirelessDebuggingActive(true)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == "com.android.settings" || sbn.packageName == "android") {
            PairingStateBus.onWirelessDebuggingActive(false)
        }
    }
}
```

`PairingStateBus` là 1 `SharedFlow`/`LiveData` đơn giản để `OverlayService` và UI lắng nghe và
phản ứng (ví dụ: tự động bắt đầu bước dò mạng ở Bước 3 ngay khi phát hiện Wireless debugging
vừa được bật).

## Bước 3: Tự động dò IP:Port bằng mDNS (NsdManager) — phần thay thế nhập tay

Android quảng bá 2 loại dịch vụ mDNS khi Wireless debugging bật:
- `_adb-tls-pairing._tcp` — chỉ xuất hiện khi màn hình "Pair device with pairing code" đang mở
- `_adb-tls-connect._tcp` — xuất hiện khi thiết bị đã sẵn sàng nhận kết nối adb thường

```kotlin
class AdbServiceDiscovery(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    fun discoverConnectService(onFound: (host: String, port: Int) -> Unit) {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                nsdManager.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        onFound(resolved.host.hostAddress!!, resolved.port)
                    }
                    override fun onResolveFailed(i: NsdServiceInfo, code: Int) {}
                })
            }
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onServiceLost(info: NsdServiceInfo) {}
        }
        nsdManager.discoverServices("_adb-tls-connect._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
    }
}
```

> Lưu ý: mã pairing 6 số **vẫn phải do người dùng đọc và nhập tay đúng 1 lần đầu tiên** — đây là
> cơ chế bảo mật cố ý của Android (chứng minh người dùng có quyền truy cập vật lý màn hình thiết
> bị), không có cách nào bỏ qua bước này một cách hợp lệ. Sau lần pairing đầu, các lần sau chỉ
> cần `_adb-tls-connect._tcp` (không cần mã) nhờ khoá RSA đã lưu (xem lại file 04, Bước 5).

## Bước 4: Ghép luồng hoàn chỉnh — trải nghiệm giống Mantis

1. Notification "Wireless debugging Connected" xuất hiện → `WirelessDebugListenerService` bắt
   được → phát sự kiện.
2. App tự động chạy `discoverConnectService()` → nếu tìm thấy `_adb-tls-connect._tcp` (nghĩa là
   máy **đã pairing từ trước**) → tự `connect()` thẳng, không cần hỏi lại người dùng gì cả.
3. Nếu chưa từng pairing (không có connect service, hoặc kết nối bị từ chối vì thiếu khoá) →
   app tự mở đúng màn hình Settings pairing (`ACTION_APPLICATION_DEVELOPMENT_SETTINGS`) và hiện
   overlay hướng dẫn nhập mã 6 số — chỉ xảy ra 1 lần duy nhất cho mỗi thiết bị.
4. Sau khi pairing xong, app lưu khoá (file 04 Bước 5), lần mở app sau này quy trình rút gọn lại
   thành: mở app → thấy notification đã bật sẵn → tự connect ngay, không thao tác gì thêm.

## Bước 5: Notification riêng của app làm "lối tắt" (tuỳ chọn UX)

Ngoài việc lắng nghe, app có thể **tự đăng 1 notification cố định của chính mình** (giống nút
"Pair now" trong ảnh Mantis bạn từng gửi) để rút ngắn số bước điều hướng:

```kotlin
fun postPairingShortcutNotification(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
    val pendingIntent = PendingIntent.getActivity(context, 0, intent,
        PendingIntent.FLAG_IMMUTABLE)

    val notif = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_gamepad)
        .setContentTitle("Chưa kết nối MantisBuddy")
        .setContentText("Bấm để mở Wireless debugging và ghép nối")
        .setContentIntent(pendingIntent)
        .setOngoing(true)
        .build()

    NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
}
```
Notification này chỉ là **lối tắt điều hướng** — không đọc được data gì, chỉ giúp người dùng bớt
phải tự mò vào Settings.

## Checklist
- [ ] Xin được quyền Notification Listener, kiểm tra đúng trạng thái đã cấp/chưa cấp.
- [ ] Bắt được sự kiện bật/tắt Wireless debugging qua notification hệ thống.
- [ ] `NsdManager` tự dò được `_adb-tls-connect._tcp` khi thiết bị đã pairing sẵn.
- [ ] Luồng tự connect lại không cần nhập mã ở các lần mở app sau lần đầu.
- [ ] Notification lối tắt của app tự mở đúng màn hình Settings khi bấm vào.

Tiếp theo: quay lại **05-DAEMON-TIEM-TOUCH.md**, phần `deployAndStartDaemon()` giờ sẽ được gọi
ngay sau khi `discoverConnectService()` trả về `host:port` ở Bước 3 của file này.

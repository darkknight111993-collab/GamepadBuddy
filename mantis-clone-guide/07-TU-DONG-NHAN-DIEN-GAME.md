# 07 — Tự động nhận diện game đang mở & load đúng Profile

## Mục tiêu
Khi người dùng mở 1 game đã có Profile, overlay + mapping tự động kích hoạt đúng cấu hình,
không cần thao tác thủ công.

## Cách 1: UsageStatsManager (không cần Accessibility Service)

```kotlin
fun getForegroundApp(context: Context): String? {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val end = System.currentTimeMillis()
    val begin = end - 10_000
    val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, end)
    return stats?.maxByOrNull { it.lastTimeUsed }?.packageName
}
```
- Cần quyền `PACKAGE_USAGE_STATS`, xin thủ công qua:
```kotlin
context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
```
- Polling mỗi 1–2 giây từ `OverlayService` để phát hiện đổi app.

## Cách 2 (chính xác/tức thời hơn): AccessibilityService

```kotlin
class AppWatcherService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            ProfileManager.onForegroundAppChanged(pkg)
        }
    }
}
```
Cần khai báo `res/xml/accessibility_service_config.xml` và người dùng bật thủ công trong
Settings → Accessibility. Cách này phản hồi tức thời, không cần polling, nhưng nhiều người dùng
ngại cấp quyền Accessibility (dễ bị Play Store soi khi submit app vì quyền này thường dùng cho
sai mục đích).

> Khuyến nghị: dùng **Cách 1** làm mặc định (ít nhạy cảm hơn khi lên Google Play), chỉ dùng
> Accessibility như tuỳ chọn nâng cao.

## Bước 2: Kích hoạt/tắt overlay tương ứng

```kotlin
object ProfileManager {
    fun onForegroundAppChanged(pkg: String) {
        val profile = profileRepository.getDefaultForPackage(pkg)
        if (profile != null) {
            OverlayService.showFor(profile)
        } else {
            OverlayService.hideOrShowAddPrompt(pkg) // gợi ý "ADD" như trong UI Mantis
        }
    }
}
```

## Bước 3: Danh sách "Games" trên màn hình chính

- Liệt kê các app đã có Profile bằng `PackageManager.getInstalledApplications()`.
- Nút "ADD" mở danh sách app đã cài để người dùng chọn game muốn thêm (giống ảnh bạn gửi).

## Checklist
- [ ] Phát hiện đúng khi chuyển từ Home → game đã có Profile → overlay tự hiện.
- [ ] Phát hiện đúng khi thoát game → overlay tự ẩn (tránh đè lên Home/app khác).
- [ ] Màn hình danh sách Games + nút ADD hoạt động.

Tiếp theo: **08-BACKEND-TAI-KHOAN.md** (tuỳ chọn) hoặc bỏ qua thẳng tới **10-LO-TRINH-MVP.md**

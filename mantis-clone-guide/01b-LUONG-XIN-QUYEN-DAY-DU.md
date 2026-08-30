# 01b — Luồng xin phân quyền đầy đủ (QuickStart Wizard)

Bạn nói đúng: chỉ làm phần ADB (file 04, 04b) là chưa đủ để app *chạy được* — đó mới chỉ là
**1 trong 6+ quyền** cần thiết. Ảnh QuickStart bạn chụp ban đầu cho thấy Mantis xin tuần tự
**6 quyền/khối cấu hình khác nhau**, mỗi cái phục vụ 1 chức năng riêng. File này liệt kê đầy đủ
tất cả các quyền cần xin (kể cả những cái không có trong 6 bước hiển thị nhưng bắt buộc phải có
ngầm), và cách dựng màn hình wizard giống hệt cấu trúc Mantis.

## Bảng đầy đủ các quyền cần thiết — vì sao thiếu 1 cái là app không hoạt động

| # | Quyền / cấu hình | Dùng cho việc gì | Thiếu thì hậu quả gì |
|---|---|---|---|
| 1 | `POST_NOTIFICATIONS` | Hiện notification trạng thái, lối tắt pairing (file 04b) | Không hiện được cảnh báo/foreground service trên Android 13+ → service dễ bị hệ thống kill |
| 2 | `SYSTEM_ALERT_WINDOW` (Draw over other apps) | Vẽ overlay nút ảo/joystick (file 03) | **Không vẽ được overlay** — toàn bộ app vô dụng, đây là quyền lõi nhất |
| 3 | Developer Options bật | Điều kiện tiên quyết để thấy mục Wireless debugging | Không có mục nào để bật debugging cả |
| 4 | USB debugging bật | Một số máy yêu cầu bật cái này trước khi Wireless debugging hoạt động ổn định | Wireless debugging chập chờn/không pairing được trên 1 số ROM |
| 5 | Wireless debugging bật + pairing | Cấp quyền `shell` cho daemon tiêm touch (file 04, 05) | **Không tiêm được touch** — tay cầm bấm nhưng game không phản hồi |
| 6 | Notification Listener Access | Tự phát hiện trạng thái debugging, tự động reconnect (file 04b) | Mất tính năng tự động, phải mở app thủ công mỗi lần |
| 7 | Usage Access (`PACKAGE_USAGE_STATS`) | Nhận diện app đang mở để tự load đúng profile (file 07) | Overlay không tự bật/tắt theo đúng game |
| 8 | Bỏ tối ưu pin (Ignore Battery Optimizations) | Giữ Foreground Service + daemon sống khi màn hình tắt/chuyển app | Daemon bị hệ thống (đặc biệt MIUI/OneUI) kill ngầm, mất kết nối liên tục |
| 9 *(tuỳ chọn)* | Accessibility Service | Thay thế cho #7 với độ chính xác cao hơn | Không bắt buộc nếu đã có #7 |

> Đây chính là lý do Mantis không chỉ hỏi "1 câu" mà dựng hẳn 1 màn hình wizard nhiều bước —
> vì đúng là cần nhiều quyền độc lập, mỗi cái xin theo 1 API khác nhau của Android, không có
> API nào gộp chung xin 1 lần được.

## Bước 1: Model hoá từng bước thành danh sách tuần tự

```kotlin
data class OnboardingStep(
    val id: String,
    val title: String,
    val description: String,
    val isGranted: (Context) -> Boolean,
    val requestAction: (Activity) -> Unit
)

val onboardingSteps = listOf(
    OnboardingStep(
        id = "notifications",
        title = "Bật quyền thông báo",
        description = "Cho phép hiện thông báo trạng thái kết nối",
        isGranted = { ctx -> NotificationManagerCompat.from(ctx).areNotificationsEnabled() },
        requestAction = { act ->
            if (Build.VERSION.SDK_INT >= 33) {
                ActivityCompat.requestPermissions(act,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    ),
    OnboardingStep(
        id = "overlay",
        title = "Bật hiển thị đè lên ứng dụng khác",
        description = "Bắt buộc để vẽ nút/joystick ảo lên trên game",
        isGranted = { ctx -> Settings.canDrawOverlays(ctx) },
        requestAction = { act ->
            act.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${act.packageName}")))
        }
    ),
    OnboardingStep(
        id = "dev_options",
        title = "Bật tuỳ chọn nhà phát triển",
        description = "Bấm liên tục 7 lần vào 'Số hiệu bản dựng' trong Giới thiệu điện thoại",
        isGranted = { ctx ->
            Settings.Global.getInt(ctx.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        },
        requestAction = { act ->
            act.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }
    ),
    OnboardingStep(
        id = "usb_debugging",
        title = "Bật USB Debugging",
        description = "Điều kiện nền để Wireless debugging hoạt động ổn định",
        isGranted = { ctx ->
            Settings.Global.getInt(ctx.contentResolver, "adb_enabled", 0) == 1
        },
        requestAction = { act ->
            act.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }
    ),
    OnboardingStep(
        id = "wireless_debugging",
        title = "Bật Wireless Debugging",
        description = "Cấp quyền shell cho MantisBuddy hoạt động",
        isGranted = { ctx ->
            // Key ẩn, đọc được bình thường không cần quyền đặc biệt trên hầu hết ROM
            Settings.Global.getInt(ctx.contentResolver, "adb_wifi_enabled", 0) == 1
        },
        requestAction = { act ->
            act.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }
    ),
    OnboardingStep(
        id = "pairing",
        title = "Ghép nối qua mã 6 số",
        description = "Chỉ cần làm 1 lần duy nhất cho mỗi thiết bị",
        isGranted = { ctx -> PairingStore.hasValidKey(ctx) }, // đã lưu khoá ở file 04 Bước 5
        requestAction = { act -> act.startActivity(Intent(act, PairingActivity::class.java)) }
    ),
    OnboardingStep(
        id = "notification_listener",
        title = "Cho phép truy cập thông báo",
        description = "Tự phát hiện trạng thái debugging, tự kết nối lại",
        isGranted = { ctx -> isNotificationAccessGranted(ctx) }, // đã viết ở file 04b
        requestAction = { act ->
            act.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    ),
    OnboardingStep(
        id = "usage_access",
        title = "Cho phép truy cập dữ liệu sử dụng",
        description = "Để tự nhận diện game đang mở",
        isGranted = { ctx -> isUsageAccessGranted(ctx) },
        requestAction = { act ->
            act.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    ),
    OnboardingStep(
        id = "battery",
        title = "Bỏ tối ưu hoá pin",
        description = "Tránh bị hệ thống tắt ngầm khi chơi lâu",
        isGranted = { ctx ->
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(ctx.packageName)
        },
        requestAction = { act ->
            act.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${act.packageName}")))
        }
    )
)

fun isUsageAccessGranted(ctx: Context): Boolean {
    val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
        android.os.Process.myUid(), ctx.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}
```

## Bước 2: Màn hình Wizard — đúng bố cục timeline như ảnh QuickStart bạn chụp

```kotlin
class OnboardingActivity : AppCompatActivity() {
    private lateinit var adapter: StepListAdapter

    override fun onResume() {
        super.onResume()
        // Quan trọng: phải re-check TOÀN BỘ mỗi khi quay lại app, vì user vừa đi qua Settings
        adapter.refreshStates(onboardingSteps.map { it.isGranted(this) })
        if (onboardingSteps.all { it.isGranted(this) }) {
            startService(Intent(this, OverlayService::class.java))
            finish()
        }
    }
}
```

Mỗi item trong danh sách hiện: số thứ tự trong vòng tròn (xanh nếu đã xong), tiêu đề, mô tả ngắn,
và **chỉ bước đang active (bước đầu tiên chưa hoàn thành) mới xổ ra nút "Enable"** — các bước sau
hiện mờ/thu gọn cho tới khi tới lượt, giống đúng hiệu ứng bạn thấy trong ảnh Mantis (chỉ bước 1
"Enable Notification Permission" xổ ra nút Enable, còn bước 2–6 thu gọn).

```kotlin
class StepListAdapter(private val steps: List<OnboardingStep>) :
    RecyclerView.Adapter<StepViewHolder>() {

    private var states = List(steps.size) { false }

    fun refreshStates(newStates: List<Boolean>) {
        states = newStates
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        val step = steps[position]
        val isDone = states[position]
        val isActive = !isDone && states.take(position).all { it } // bước liền trước đã xong

        holder.bind(step, isDone, isActive)
        holder.enableButton.visibility = if (isActive) View.VISIBLE else View.GONE
        holder.enableButton.setOnClickListener {
            step.requestAction(holder.itemView.context as Activity)
        }
    }
}
```

## Bước 3: Vì sao PHẢI xếp tuần tự (không cho nhảy cóc)

- Bước "Wireless debugging" (5) không tồn tại trên màn hình Settings nếu "Developer Options" (3)
  chưa bật → bắt buộc thứ tự.
- Bước "Ghép nối mã 6 số" (6) chỉ khả dụng khi "Wireless debugging" (5) đã bật → bắt buộc thứ tự.
- Các bước 1, 2, 7, 8, 9 độc lập nhau, có thể làm ở bất kỳ thứ tự nào — nhưng vẫn nên xếp cố định
  để trải nghiệm nhất quán, tránh người dùng bối rối.

## Bước 4: Từng quyền dùng ĐÚNG intent Settings nào (bảng tra nhanh)

| Quyền | Intent action |
|---|---|
| Notification | `ActivityCompat.requestPermissions` (không phải Intent) |
| Overlay | `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` |
| Developer Options | `Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS` |
| USB/Wireless debugging | `Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS` (cùng màn hình, khác mục con) |
| Notification Listener | `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` |
| Usage Access | `Settings.ACTION_USAGE_ACCESS_SETTINGS` |
| Bỏ tối ưu pin | `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |

## Checklist
- [ ] Toàn bộ 9 bước có hàm `isGranted()` đọc đúng trạng thái thật (test bằng cách tắt/bật thủ
      công từng cái trong Settings rồi mở lại app, xem app có nhận đúng không).
- [ ] Wizard chỉ cho làm bước hiện tại, khoá các bước sau cho tới khi bước trước xong.
- [ ] `onResume()` luôn tái kiểm tra toàn bộ — không tin vào trạng thái đã lưu trong bộ nhớ, vì
      người dùng có thể tắt quyền bất kỳ lúc nào trong Settings.
- [ ] Sau khi cả 9 bước hoàn thành → tự động khởi động `OverlayService`, không cần bấm gì thêm.
- [ ] Có màn hình riêng giải thích **vì sao** cần từng quyền (đặc biệt Overlay + Usage Access dễ
      khiến người dùng nghi ngại) — tăng tỷ lệ người dùng đồng ý thay vì bỏ giữa chừng.

Đọc tiếp: quay lại **03-OVERLAY-EDITOR.md** và **04b-GHEP-NOI-QUA-THONG-BAO.md** — cả 2 module đó
giờ được kích hoạt đúng lúc thông qua wizard này thay vì gọi rời rạc.

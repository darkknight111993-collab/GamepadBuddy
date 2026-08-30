package com.gamepadbuddy.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import com.gamepadbuddy.pairing.PairingActivity
import com.gamepadbuddy.pairing.PairingStore

/**
 * Model 1 bước phân quyền trong Onboarding Wizard (file 01b). 9 bước tuần tự, mỗi bước có
 * hàm isGranted() đọc trạng thái thật và requestAction() mở đúng màn hình Settings/tên quyền.
 */
data class OnboardingStep(
    val id: String,
    val title: String,
    val description: String,
    val isGranted: (Context) -> Boolean,
    val requestAction: (Activity) -> Unit,
    /** Bước có thể xác nhận thủ công "tôi đã xong" khi hệ thống đọc trạng thái không tin cậy (vd: tối ưu pin). */
    val manualConfirmable: Boolean = false
)

val onboardingSteps = listOf(
    OnboardingStep(
        id = "notifications",
        title = "Bật quyền thông báo",
        description = "Cho phép hiện thông báo trạng thái kết nối",
        isGranted = { ctx -> areNotificationsEnabled(ctx) },
        requestAction = { act ->
            if (Build.VERSION.SDK_INT >= 33)
                ActivityCompat.requestPermissions(act, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    ),
    OnboardingStep(
        id = "overlay",
        title = "Bật hiển thị đè lên ứng dụng khác",
        description = "Bắt buộc để vẽ nút/joystick ảo lên trên game",
        isGranted = { ctx -> Settings.canDrawOverlays(ctx) },
        requestAction = { act ->
            act.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${act.packageName}"))
            )
        }
    ),
    OnboardingStep(
        id = "dev_options",
        title = "Bật tuỳ chọn nhà phát triển",
        description = "Bấm 7 lần vào 'Số hiệu bản dựng' trong Giới thiệu điện thoại",
        isGranted = { ctx -> isDevOptionsEnabled(ctx) },
        requestAction = { act -> act.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
    ),
    OnboardingStep(
        id = "usb_debugging",
        title = "Bật USB / Wireless Debugging",
        description = "Bật ít nhất 1 trong 2 để daemon có quyền shell",
        isGranted = { ctx -> isAdbEnabled(ctx) },
        requestAction = { act -> act.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
    ),
    OnboardingStep(
        id = "wireless_debugging",
        title = "Bật Wireless Debugging",
        description = "Cấp quyền shell cho daemon tiêm touch",
        isGranted = { ctx -> isWirelessDebuggingActive(ctx) },
        requestAction = { act -> act.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
    ),
    OnboardingStep(
        id = "pairing",
        title = "Ghép nối qua mã 6 số",
        description = "Chỉ cần làm 1 lần cho mỗi thiết bị",
        isGranted = { ctx -> PairingStore.hasValidKey(ctx) },
        requestAction = { act -> act.startActivity(Intent(act, PairingActivity::class.java)) }
    ),
    OnboardingStep(
        id = "battery",
        title = "Bỏ tối ưu hoá pin",
        description = "Tránh bị hệ thống tắt ngầm khi chơi lâu",
        isGranted = { ctx -> isIgnoringBattery(ctx) },
        requestAction = { act ->
            act.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${act.packageName}"))
            )
        },
        manualConfirmable = true
    )
)

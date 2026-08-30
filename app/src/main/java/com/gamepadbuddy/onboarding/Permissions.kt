package com.gamepadbuddy.onboarding

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** Các hàm kiểm tra trạng thái quyền thật từ hệ thống (file 01b). */

fun isWirelessDebuggingActive(ctx: Context): Boolean =
    Settings.Global.getInt(ctx.contentResolver, "adb_wifi_enabled", 0) == 1

fun isDevOptionsEnabled(ctx: Context): Boolean =
    Settings.Global.getInt(ctx.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1

fun isUsbDebuggingEnabled(ctx: Context): Boolean =
    Settings.Global.getInt(ctx.contentResolver, "adb_enabled", 0) == 1

/**
 * Trạng thái cài đặt ADB thực tế trên thiết bị.
 * LƯU Ý: app chạy trên device KHÔNG thể biết trạng thái "offline" phía host
 * (đó là khái niệm của `adb devices` trên PC). Hàm này chỉ phản ánh *cài đặt* ADB.
 */
enum class AdbStatus { USB, WIRELESS, DISABLED }

fun getAdbStatus(ctx: Context): AdbStatus =
    when {
        isWirelessDebuggingActive(ctx) -> AdbStatus.WIRELESS
        isUsbDebuggingEnabled(ctx) -> AdbStatus.USB
        else -> AdbStatus.DISABLED
    }

/** ADB (USB hoặc Wireless) đã được bật cài đặt hay chưa. */
fun isAdbEnabled(ctx: Context): Boolean =
    isUsbDebuggingEnabled(ctx) || isWirelessDebuggingActive(ctx)

fun isIgnoringBattery(ctx: Context): Boolean {
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(ctx.packageName)
}

fun areNotificationsEnabled(ctx: Context): Boolean =
    NotificationManagerCompat.from(ctx).areNotificationsEnabled()

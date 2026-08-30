package com.gamepadbuddy.pairing

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * Phát hiện trạng thái Wireless debugging KHÔNG dùng NotificationListenerService
 * (bị HyperOS / Android 13+ chặn qua "Restricted Settings").
 *
 * Thay vào đó đọc trực tiếp Settings.Global "adb_wifi_enabled" — API chuẩn, không cần
 * quyền nhạy cảm, hoạt động trên mọi ROM. Khi trạng thái thay đổi, cập nhật
 * [PairingStateBus.wirelessDebuggingActive] để luồng auto-reconnect (PairingCoordinator)
 * vẫn chạy như cũ.
 */
object WirelessDebugging {
    private const val KEY = "adb_wifi_enabled"
    private var observer: ContentObserver? = null

    fun isEnabled(context: Context): Boolean =
        runCatching { Settings.Global.getInt(context.contentResolver, KEY, 0) == 1 }
            .getOrDefault(false)

    fun register(context: Context) {
        if (observer != null) return
        val uri = Settings.Global.getUriFor(KEY)
        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                PairingStateBus.onWirelessDebuggingActive(isEnabled(context))
            }
        }
        context.contentResolver.registerContentObserver(uri, false, observer!!)
        PairingStateBus.onWirelessDebuggingActive(isEnabled(context))
    }

    fun unregister(context: Context) {
        observer?.let { context.contentResolver.unregisterContentObserver(it) }
        observer = null
    }
}

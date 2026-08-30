package com.gamepadbuddy.pairing

import androidx.lifecycle.MutableLiveData

/**
 * Bus trạng thái Wireless debugging (file 04b/04c).
 *
 * Không còn dùng NotificationListenerService (bị HyperOS / Android 13+ chặn).
 * - Trạng thái bật/tắt Wireless debugging lấy từ Settings.Global "adb_wifi_enabled"
 *   (xem [WirelessDebugging]).
 * - PairingService (và UI) quan sát để tự động dò endpoint và reconnect.
 */
data class PairingEndpoint(val host: String, val port: Int)

object PairingStateBus {
    /** Wireless debugging đang bật (từ Settings.Global "adb_wifi_enabled"). */
    val wirelessDebuggingActive = MutableLiveData(false)
    /** Endpoint ghép nối (từ mDNS `_adb-tls-pairing._tcp`). */
    val pairingEndpoint = MutableLiveData<PairingEndpoint?>(null)
    /** Thông báo tiến trình cho UI. */
    val status = MutableLiveData("")

    fun onWirelessDebuggingActive(active: Boolean) = wirelessDebuggingActive.postValue(active)
    fun postPairingEndpoint(e: PairingEndpoint?) = pairingEndpoint.postValue(e)
    fun postStatus(s: String) = status.postValue(s)
}

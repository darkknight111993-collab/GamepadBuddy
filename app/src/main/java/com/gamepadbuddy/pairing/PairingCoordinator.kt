package com.gamepadbuddy.pairing

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * File 04b - Bước 4: phối hợp luồng tự động. Khi Wireless debugging vừa bật VÀ đã có khoá
 * (đã pair lần đầu), tự dò mDNS connect service → connect → deploy daemon (không hỏi lại).
 * Nếu chưa có khoá, bỏ qua (người dùng vẫn phải pair thủ công 1 lần).
 */
object PairingCoordinator {

    fun onWirelessActive(context: Context) {
        if (!PairingStore.hasValidKey(context)) return
        val discovery = AdbServiceDiscovery(context)
        discovery.discoverConnect { host, port ->
            discovery.stop() // dừng dò (và giải phóng MulticastLock) sau khi tìm thấy
            CoroutineScope(Dispatchers.IO).launch {
                val kadb = AdbBridge.connect(host, port) ?: return@launch
                kadb.use { AdbBridge.deployDaemon(it, DaemonAssets.copyBinary(context) ?: return@use) }
            }
        }
    }
}

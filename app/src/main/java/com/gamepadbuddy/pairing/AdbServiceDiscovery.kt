package com.gamepadbuddy.pairing

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager

/**
 * File 04b - Bước 3: tự động dò IP:Port qua mDNS.
 *  - discoverPairing(): `_adb-tls-pairing._tcp` — chỉ xuất hiện khi bấm
 *    "Pair device with pairing code" (dùng để `adb pair`).
 *  - discoverConnect(): `_adb-tls-connect._tcp` — chỉ xuất hiện SAU KHI đã pair xong
 *    (dùng để `adb connect` chính thức, không cần mã).
 *
 * Giữ WifiManager.MulticastLock trong suốt quá trình discovery để nhận gói mDNS đáng tin cậy
 * trên mọi thiết bị/Rom (Android thường khoá multicast mặc định, khiến NSD hay "câm").
 */
class AdbServiceDiscovery(private val context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var listener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun discoverPairing(onFound: (host: String, port: Int) -> Unit) =
        discover("_adb-tls-pairing._tcp", onFound)

    fun discoverConnect(onFound: (host: String, port: Int) -> Unit) =
        discover("_adb-tls-connect._tcp", onFound)

    /** Dừng discovery và giải phóng MulticastLock (idempotent). */
    @Synchronized
    fun stop() {
        listener?.let { nsd.stopServiceDiscovery(it) }
        listener = null
        multicastLock?.let { if (it.isHeld) it.release() }
        multicastLock = null
    }

    private fun discover(serviceType: String, onFound: (host: String, port: Int) -> Unit) {
        // Giải phóng discovery cũ (nếu có) trước khi bắt đầu mới.
        stop()

        multicastLock = (context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createMulticastLock("AdbMdnsLock").apply {
                setReferenceCounted(false)
                acquire()
            }

        val want = if (serviceType.startsWith("_adb-tls-pairing")) "_adb-tls-pairing"
        else "_adb-tls-connect"

        listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                // Lọc đúng type (API có thể trả về "..._tcp." hoặc "..._tcp.local.").
                if (!info.serviceType.contains(want)) return
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val host = resolved.host?.hostAddress ?: return
                        onFound(host, resolved.port)
                    }

                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                })
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceLost(service: NsdServiceInfo) {}
        }
        nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
    }
}

package com.gamepadbuddy.pairing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.Observer
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean

/**
 * File 04c: Foreground service tự động hoá luồng pairing/connect ADB không dây.
 *
 * Cơ chế ĐÚNG (chuẩn Android, KHÔNG dùng NotificationListenerService):
 *  1) Dò pairing port qua mDNS `_adb-tls-pairing._tcp` (không phụ thuộc notification).
 *  2) User nhập mã 6 số TAY vào Floating Overlay (SYSTEM_ALERT_WINDOW) hoặc trực tiếp trong app.
 *  3) Kadb.pair(host, pairingPort, code).
 *  4) SAU KHI pair xong → dò connect port qua mDNS `_adb-tls-connect._tcp` → Kadb.create → deploy daemon.
 */
class PairingService : android.app.Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private var code: String? = null
    private val discovery by lazy { AdbServiceDiscovery(this) }

    private val pairObserver = Observer<PairingEndpoint?> { maybeRun() }

    override fun onCreate() {
        super.onCreate()
        val notif = buildNotification("Đang chờ Wireless debugging...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this, NOTIF_ID, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
        // Kích hoạt discovery (lazy) SAU khi context đã hợp lệ.
        discovery
        PairingStateBus.pairingEndpoint.observeForever(pairObserver)
        // Chủ động dò pairing endpoint qua mDNS (cách chuẩn, không phụ thuộc notification).
        discovery.discoverPairing { host, port ->
            PairingStateBus.postPairingEndpoint(PairingEndpoint(host, port))
            // Hiện thông báo xổ xuống có nút "Nhập mã" để gõ 6 số ngay tại chỗ
            // (không cần chuyển qua lại giữa Cài đặt và ứng dụng).
            postCodePrompt("$host:$port")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                PairingStateBus.postStatus(
                    "Đã bắt đầu. Bật Wireless debugging → Pair device with pairing code. App tự dò port qua mDNS."
                )
                // Hiện ngay notification nhập mã (RemoteInput) để user gõ mã 6 số bất cứ lúc nào,
                // không cần chờ mDNS. Khi dò được IP, postCodePrompt sẽ cập nhật lại nội dung.
                postCodePrompt(PairingStateBus.pairingEndpoint.value?.let { "${it.host}:${it.port}" } ?: "chưa dò được IP")
            }
            ACTION_SUBMIT_CODE -> {
                code = intent.getStringExtra(EXTRA_CODE)?.trim()
                PairingStateBus.postStatus("Đã nhận mã. Đang chờ port pairing từ mDNS...")
                maybeRun()
            }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    private fun maybeRun() {
        if (running.get()) return
        val pair = PairingStateBus.pairingEndpoint.value
        val c = code
        // CHỈ cần pairingEndpoint + mã do user nhập. Connect endpoint lấy SAU khi pair xong.
        if (pair != null && !c.isNullOrBlank()) {
            if (running.compareAndSet(false, true)) runPairing(pair, c)
        }
    }

    private fun runPairing(pair: PairingEndpoint, code: String) {
        scope.launch {
            updateNotif("Đang pair ${pair.host}:${pair.port}...")
            val paired = AdbBridge.pair(pair.host, pair.port, code)
            if (paired) PairingStore.markPaired(this@PairingService)
            PairingStateBus.postStatus("Pair: $paired")
            if (!paired) { finish("Pair thất bại ❌"); return@launch }

            // Sau khi pair thành công, dò connect endpoint (mDNS _adb-tls-connect._tcp).
            updateNotif("Đang dò connect endpoint...")
            val conn = discoverConnectEndpoint()
            discovery.stop() // đã có connect endpoint, không cần dò tiếp
            val cadb = conn ?: run {
                PairingStateBus.postStatus("Không dò được connect endpoint (kiểm tra Wi-Fi/mDNS)")
                finish("Thiếu connect endpoint ❌"); return@launch
            }
            updateNotif("Đang connect ${cadb.host}:${cadb.port}...")
            val kadb = AdbBridge.connect(cadb.host, cadb.port)
            if (kadb == null) { PairingStateBus.postStatus("Connect thất bại"); finish("Connect thất bại ❌"); return@launch }
            kadb.use {
                val bin = DaemonAssets.copyBinary(this@PairingService)
                if (bin == null) { PairingStateBus.postStatus("Thiếu binary daemon trong assets/"); finish("Thiếu binary ❌"); return@use }
                AdbBridge.deployDaemon(it, bin)
                PairingStateBus.postStatus("Đã deploy & chạy daemon. App tự nối socket @mantisbridge.")
                finish("Hoàn tất ✅")
            }
        }
    }

    /** Dò connect endpoint qua mDNS (chuẩn). Nếu mDNS không ra trong 8s, coi như thiếu. */
    private suspend fun discoverConnectEndpoint(): PairingEndpoint? = suspendCancellableCoroutine { cont ->
        var resolved = false
        fun complete(endpoint: PairingEndpoint?) {
            if (!resolved) { resolved = true; cont.resume(endpoint) }
        }
        discovery.discoverConnect { host, port -> complete(PairingEndpoint(host, port)) }
        // Nếu mDNS không ra trong 8s, coi như thiếu connect endpoint.
        scope.launch { delay(8000); complete(null) }
    }

    private fun finish(msg: String) {
        discovery.stop()
        updateNotif(msg)
        scope.launch {
            delay(2000)
            stopSelf()
        }
    }

    /**
     * Post một notification THƯỜNG (không phải của foreground service) có action "Nhập mã".
     * Bấm vào sẽ mở CodeInputActivity (dialog nổi trên mọi app) để gõ 6 số ngay tại chỗ.
     * Dùng notification thường thay vì RemoteInput vì Android 11+ cấm RemoteInput trên
     * notification của foreground service.
     */
    /**
     * Post một notification THƯỜNG (không phải của foreground service) có textbox nhập mã 6 số
     * trực tiếp trong khay thông báo (RemoteInput). Gõ xong là hệ thống gửi về CodeInputReceiver.
     * RemoteInput dùng được ở đây vì notification này là notification THƯỜNG (Android 11+ chỉ cấm
     * RemoteInput trên notification của foreground service, còn cái startForeground là NOTIF_ID).
     */
    private fun postCodePrompt(endpoint: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(PROMPT_CHANNEL, "Nhập mã ghép nối", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        // Action "Nhập mã" mang theo RemoteInput; khi user gõ xong, hệ thống bắn intent về receiver.
        val action = NotificationCompat.Action.Builder(
            android.R.drawable.ic_input_add,
            "Nhập mã 6 số",
            CodeInputReceiver.pendingIntent(this)
        ).addRemoteInput(CodeInputReceiver.remoteInput()).build()

        val prompt = NotificationCompat.Builder(this, PROMPT_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Đã dò được $endpoint")
            .setContentText("Kéo xuống, bấm 'Nhập mã 6 số' và gõ mã ghép nối")
            .addAction(action)
            .setAutoCancel(false)
            .build()
        nm.notify(PROMPT_NOTIF_ID, prompt)
    }

    private fun updateNotif(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "ADB Pairing", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
        // Bấm notification -> mở PairingActivity để user nhập mã 6 số (thay cho RemoteInput
        // đã bị gỡ bỏ trên Android 11+).
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, PairingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("GamepadBuddy · ADB Pairing")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        discovery.stop()
        PairingStateBus.pairingEndpoint.removeObserver(pairObserver)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.gamepadbuddy.pairing.START"
        const val ACTION_SUBMIT_CODE = "com.gamepadbuddy.pairing.SUBMIT_CODE"
        const val ACTION_STOP = "com.gamepadbuddy.pairing.STOP"
        const val EXTRA_CODE = "extra_code"
        const val NOTIF_ID = 4040
        const val CHANNEL = "gpb_adb_pairing"
        private const val PROMPT_CHANNEL = "gpb_adb_prompt"
        private const val PROMPT_NOTIF_ID = 4041

        fun start(context: Context) =
            context.startForegroundService(Intent(context, PairingService::class.java).setAction(ACTION_START))

        fun submitCode(context: Context, code: String) =
            context.startForegroundService(
                Intent(context, PairingService::class.java).setAction(ACTION_SUBMIT_CODE).putExtra(EXTRA_CODE, code)
            )
    }
}

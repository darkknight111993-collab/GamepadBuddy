package com.gamepadbuddy.pairing

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gamepadbuddy.databinding.ActivityPairingBinding

/**
 * Màn hình Hướng A (file 04): khởi chạy PairingService để tự động pairing/connect ADB không dây.
 *
 * KHÔNG dùng NotificationListenerService (bị HyperOS / Android 13+ chặn qua "Restricted Settings").
 * Thay vào đó (Mantis-style):
 *  - App tự dò port pairing qua mDNS (`_adb-tls-pairing._tcp`).
 *  - App tự dò port pairing qua mDNS (`_adb-tls-pairing._tcp`).
 *  - Mã 6 số được nhập trực tiếp vào RemoteInput trên notification (không cần mở app).
 */
class PairingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPairingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPairingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            binding.tvFallback.text =
                "Hướng A yêu cầu Android 11+ (API 30). Máy bạn dùng Hướng B: cắm cáp/máy tính chạy scripts/adb_deploy.sh."
            binding.btnStart.isEnabled = false
        }

        binding.btnDevOptions.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }

        binding.btnOverlayPerm.setOnClickListener {
            // Mở màn hình cấp quyền "Hiện trên cửa sổ khác" (SYSTEM_ALERT_WINDOW).
            // Quyền này HyperOS cho phép bật dễ dàng, không bị "Restricted Settings" chặn.
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        binding.btnStart.setOnClickListener {
            PairingService.start(this)
            toast("Đã khởi chạy. Bật Wireless debugging → Pair device with pairing code. Nhập mã 6 số qua thông báo.")
        }


        PairingStateBus.pairingEndpoint.observe(this) {
            binding.tvPairEndpoint.text = it?.let { "Pairing: ${it.host}:${it.port}" } ?: "Pairing: —"
        }
        PairingStateBus.status.observe(this) {
            binding.tvStatus.text = it
        }

        updateOverlayStatus()
    }

    override fun onResume() {
        super.onResume()
        updateOverlayStatus()
    }

    private fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(this)

    private fun updateOverlayStatus() {
        binding.tvOverlayStatus.text = if (hasOverlayPermission()) {
            "✅ Đã cấp quyền Hiện trên cửa sổ khác (cần để Overlay vẽ widget trong game)"
        } else {
            "⚠️ Chưa cấp quyền Hiện trên cửa sổ khác → Overlay không vẽ được widget trong game"
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

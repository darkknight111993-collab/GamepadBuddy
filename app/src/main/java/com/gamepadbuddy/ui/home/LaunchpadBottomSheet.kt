package com.gamepadbuddy.ui.home

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gamepadbuddy.databinding.SheetLaunchpadBinding
import com.gamepadbuddy.detector.DetectorState
import com.gamepadbuddy.onboarding.isUsageAccessGranted
import com.gamepadbuddy.overlay.OverlayService
import com.gamepadbuddy.pairing.PairingActivity
import com.gamepadbuddy.pairing.PairingCoordinator
import com.gamepadbuddy.pairing.PairingStore
import com.gamepadbuddy.profile.Profile

/**
 * File 11 #6 — Launchpad (BottomSheet): tóm tắt profile + kiểm tra điều kiện + Edit/Launch.
 *
 * Trước khi Launch, kiểm tra 3 điều kiện:
 *   1) Floating widget có thể hiện  → quyền SYSTEM_ALERT_WINDOW (Settings.canDrawOverlays).
 *   2) Keymapping có thể hoạt động   → OverlayService đang chạy (chứa MappingEngine + daemon).
 *   3) Tự nhận diện được game đang mở → quyền Usage Access (AppDetector cần để không trả về null).
 * Chỉ khi CẢ BA OK mới bật nút Launch (và Edit). Thiếu điều kiện nào sẽ hiện nút "sửa".
 */
class LaunchpadBottomSheet(private val profile: Profile) : BottomSheetDialogFragment() {

    private var _binding: SheetLaunchpadBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetLaunchpadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvName.text = profile.name
        binding.tvInfo.text = "Package: ${profile.packageName}\nWidgets: ${profile.widgets.size}"

        binding.btnEdit.setOnClickListener {
            requireContext().sendBroadcast(
                Intent(DetectorState.ACTION_EDIT_PROFILE).putExtra("package", profile.packageName)
            )
            dismiss()
        }

        binding.btnLaunch.setOnClickListener {
            val intent = requireContext().packageManager.getLaunchIntentForPackage(profile.packageName)
            if (intent != null) startActivity(intent)
            dismiss()
        }

        binding.btnFixOverlay.setOnClickListener {
            // Mở màn hình cấp quyền "Hiện trên cửa sổ khác".
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${requireContext().packageName}".toUri())
            )
        }

        binding.btnFixDetect.setOnClickListener {
            // Thiếu quyền này -> AppDetector.getForegroundPackage() luôn null -> bong bóng
            // không bao giờ hiện khi mở game, dù overlay/daemon đều "sẵn sàng".
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        binding.btnFixService.setOnClickListener {
            val ctx = requireContext()
            // Overlay luôn cần chạy (vẽ widget & dịch input tay cầm).
            ctx.startForegroundService(Intent(ctx, OverlayService::class.java))
            if (OverlayService.isDaemonConnected) {
                refreshStatus()
                return@setOnClickListener
            }
            if (PairingStore.hasValidKey(ctx)) {
                // Đã ghép nối trước đó → tự deploy & reconnect daemon qua wireless debugging.
                Toast.makeText(ctx, "Đang tự kết nối lại daemon...", Toast.LENGTH_SHORT).show()
                PairingCoordinator.onWirelessActive(ctx)
                // Polling refresh thay vì hẹn giờ cứng 1.5s (deploy + push binary mất vài giây).
                startDaemonPoll()
            } else {
                // Chưa deploy daemon → đưa user vào màn Pairing (ghép nối ADB không dây).
                Toast.makeText(ctx, "Cần ghép nối ADB để triển khai daemon", Toast.LENGTH_SHORT).show()
                startActivity(Intent(ctx, PairingActivity::class.java))
            }
        }

        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        // Cập nhật lại sau khi quay về từ màn hình cấp quyền.
        refreshStatus()
    }

    private fun refreshStatus() {
        val b = _binding ?: return
        val ctx = context ?: return
        val overlayOk = Settings.canDrawOverlays(ctx)
        val serviceRunning = OverlayService.isRunning
        val daemonOk = OverlayService.isDaemonConnected
        val keymapOk = serviceRunning && daemonOk
        // Thiếu quyền này là nguyên nhân phổ biến nhất khiến bong bóng không hiện dù overlay
        // và daemon đều OK — AppDetector không bao giờ tìm thấy đúng game đang mở.
        val detectOk = isUsageAccessGranted(ctx)

        setStatus(b.ivOverlayStatus, b.tvOverlayStatus, b.btnFixOverlay, overlayOk,
            "✅ Floating widget có thể hiện", "⚠️ Thiếu quyền Hiện trên cửa sổ khác")

        val keymapOkMsg = "✅ Keymapping sẵn sàng (daemon đã kết nối)"
        val keymapBadMsg = if (!serviceRunning) "⚠️ Overlay service chưa chạy" else "⚠️ Daemon chưa kết nối"
        setStatus(b.ivServiceStatus, b.tvServiceStatus, b.btnFixService, keymapOk, keymapOkMsg, keymapBadMsg)

        setStatus(b.ivDetectStatus, b.tvDetectStatus, b.btnFixDetect, detectOk,
            "✅ Tự nhận diện game đang mở", "⚠️ Thiếu quyền Usage Access — bong bóng sẽ KHÔNG hiện")

        val bothOk = overlayOk && keymapOk && detectOk
        b.btnEdit.isEnabled = bothOk
        b.btnLaunch.isEnabled = bothOk
    }

    private fun setStatus(icon: ImageView, text: TextView, fix: Button, ok: Boolean, okMsg: String, badMsg: String) {
        icon.setImageResource(if (ok) android.R.drawable.presence_online else android.R.drawable.presence_busy)
        val color = ContextCompat.getColor(
            requireContext(),
            if (ok) android.R.color.holo_green_dark else android.R.color.holo_red_dark
        )
        text.setTextColor(color)
        text.text = if (ok) okMsg else badMsg
        fix.visibility = if (ok) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }

    /** Polling refresh trạng thái daemon thay vì hẹn giờ cứng 1.5s.
     *  Deploy + push binary (2MB) qua mDNS mất vài giây, nên thử lại mỗi 1s trong 10s. */
    private fun startDaemonPoll() {
        val handler = Handler(Looper.getMainLooper())
        var tries = 0
        val runnable = object : Runnable {
            override fun run() {
                refreshStatus()
                if (OverlayService.isDaemonConnected || tries++ >= 10) return
                handler.postDelayed(this, 1000)
            }
        }
        handler.postDelayed(runnable, 1000)
    }
}

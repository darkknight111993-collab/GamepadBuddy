package com.gamepadbuddy.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.gamepadbuddy.databinding.FragmentSettingsBinding
import com.gamepadbuddy.overlay.OverlayService
import com.gamepadbuddy.pairing.PairingActivity

/**
 * Tab 3 — Cài đặt (file 11 #4): quyền (overlay/usage/dev/wireless/notification listener) +
 * điều khiển overlay + mở màn hình Pairing.
 */
class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnOverlay.setOnClickListener {
            if (Settings.canDrawOverlays(requireContext())) {
                requireContext().startForegroundService(Intent(requireContext(), OverlayService::class.java))
            } else {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${requireContext().packageName}"))
                )
            }
        }
        binding.btnStop.setOnClickListener {
            requireContext().stopService(Intent(requireContext(), OverlayService::class.java))
        }
        binding.btnUsage.setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        binding.btnDev.setOnClickListener { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
        binding.btnNotifListener.setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        binding.btnPairing.setOnClickListener { startActivity(Intent(requireContext(), PairingActivity::class.java)) }
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}

package com.gamepadbuddy.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.gamepadbuddy.databinding.ActivityOnboardingBinding
import com.gamepadbuddy.overlay.OverlayService
import com.gamepadbuddy.pairing.PairingCoordinator
import com.gamepadbuddy.pairing.PairingStateBus

/**
 * File 01b — Onboarding Wizard (gate quyền). Chỉ hiện cho tới khi đủ 9 quyền; onResume luôn
 * re-check trạng thái thật (vì người dùng có thể tắt quyền bất cứ lúc nào trong Settings).
 * Khi đủ → tự khởi động OverlayService và vào màn hình chính.
 */
class OnboardingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var adapter: StepListAdapter
    private val prefs by lazy { getSharedPreferences("gpb_onboarding", MODE_PRIVATE) }
    private val manualDone by lazy {
        prefs.getStringSet("manual_done", emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        adapter = StepListAdapter(onboardingSteps) { confirmStep(it) }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        PairingStateBus.wirelessDebuggingActive.observe(this) { active ->
            if (active) PairingCoordinator.onWirelessActive(this)
        }
    }

    override fun onResume() {
        super.onResume()
        recheck()
    }

    /** Tính lại trạng thái (kết hợp xác nhận thủ công) và chuyển màn hình nếu đủ điều kiện. */
    private fun recheck() {
        val states = onboardingSteps.map { it.isGranted(this) || manualDone.contains(it.id) }
        adapter.refreshStates(states)
        if (states.all { it }) {
            startForegroundService(Intent(this, OverlayService::class.java))
            startActivity(Intent(this, com.gamepadbuddy.MainActivity::class.java))
            finish()
        }
    }

    /** Người dùng xác nhận thủ công đã hoàn thành bước (dùng khi hệ thống đọc trạng thái không tin cậy). */
    private fun confirmStep(id: String) {
        manualDone.add(id)
        prefs.edit().putStringSet("manual_done", manualDone).apply()
        recheck()
    }
}

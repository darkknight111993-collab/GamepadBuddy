package com.gamepadbuddy

import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.gamepadbuddy.core.EventBus
import com.gamepadbuddy.databinding.ActivityMainBinding
import com.gamepadbuddy.input.applyDeadzone
import com.gamepadbuddy.ui.controllers.ControllersFragment
import com.gamepadbuddy.ui.home.HomeFragment
import com.gamepadbuddy.ui.settings.SettingsFragment

/**
 * Màn hình chính (file 11): Bottom Navigation 3 tab — Games / Tay cầm / Cài đặt.
 * Bắt KeyEvent/MotionEvent ở mức Activity → đẩy vào EventBus cho ControllersFragment.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge bắt buộc từ Android 15 (API 35): đẩy BottomNavigationView xuống dưới thanh điều hướng hệ thống.
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { v, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, navBar.bottom)
            insets
        }

        if (savedInstanceState == null) showFragment(HomeFragment())
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> showFragment(HomeFragment())
                R.id.nav_controllers -> showFragment(ControllersFragment())
                R.id.nav_settings -> showFragment(SettingsFragment())
            }
            true
        }
    }

    private fun showFragment(f: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, f).commit()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isGamepadSource(event.source)) {
            val code = event.keyCode
            val down = event.action == KeyEvent.ACTION_DOWN
            EventBus.append("BTN ${keyName(code)} ${if (down) "DOWN" else "UP"}")
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) {
            val lx = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_X))
            val ly = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_Y))
            EventBus.append("AXIS L=($lx,$ly)")
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun isGamepadSource(source: Int): Boolean =
        source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
        source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

    private fun keyName(code: Int): String = when (code) {
        KeyEvent.KEYCODE_BUTTON_A -> "A"
        KeyEvent.KEYCODE_BUTTON_B -> "B"
        KeyEvent.KEYCODE_BUTTON_X -> "X"
        KeyEvent.KEYCODE_BUTTON_Y -> "Y"
        KeyEvent.KEYCODE_BUTTON_L1 -> "L1"
        KeyEvent.KEYCODE_BUTTON_R1 -> "R1"
        KeyEvent.KEYCODE_BUTTON_L2 -> "L2"
        KeyEvent.KEYCODE_BUTTON_R2 -> "R2"
        KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
        KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"
        KeyEvent.KEYCODE_BUTTON_START -> "START"
        KeyEvent.KEYCODE_BUTTON_SELECT -> "SELECT"
        KeyEvent.KEYCODE_DPAD_UP -> "DPAD_UP"
        KeyEvent.KEYCODE_DPAD_DOWN -> "DPAD_DOWN"
        KeyEvent.KEYCODE_DPAD_LEFT -> "DPAD_LEFT"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "DPAD_RIGHT"
        else -> "KEY$code"
    }
}

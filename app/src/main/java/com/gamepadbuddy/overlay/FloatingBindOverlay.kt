package com.gamepadbuddy.overlay

import com.gamepadbuddy.R
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.gamepadbuddy.profile.AxisGroup

/**
 * Panel nổi (WindowManager TYPE_APPLICATION_OVERLAY) dùng để gán nút/cần vật lý cho 1 widget ảo
 * (file 06, phần "map joystick và nút"). Cùng phong cách với [com.gamepadbuddy.pairing.FloatingPairOverlay]:
 * panel bo góc, nền tối, WRAP_CONTENT, FLAG_NOT_FOCUSABLE (không cần focus vì chỉ có Button, còn
 * việc "nghe" phím tay cầm vẫn do KeyCaptureWindow đảm nhiệm — xem OverlayService.feedButton()).
 *
 * 2 chế độ:
 * - [Mode.BUTTON]: chờ 1 lần nhấn nút vật lý (OverlayService gọi [onKeyCaptured] khi bắt được),
 *   hiện tên nút vừa bắt rồi tự đóng sau khi gán.
 * - [Mode.JOYSTICK]: không cần bắt phím, chỉ hỏi chọn Cần trái/Cần phải bằng 2 nút bấm tay.
 *
 * QUAN TRỌNG: mọi thao tác WindowManager đều đẩy qua [mainHandler] (main thread), giống
 * FloatingPairOverlay, để tránh CalledFromWrongThreadException khi gọi từ vòng lặp daemon/coroutine.
 */
class FloatingBindOverlay private constructor(
    private val context: Context,
    private val mode: Mode,
    private val widgetLabel: String,
    private val onBoundButton: (Int) -> Unit,
    private val onAxisChosen: (AxisGroup) -> Unit,
    private val onCancel: () -> Unit
) {
    enum class Mode { BUTTON, JOYSTICK }

    companion object {
        /** Mở panel chờ bắt 1 nút vật lý để gán vào [widgetLabel]. */
        fun forButton(
            context: Context,
            widgetLabel: String,
            onBound: (keyCode: Int) -> Unit,
            onCancel: () -> Unit
        ) = FloatingBindOverlay(context, Mode.BUTTON, widgetLabel, onBound, {}, onCancel)

        /** Mở panel chọn Cần trái/Cần phải cho [widgetLabel]. */
        fun forJoystick(
            context: Context,
            widgetLabel: String,
            onChosen: (AxisGroup) -> Unit,
            onCancel: () -> Unit
        ) = FloatingBindOverlay(context, Mode.JOYSTICK, widgetLabel, {}, onChosen, onCancel)
    }

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var added = false

    private lateinit var statusText: TextView
    private val panel = buildPanel()

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // Không cần focus: chỉ có Button (touch), việc bắt KeyEvent tay cầm vẫn do
        // KeyCaptureWindow (cửa sổ 1x1 riêng, xem file 02/03) đảm nhiệm song song.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = (140 * context.resources.displayMetrics.density).toInt()
    }

    private fun buildPanel(): LinearLayout {
        val dp = context.resources.displayMetrics.density
        val pad = (14 * dp).toInt()
        val w = (280 * dp).toInt()

        val p = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16 * dp
                setColor(0xEE222222.toInt())
                setStroke(dp.toInt(), 0x664488FF.toInt())
            }
            layoutParams = ViewGroup.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val title = TextView(context).apply {
            text = context.getString(
                if (mode == Mode.BUTTON) R.string.bind_overlay_title_button
                else R.string.bind_overlay_title_joystick,
                widgetLabel
            )
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
        }
        p.addView(title)

        when (mode) {
            Mode.BUTTON -> {
                statusText = TextView(context).apply {
                    text = context.getString(R.string.bind_overlay_waiting)
                    setTextColor(0xFFAADDFF.toInt())
                    textSize = 15f
                    val m = (8 * dp).toInt()
                    setPadding(0, m, 0, m)
                }
                p.addView(statusText)
            }
            Mode.JOYSTICK -> {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    val m = (8 * dp).toInt()
                    setPadding(0, m, 0, m)
                }
                row.addView(Button(context).apply {
                    text = context.getString(R.string.bind_overlay_left_stick)
                    setOnClickListener { onAxisChosen(AxisGroup.LEFT_STICK); dismiss() }
                })
                row.addView(Button(context).apply {
                    text = context.getString(R.string.bind_overlay_right_stick)
                    setOnClickListener { onAxisChosen(AxisGroup.RIGHT_STICK); dismiss() }
                })
                p.addView(row)
            }
        }

        p.addView(Button(context).apply {
            text = context.getString(R.string.bind_overlay_cancel)
            setOnClickListener { onCancel(); dismiss() }
        })

        return p
    }

    /**
     * Gọi từ OverlayService khi KeyCaptureWindow bắt được 1 lần nhấn nút tay cầm trong lúc
     * panel này đang ở [Mode.BUTTON]. Hiện tên nút vừa bắt rồi tự gán + đóng sau 1 nhịp ngắn để
     * người dùng kịp thấy phản hồi (giống UX Mantis khi ghép nối thành công).
     */
    fun onKeyCaptured(keyCode: Int) {
        if (mode != Mode.BUTTON || !::statusText.isInitialized) return
        mainHandler.post {
            statusText.text = context.getString(
                R.string.bind_overlay_captured,
                KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
            )
        }
        mainHandler.postDelayed({
            onBoundButton(keyCode)
            dismiss()
        }, 400)
    }

    fun show() {
        mainHandler.post {
            if (added) return@post
            (panel.parent as? ViewGroup)?.removeView(panel)
            runCatching { wm.addView(panel, params) }.onSuccess { added = true }
        }
    }

    fun dismiss() {
        mainHandler.post {
            if (!added) return@post
            runCatching { wm.removeView(panel) }
            added = false
        }
    }
}

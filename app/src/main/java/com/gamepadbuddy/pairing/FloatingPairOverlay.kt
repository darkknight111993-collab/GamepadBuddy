package com.gamepadbuddy.pairing

import com.gamepadbuddy.R
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Bong bóng (bubble) trôi nổi để nhập mã 6 số (WindowManager TYPE_APPLICATION_OVERLAY),
 * thay thế NotificationListenerService (bị HyperOS chặn) và ô nhập tĩnh cũ.
 *
 * - Lúc đầu chỉ là một bong bóng tròn nhỏ, có thể kéo đi khắp màn hình.
 * - Chạm vào bong bóng -> mở rộng thành panel nhập mã (nhập cho nhanh).
 * - Trong panel có nút "Thu nhỏ" để gập lại thành bong bóng.
 *
 * Yêu cầu: [android.provider.Settings.canDrawOverlays] == true.
 *
 * QUAN TRỌNG: WindowManager.addView/removeView/updateViewLayout BẮT BUỘC chạy trên main thread.
 * Mọi thao tác view ở đây đều được đẩy qua [mainHandler] để tránh crash
 * "CalledFromWrongThreadException" khi service gọi từ coroutine IO.
 */
class FloatingPairOverlay(
    private val context: Context,
    private val onSubmit: (String) -> Unit,
    private val onDismiss: () -> Unit
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val root = FrameLayout(context)
    private val bubble = buildBubble()
    private val panel = buildPanel()
    private var added = false

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // FLAG_NOT_TOUCH_MODAL -> chạm ra ngoài vẫn tới dialog hệ thống phía dưới.
        // FLAG_NOT_FOCUSABLE -> overlay luôn nằm TRÊN CÙNG (kể cả màn hình Cài đặt),
        // không đánh cắp focus nên không bị hệ thống đẩy xuống khi mở Settings.
        // Khi mở rộng panel nhập mã sẽ tạm bỏ flag này (setFocusable) để EditText nhận focus.
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        // Vị trí ban đầu: góc trên bên phải để không che dialog Wireless debugging.
        x = (context.resources.displayMetrics.widthPixels * 0.6f).toInt()
        y = (96 * context.resources.displayMetrics.density).toInt()
    }

    init {
        root.addView(bubble)
        root.addView(panel)
        panel.visibility = View.GONE
    }

    /* ----------------------------- Bubble ----------------------------- */

    private fun buildBubble(): View {
        val dp = context.resources.displayMetrics.density
        val size = (56 * dp).toInt()
        val v = TextView(context).apply {
            text = "🔗"
            textSize = 22f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF1565C0.toInt())
                setStroke((2 * dp).toInt(), 0xFFFFFFFF.toInt())
            }
        }
        v.layoutParams = FrameLayout.LayoutParams(size, size).apply {
            setMargins(8, 8, 8, 8)
        }
        v.setOnTouchListener(DragTouchListener())
        return v
    }

    /** Xử lý kéo bong bóng; nếu chỉ chạm (không kéo) thì mở rộng panel. */
    private inner class DragTouchListener : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt()
                    val dy = (e.rawY - downY).toInt()
                    if (!dragging && (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8)) {
                        dragging = true
                    }
                    if (dragging) {
                        params.x = startX + dx
                        params.y = startY + dy
                        mainHandler.post { runCatching { wm.updateViewLayout(root, params) } }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    // Không kéo (chỉ chạm) -> mở rộng panel nhập mã.
                    if (!dragging) {
                        expand()
                        v.performClick()
                    }
                    return true
                }
            }
            return true
        }
    }

    /* ----------------------------- Panel ----------------------------- */

    private fun buildPanel(): LinearLayout {
        val dp = context.resources.displayMetrics.density
        val pad = (12 * dp).toInt()
        val w = (260 * dp).toInt()
        val p = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16 * dp
                setColor(0xEE222222.toInt())
                setStroke(dp.toInt(), 0x664488FF.toInt())
            }
            layoutParams = FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val title = TextView(context).apply {
            text = context.getString(R.string.pair_overlay_title)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
        }

        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            maxLines = 1
            filters = arrayOf(InputFilter.LengthFilter(6))
            hint = context.getString(R.string.pair_overlay_hint)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0x99FFFFFF.toInt())
        }
        // Gán tag để lấy text khi bấm Ghép nối.
        input.tag = "code_input"

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val pairBtn = Button(context).apply {
            text = context.getString(R.string.pair_overlay_submit)
            setOnClickListener {
                val c = input.text.toString().trim()
                if (c.length == 6) onSubmit(c)
            }
        }
        val collapseBtn = Button(context).apply {
            text = context.getString(R.string.pair_overlay_collapse)
            setOnClickListener { collapse() }
        }
        val closeBtn = Button(context).apply {
            text = context.getString(R.string.pair_overlay_close)
            setOnClickListener { dismiss() }
        }
        row.addView(pairBtn)
        row.addView(collapseBtn)
        row.addView(closeBtn)

        p.addView(title)
        p.addView(input)
        p.addView(row)
        return p
    }

    private fun expand() {
        bubble.visibility = View.GONE
        panel.visibility = View.VISIBLE
        // Cho phép window nhận focus để EditText gõ được, rồi hiện bàn phím.
        setFocusable(true)
        val input = panel.findViewWithTag<EditText>("code_input")
        input?.requestFocus()
        mainHandler.post {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun collapse() {
        panel.visibility = View.GONE
        bubble.visibility = View.VISIBLE
        // Trả về trạng thái không focus để bubble tiếp tục nổi trên mọi app/Settings.
        setFocusable(false)
    }

    /* --------------------------- Lifecycle --------------------------- */

    /** Bật/tắt FLAG_NOT_FOCUSABLE (phải chạy ở main thread vì thao tác WindowManager). */
    private fun setFocusable(focusable: Boolean) {
        params.flags = if (focusable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        mainHandler.post { runCatching { if (added) wm.updateViewLayout(root, params) } }
    }

    fun show() {
        mainHandler.post {
            if (added) return@post
            runCatching { wm.addView(root, params) }.onSuccess { added = true }
        }
    }

    fun dismiss() {
        mainHandler.post {
            if (!added) { onDismiss(); return@post }
            runCatching { wm.removeView(root) }
            added = false
            onDismiss()
        }
    }
}

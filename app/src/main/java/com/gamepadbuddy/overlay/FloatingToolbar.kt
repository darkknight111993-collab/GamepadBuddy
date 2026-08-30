package com.gamepadbuddy.overlay

import android.content.Context
import android.widget.Button
import android.widget.LinearLayout

/**
 * Thanh công cụ nổi (file 03 - Bước 5): mở/đóng editor, thêm widget, đóng overlay.
 * Đặt ở top-level overlay để mở nhanh mà không cần thoát game.
 */
class FloatingToolbar(
    context: Context,
    callbacks: OverlayRootView.RootCallbacks
) : LinearLayout(context) {

    init {
        orientation = HORIZONTAL
        setBackgroundColor(0xCC000000.toInt())
        setPadding(8, 8, 8, 8)
        addBtn("Edit") { callbacks.onToggleEdit() }
        addBtn("+Btn") { callbacks.onAddButton() }
        addBtn("+Joy") { callbacks.onAddJoystick() }
        addBtn("X") { callbacks.onClose() }
    }

    private fun addBtn(text: String, onClick: () -> Unit) {
        val b = Button(context).apply {
            this.text = text
            textSize = 12f
            setOnClickListener { onClick() }
        }
        addView(b)
    }
}

package com.gamepadbuddy.overlay

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.gamepadbuddy.profile.MappedWidget

/**
 * Container gốc của overlay (file 03): widget layer (toàn màn hình) + toolbar.
 * Quản lý Edit Mode: khi bật, widget hiện viền và có thể kéo-thả (file 03 - Bước 4).
 */
class OverlayRootView(
    context: Context,
    private val callbacks: OverlayRootView.RootCallbacks
) : FrameLayout(context) {

    interface RootCallbacks {
        fun onToggleEdit()
        fun onAddButton()
        fun onAddJoystick()
        fun onClose()
    }

    var editMode: Boolean = false
        set(value) {
            field = value
            views.values.forEach { v ->
                when (v) {
                    is VirtualButtonView -> v.editMode = value
                    is VirtualJoystickView -> v.editMode = value
                }
            }
            invalidate()
        }

    private val widgetLayer = FrameLayout(context)
    private val toolbar = FloatingToolbar(context, callbacks)
    val views = mutableMapOf<String, View>()

    init {
        widgetLayer.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(widgetLayer)
        val tlp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        addView(toolbar, tlp)
    }

    fun clearWidgets() {
        widgetLayer.removeAllViews()
        views.clear()
    }

    fun addButton(w: MappedWidget.Button, onPos: (Float, Float) -> Unit): VirtualButtonView {
        val vb = VirtualButtonView(context).apply {
            editMode = this@OverlayRootView.editMode
            onPositionChanged = { x, y -> onPos(x.toFloat(), y.toFloat()) }
        }
        val lp = FrameLayout.LayoutParams(120, 120).apply {
            leftMargin = w.x.toInt(); topMargin = w.y.toInt()
        }
        widgetLayer.addView(vb, lp)
        views[w.id] = vb
        return vb
    }

    fun addJoystick(
        w: MappedWidget.Joystick,
        onPos: (Float, Float) -> Unit,
        onMove: (Float, Float) -> Unit,
        onRelease: () -> Unit
    ): VirtualJoystickView {
        val vj = VirtualJoystickView(context).apply {
            editMode = this@OverlayRootView.editMode
            onPositionChanged = { x, y -> onPos(x.toFloat(), y.toFloat()) }
            this.onMove = onMove
            this.onRelease = onRelease
        }
        val size = (w.radius * 2 + 20).toInt().coerceAtLeast(120)
        val lp = FrameLayout.LayoutParams(size, size).apply {
            leftMargin = w.x.toInt(); topMargin = w.y.toInt()
        }
        widgetLayer.addView(vj, lp)
        views[w.id] = vj
        return vj
    }

    fun findButtonView(id: String) = views[id] as? VirtualButtonView
    fun findJoystickView(id: String) = views[id] as? VirtualJoystickView
}

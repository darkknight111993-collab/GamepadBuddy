package com.gamepadbuddy.core

import androidx.lifecycle.MutableLiveData

/**
 * Bus log sự kiện tay cầm (file 02). MainActivity bắt KeyEvent/MotionEvent ở mức Activity
 * và đẩy vào đây; ControllersFragment quan sát LiveData để hiển thị (fragment không nhận
 * KeyEvent trực tiếp).
 */
object EventBus {
    val gamepadLog = MutableLiveData("")

    @Synchronized
    fun append(line: String) {
        val cur = gamepadLog.value ?: ""
        val lines = cur.split("\n").toMutableList().apply { add(line) }
        val trimmed = if (lines.size > 400) lines.takeLast(250) else lines
        gamepadLog.postValue(trimmed.joinToString("\n"))
    }
}

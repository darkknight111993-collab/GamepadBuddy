package com.gamepadbuddy.daemon

import java.net.Socket
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * Client giao tiếp với MantisBuddy daemon qua TCP Socket (127.0.0.1:13579).
 *
 * Dùng TCP thay vì LocalSocket để tránh lỗi SELinux "Permission denied" khi app
 * cố nối với socket của shell (Android 10+).
 */
class DaemonClient {

    private var socket: Socket? = null
    private var out: OutputStream? = null

    fun connect(): Boolean {
        close()
        return try {
            val s = Socket("127.0.0.1", 13579)
            s.tcpNoDelay = true // giảm độ trễ
            socket = s
            out = s.outputStream
            true
        } catch (e: Exception) {
            // e.printStackTrace() // ẩn log để tránh spam khi retry
            false
        }
    }

    fun isConnected(): Boolean = socket?.isConnected == true && !socket!!.isClosed

    fun tap(x: Int, y: Int) = send("TAP $x $y")
    fun dragStart(id: Int, x: Int, y: Int) = send("DOWN $id $x $y")
    fun dragMove(id: Int, x: Int, y: Int) = send("MOVE $id $x $y")
    fun dragEnd(id: Int) = send("UP $id")

    private fun send(cmd: String) {
        if (!isConnected()) return
        try {
            out?.write((cmd + "\n").toByteArray(StandardCharsets.UTF_8))
            out?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun close() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        out = null
    }
}

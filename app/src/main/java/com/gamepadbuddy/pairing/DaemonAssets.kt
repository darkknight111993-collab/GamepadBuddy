package com.gamepadbuddy.pairing

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Chép binary daemon (được build bằng NDK qua scripts/build_mantisbuddy.sh) từ assets
 * ra filesDir, để Hướng A có thể push lên device qua ADB connection.
 *
 * Yêu cầu: đặt binary vào app/src/main/assets/daemon/ với tên:
 *   mantisbuddy-arm64   (arm64-v8a)
 *   mantisbuddy-armv7   (armeabi-v7a)
 * (xem scripts/build_mantisbuddy.sh)
 */
object DaemonAssets {
    /** Map ABI → tên file binary đã build trong assets/daemon/. */
    private val BINARY_NAME = mapOf(
        "arm64-v8a" to "mantisbuddy-arm64",
        "armeabi-v7a" to "mantisbuddy-armv7",
        "x86_64" to "mantisbuddy-x86_64",
        "x86" to "mantisbuddy-x86"
    )

    fun copyBinary(context: Context): File? = runCatching {
        val abi = Build.SUPPORTED_ABIS.firstOrNull { it in BINARY_NAME }
            ?: throw IllegalStateException("ABI không hỗ trợ: ${Build.SUPPORTED_ABIS.joinToString()}")
        val name = "daemon/${BINARY_NAME[abi]}"
        val out = File(context.filesDir, "mantisbuddy")
        context.assets.open(name).use { input -> out.outputStream().use { input.copyTo(it) } }
        out
    }.getOrNull()
}

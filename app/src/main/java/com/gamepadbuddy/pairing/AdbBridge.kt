package com.gamepadbuddy.pairing

import com.flyfishxu.kadb.Kadb
import java.io.File

/**
 * Hướng A (file 04): tự động hoá pairing/connect ADB không dây ngay trong app,
 * dùng thư viện Kadb (Kotlin Multiplatform, minSdk 23, hỗ trợ pair() native trên Android 11+).
 */
object AdbBridge {

    /** Pair không dây bằng mã 6 số (Android 11+). Trả về true nếu thành công. */
    suspend fun pair(host: String, port: Int, code: String): Boolean =
        runCatching { Kadb.pair(host, port, code) }.isSuccess

    /** Connect tới cổng connect chính thức (khác cổng pairing). Trả về connection hoặc null. */
    suspend fun connect(host: String, port: Int): Kadb? =
        runCatching { Kadb.create(host, port) }.getOrNull()

    /** Đẩy binary daemon (file 05) và khởi động nó với quyền shell. */
    suspend fun deployDaemon(kadb: Kadb, localBinary: File) {
        runCatching {
            kadb.push(localBinary, "/data/local/tmp/mantisbuddy")
            kadb.shell("chmod 755 /data/local/tmp/mantisbuddy")
            // setsid + redirect để daemon sống sót sau khi session adb shell kết thúc
            // (tránh bị kill ngay khi connect xong).
            kadb.shell("setsid /data/local/tmp/mantisbuddy >/dev/null 2>&1 &")
        }
    }
}

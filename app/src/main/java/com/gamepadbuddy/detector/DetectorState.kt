package com.gamepadbuddy.detector

/**
 * Trạng thái chia sẻ giữa OverlayService và MainActivity (cùng process).
 * - ACTION_RELOAD: broadcast bảo service tải lại profile (sau khi tạo/sửa).
 * - lastGamePackage: gói game cuối cùng được phát hiện, dùng cho nút "TẠO PROFILE" ở MainActivity.
 */
object DetectorState {
    const val ACTION_RELOAD = "com.gamepadbuddy.RELOAD_PROFILES"
    const val ACTION_EDIT_PROFILE = "com.gamepadbuddy.ACTION_EDIT_PROFILE"
    var lastGamePackage: String? = null
}

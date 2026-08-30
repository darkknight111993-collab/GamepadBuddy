package com.gamepadbuddy.pairing

import android.content.Context

/**
 * Lưu cờ đã ghép nối thành công (file 01b bước "pairing"). Dùng để bước onboarding "pairing"
 * biết đã xong, và PairingCoordinator chỉ tự-connect lại khi đã có khoá.
 */
object PairingStore {
    private const val PREFS = "gpb_pairing"
    private const val KEY_PAIRED = "paired"

    fun hasValidKey(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PAIRED, false)

    fun markPaired(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_PAIRED, true).apply()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_PAIRED).apply()
    }
}

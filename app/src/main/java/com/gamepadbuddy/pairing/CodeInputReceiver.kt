package com.gamepadbuddy.pairing

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput

/**
 * Nhận mã 6 số người dùng gõ trực tiếp vào RemoteInput (textbox ngay trong khay thông báo),
 * rồi chuyển vào PairingService để ghép nối. Không cần mở app/activity nào.
 *
 * Lưu ý: RemoteInput chỉ bị cấm trên notification của FOREGROUND service. Notification "Nhập mã"
 * (PROMPT_NOTIF_ID) là notification THƯỜNG nên RemoteInput hoạt động bình thường trên mọi version.
 */
class CodeInputReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val results = RemoteInput.getResultsFromIntent(intent)
        val code = results?.getCharSequence(KEY_CODE)?.toString()?.trim().orEmpty()
        if (code.length == 6) {
            PairingService.submitCode(context, code)
        }
    }

    companion object {
        const val ACTION = "com.gamepadbuddy.pairing.CODE_INPUT"
        const val KEY_CODE = "key_code"

        /** PendingIntent cho action "Nhập mã" trên notification (dùng để hệ thống gắn RemoteInput). */
        fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, CodeInputReceiver::class.java).setAction(ACTION)
            // FLAG_MUTABLE (0x02000000) bắt buộc để hệ thống điền kết quả RemoteInput.
            // Dùng giá trị thô để tránh lỗi resolve constant trên một số toolchain.
            return PendingIntent.getBroadcast(
                context, 2, intent,
                PendingIntent.FLAG_MUTABLE
            )
        }

        /** Tạo RemoteInput đi kèm action nhập mã. */
        fun remoteInput(): RemoteInput =
            RemoteInput.Builder(KEY_CODE)
                .setLabel("Mã 6 số ghép nối")
                .setAllowFreeFormInput(true)
                .build()
    }
}

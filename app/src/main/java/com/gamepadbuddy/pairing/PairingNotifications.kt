package com.gamepadbuddy.pairing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Notification lối tắt của app (file 04b - Bước 5): khi daemon mất kết nối, post notification
 * "Chưa kết nối" → bấm mở PairingActivity. Không đọc data gì, chỉ điều hướng.
 */
object PairingNotifications {
    const val CHANNEL_ID = "pairing_channel"
    const val NOTIF_ID = 2002

    fun postShortcut(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Pairing", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(
            context, 0, Intent(context, PairingActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("GamepadBuddy: chưa kết nối daemon")
            .setContentText("Bấm để mở Wireless debugging và ghép nối lại")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
    }

    fun cancelShortcut(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
    }
}

package com.gamepadbuddy.detector

import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Phát hiện app foreground qua UsageStatsManager (file 07 - Cách 1, ít nhạy cảm khi lên Play).
 * Cần quyền PACKAGE_USAGE_STATS (xin thủ công trong Settings → Usage Access).
 */
object AppDetector {
    fun getForegroundPackage(context: Context): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val end = System.currentTimeMillis()
        val begin = end - 10_000
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, end)
        return stats?.maxByOrNull { it.lastTimeUsed }?.packageName
    }
}

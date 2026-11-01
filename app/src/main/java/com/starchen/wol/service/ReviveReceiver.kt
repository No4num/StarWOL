package com.starchen.wol.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.starchen.wol.util.LogStore

/**
 * 任务被划掉后的复活闹钟。
 *
 * 相比在 onTaskRemoved 里 delay 一下再 start，闹钟的 PendingIntent 由系统持有，
 * 进程被杀了也照样触发，是唯一能在"划掉就被杀"的 ROM 上把服务拉回来的办法。
 */
class ReviveReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        LogStore.info(context, "SERVICE", "复活闹钟触发，重新拉起保活服务")
        AutoWakeService.start(context)
    }

    companion object {
        private const val REQUEST_CODE = 9001

        fun schedule(context: Context, delayMs: Long = 2000L) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val pi = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, ReviveReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            runCatching {
                am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, pi)
            }.onFailure {
                LogStore.warn(context, "SERVICE", "复活闹钟设置失败：${it.message}")
            }
        }

        fun cancel(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val pi = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, ReviveReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                runCatching { am.cancel(pi) }
            }
        }
    }
}

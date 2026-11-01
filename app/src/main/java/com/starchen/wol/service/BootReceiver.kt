package com.starchen.wol.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 开机 / 应用更新 / 用户解锁后，把保活服务和定时任务重新挂上。
 * 解锁事件（USER_PRESENT）是最便宜的复活时机：用户一动手机就检查一次。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action.orEmpty()
        Log.i("StarWol.Boot", "收到 $action")
        val pending = goAsync()
        Thread {
            runCatching {
                if (action == Intent.ACTION_USER_PRESENT) {
                    // 解锁：只轻量检查一次服务，不动闹钟
                    AutoWakeService.start(context)
                } else {
                    Scheduler.scheduleAll(context)
                    AutoWakeService.start(context)
                }
            }.onFailure { Log.e("StarWol.Boot", "恢复失败: ${it.message}") }
            pending.finish()
        }.start()
    }
}

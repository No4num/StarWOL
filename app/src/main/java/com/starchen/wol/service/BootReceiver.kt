package com.starchen.wol.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** 开机 / 应用更新后把手头的定时任务重新排一遍 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("StarWol.Boot", "收到 ${intent.action}")
        val pending = goAsync()
        Thread {
            runCatching {
                Scheduler.scheduleAll(context)
                AutoWakeService.start(context)
            }.onFailure { Log.e("StarWol.Boot", "恢复失败: ${it.message}") }
            pending.finish()
        }.start()
    }
}

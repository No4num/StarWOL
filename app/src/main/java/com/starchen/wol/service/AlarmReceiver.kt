package com.starchen.wol.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.starchen.wol.data.WolStore
import com.starchen.wol.util.LogStore
import com.starchen.wol.wol.WolSender
import kotlinx.coroutines.runBlocking

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Scheduler.ACTION_AUTO_WAKE) return
        val deviceId = intent.getStringExtra(Scheduler.EXTRA_DEVICE_ID) ?: return
        val pending = goAsync()

        Thread {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "StarWol:autoWake"
            )
            try {
                wakeLock.acquire(30_000L)
                val device = WolStore.devicesBlocking(context).firstOrNull { it.id == deviceId }
                if (device == null) {
                    LogStore.warn(context, "ALARM", "定时任务触发了但设备已被删除：$deviceId")
                } else {
                    LogStore.info(context, "ALARM", "定时唤醒 ${device.name} → ${device.endpoint}")
                    runBlocking {
                        val report = WolSender.send(
                            ip = device.ip,
                            port = device.port,
                            mac = device.mac,
                            alsoBroadcast = device.autoBroadcast
                        )
                        report.steps.forEach { step ->
                            if (step.ok) {
                                LogStore.info(context, "ALARM", "${step.label} 成功 ${step.detail}")
                            } else {
                                LogStore.error(context, "ALARM", "${step.label} 失败 ${step.detail}")
                            }
                        }
                    }
                    // 重排下一天
                    Scheduler.schedule(context, device)
                }
            } catch (e: Exception) {
                LogStore.error(context, "ALARM", "定时唤醒异常：${e.message}")
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                pending.finish()
            }
        }.start()
    }
}

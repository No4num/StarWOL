package com.starchen.wol.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.starchen.wol.data.WolStore
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
                    Log.w("StarWol.Alarm", "设备已删除: $deviceId")
                } else {
                    runBlocking {
                        val (report, socket) = WolSender.send(
                            ip = device.ip,
                            port = device.port,
                            mac = device.mac,
                            alsoBroadcast = device.autoBroadcast
                        )
                        socket?.close()
                        Log.i("StarWol.Alarm", "定时唤醒 ${device.name}: ${report.steps.joinToString()}")
                    }
                    // 重排下一天
                    Scheduler.schedule(context, device)
                }
            } catch (e: Exception) {
                Log.e("StarWol.Alarm", "定时唤醒失败: ${e.message}", e)
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                pending.finish()
            }
        }.start()
    }
}

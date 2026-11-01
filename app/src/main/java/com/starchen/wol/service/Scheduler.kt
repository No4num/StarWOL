package com.starchen.wol.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.starchen.wol.data.Device
import com.starchen.wol.data.WolStore
import java.util.Calendar

/**
 * 定时唤醒的调度器。
 * 每个需要定时的设备注册一条精确闹钟；触发后由 AlarmReceiver 重发并重新排下一天。
 */
object Scheduler {

    const val ACTION_AUTO_WAKE = "com.starchen.wol.ACTION_AUTO_WAKE"
    const val EXTRA_DEVICE_ID = "device_id"
    private const val TAG = "StarWol.Scheduler"

    private fun pendingIntent(context: Context, device: Device): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_AUTO_WAKE
            putExtra(EXTRA_DEVICE_ID, device.id)
            // 用 data 区分不同设备的 PendingIntent，避免被覆盖
            data = android.net.Uri.parse("starwol://device/${device.id}")
        }
        return PendingIntent.getBroadcast(
            context,
            device.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun scheduleAll(context: Context) {
        val devices = WolStore.devicesBlocking(context)
        devices.filter { it.autoWakeEnabled }.forEach { schedule(context, it) }
    }

    fun schedule(context: Context, device: Device) {
        if (!device.autoWakeEnabled) {
            cancel(context, device.id)
            return
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextTrigger(device)
        val pi = pendingIntent(context, device)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                Log.w(TAG, "无精确闹钟权限，退化为 setAndAllowWhileIdle")
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
            Log.i(TAG, "已排期 ${device.name} → ${java.util.Date(triggerAt)}")
        } catch (e: Exception) {
            Log.e(TAG, "排期失败: ${e.message}")
        }
    }

    fun cancel(context: Context, deviceId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_AUTO_WAKE
            data = android.net.Uri.parse("starwol://device/$deviceId")
        }
        val pi = PendingIntent.getBroadcast(
            context,
            deviceId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }

    /** 下一次触发时间（今天该时刻没过就是今天，过了就明天） */
    fun nextTrigger(device: Device, now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, device.autoWakeHour)
            set(Calendar.MINUTE, device.autoWakeMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }
}

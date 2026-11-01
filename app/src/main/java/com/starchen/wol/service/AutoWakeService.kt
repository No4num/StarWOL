package com.starchen.wol.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.starchen.wol.MainActivity
import com.starchen.wol.R
import com.starchen.wol.util.LogStore

/**
 * 常驻前台服务：定时唤醒要活着，就得有个东西一直醒着。
 * 被划掉或被回收时通过复活闹钟拉回来。
 */
class AutoWakeService : Service() {

    /** 服务实例第一次收到 START 才记日志，重复 start（无障碍 / 任务调度）不刷屏 */
    private var startLogged = false

    /** 用户主动停止时不再安排复活 */
    private var userStopped = false

    companion object {
        private const val TAG = "StarWol.Service"
        const val CHANNEL_ID = "starwol_keepalive"
        const val NOTIF_ID = 1001
        const val ACTION_START = "com.starchen.wol.START_KEEPALIVE"
        const val ACTION_STOP = "com.starchen.wol.STOP_KEEPALIVE"

        fun start(context: Context) {
            val intent = Intent(context, AutoWakeService::class.java).setAction(ACTION_START)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { Log.e(TAG, "启动保活服务失败: ${it.message}") }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AutoWakeService::class.java).setAction(ACTION_STOP))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            userStopped = true
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildNotification())
        Scheduler.scheduleAll(this)
        if (!startLogged) {
            startLogged = true
            LogStore.info(this, "SERVICE", "保活服务启动")
        }
        return START_STICKY
    }
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.keepalive_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.keepalive_channel_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_wol)
            .setContentTitle(getString(R.string.keepalive_title))
            .setContentText(getString(R.string.keepalive_text))
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        LogStore.info(this, "SERVICE", "任务被划掉，安排复活")
        ReviveReceiver.cancel(this)
        ReviveReceiver.schedule(this, 2_000L)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // 服务被系统回收时也挂一个复活闹钟；START_STICKY 有概率不生效，闹钟更硬
        if (!userStopped) {
            LogStore.info(this, "SERVICE", "服务被销毁，安排 3 秒后复活")
            ReviveReceiver.cancel(this)
            ReviveReceiver.schedule(this, 3_000L)
        }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}

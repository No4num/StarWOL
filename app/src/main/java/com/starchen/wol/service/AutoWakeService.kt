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
import com.starchen.wol.data.WorkMode
import com.starchen.wol.data.WolStore
import com.starchen.wol.util.LogStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 常驻前台服务：定时唤醒要活着，就得有个东西一直醒着。
 * 正常模式靠系统前台服务 + 白名单，root 模式额外往系统白名单里钉一遍。
 */
class AutoWakeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** root 保活只在结果发生变化时记一笔，避免服务被反复拉起时刷屏 */
    private var rootAppliedOkOnce = false

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
        scope.launch { applyRootIfNeeded() }
        return START_STICKY
    }

    private suspend fun applyRootIfNeeded() {
        val settings = WolStore.settings(this).first()
        if (settings.workMode == WorkMode.ROOT && KeepAliveManager.isRootAvailable()) {
            val result = KeepAliveManager.applyRootKeepAlive(this)
            val okCount = result.steps.count { it.ok }
            if (result.allOk && rootAppliedOkOnce) return
            rootAppliedOkOnce = result.allOk
            LogStore.info(
                this, "ROOT",
                "启动时自动应用保活：$okCount/${result.steps.size} 成功"
            )
            result.steps.filterNot { it.ok }.forEach {
                LogStore.warn(this, "ROOT", "${it.command.label} 失败：${it.output}")
            }
        }
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
        // 从最近任务里划掉：先取消复活闹钟避免重复，再挂一个新的。
        // 用系统闹钟而不是 delay，是因为进程可能在 delay 期间就被 ROM 杀了。
        LogStore.info(this, "SERVICE", "任务被划掉，安排 2 秒后复活")
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
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

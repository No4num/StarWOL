package com.starchen.wol.service

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.starchen.wol.util.LogStore

/**
 * 无障碍服务，两个用途，都不读取屏幕内容：
 *
 * 1. 带无障碍权限的进程优先级更高，不容易被系统回收。
 * 2. 监听窗口切换事件（只有包名，不开 canRetrieveWindowContent）。
 *    用户划掉应用回到桌面时，这里的进程通常还活着，顺手确认前台服务在不在，
 *    不在就拉起来 —— 这是"划掉后台还能活"最有用的一环。
 */
class KeepAliveAccessibilityService : AccessibilityService() {

    private var lastCheckAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        LogStore.info(this, "A11Y", "无障碍已连接，拉起保活服务")
        runCatching { AutoWakeService.start(this) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString().orEmpty()
        if (pkg.isEmpty() || pkg == packageName) return

        // 窗口切换很频繁，8 秒内最多检查一次
        val now = SystemClock.elapsedRealtime()
        if (now - lastCheckAt < 8_000L) return
        lastCheckAt = now

        if (!isKeepAliveServiceRunning()) {
            LogStore.info(this, "A11Y", "检测到保活服务不在，重新拉起")
            runCatching { AutoWakeService.start(this) }
        }
    }

    private fun isKeepAliveServiceRunning(): Boolean = runCatching {
        val am = getSystemService(ActivityManager::class.java) ?: return@runCatching true
        val target = AutoWakeService::class.java.name
        am.getRunningServices(64).any { it.service.className == target }
    }.getOrDefault(true)

    override fun onInterrupt() = Unit
}

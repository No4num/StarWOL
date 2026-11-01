package com.starchen.wol.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * 无障碍服务，纯粹当保活兜底用：
 * 带无障碍权限的进程优先级更高，不容易被系统回收。
 * 连接后顺手把前台服务重新拉起来。不做任何读取屏幕内容的操作。
 */
class KeepAliveAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i("StarWol.A11y", "无障碍已连接，拉起保活服务")
        runCatching {
            AutoWakeService.start(this)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不监听内容
    }

    override fun onInterrupt() = Unit
}

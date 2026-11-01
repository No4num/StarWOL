package com.starchen.wol.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import java.io.DataOutputStream

/**
 * 保活相关能力检测 + 请求入口。
 * 每个权限/能力对应设置页里的一行。
 */
enum class KeepAliveItem {
    NOTIFICATION,       // 通知权限（前台服务通知必需）
    BATTERY,            // 忽略电池优化
    EXACT_ALARM,        // 精确闹钟
    AUTO_START,         // 自启动（各家 ROM 只能引导）
    BACKGROUND,         // 后台限制
    ACCESSIBILITY       // 无障碍（兜底拉起）
}

data class KeepAliveState(val item: KeepAliveItem, val granted: Boolean)

object KeepAliveManager {

    fun title(item: KeepAliveItem): String = when (item) {
        KeepAliveItem.NOTIFICATION -> "通知权限"
        KeepAliveItem.BATTERY -> "忽略电池优化"
        KeepAliveItem.EXACT_ALARM -> "精确闹钟"
        KeepAliveItem.AUTO_START -> "开机自启动"
        KeepAliveItem.BACKGROUND -> "后台运行限制"
        KeepAliveItem.ACCESSIBILITY -> "无障碍服务"
    }

    fun description(item: KeepAliveItem): String = when (item) {
        KeepAliveItem.NOTIFICATION -> "前台服务需要常驻通知，否则定时唤醒会被系统掐掉"
        KeepAliveItem.BATTERY -> "允许应用在电池优化中豁免，避免息屏后被冻结"
        KeepAliveItem.EXACT_ALARM -> "定时唤醒到点要准，非精确闹钟可能延迟几分钟"
        KeepAliveItem.AUTO_START -> "重启后自动恢复定时任务，需要到系统设置里手动允许"
        KeepAliveItem.BACKGROUND -> "部分 ROM 会限制后台活动，需要手动放行"
        KeepAliveItem.ACCESSIBILITY -> "作为兜底：被系统回收后自动把应用重新拉起来"
    }

    fun isGranted(context: Context, item: KeepAliveItem): Boolean = when (item) {
        KeepAliveItem.NOTIFICATION ->
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        KeepAliveItem.BATTERY -> {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        }
        KeepAliveItem.EXACT_ALARM -> {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
        }
        KeepAliveItem.AUTO_START -> isAutoStartAllowed(context)
        KeepAliveItem.BACKGROUND -> !isBackgroundRestricted(context)
        KeepAliveItem.ACCESSIBILITY -> isAccessibilityServiceEnabled(context)
    }

    fun allStates(context: Context): List<KeepAliveState> =
        KeepAliveItem.entries.map { KeepAliveState(it, isGranted(context, it)) }

    /** 普通权限 / 需要跳系统设置的能力 */
    fun isRuntimePermission(item: KeepAliveItem): Boolean =
        item == KeepAliveItem.NOTIFICATION

    private fun isAutoStartAllowed(context: Context): Boolean {
        // 系统层面没有统一 API。国产 ROM 各自有自启动管理器：
        // 有管理器的机型默认视为需要用户手动确认，没有的就当不受限。
        val managers = listOf(
            "com.miui.securitycenter",
            "com.huawei.systemmanager",
            "com.coloros.safecenter",
            "com.oppo.safe",
            "com.vivo.permissionmanager",
            "com.iqoo.secure"
        )
        val hasManager = managers.any { pkg ->
            runCatching { context.packageManager.getApplicationInfo(pkg, 0) }.isSuccess
        }
        return !hasManager
    }

    private fun isBackgroundRestricted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.isBackgroundRestricted
        } else false
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val me = "${context.packageName}/${KeepAliveAccessibilityService::class.java.name}"
        return enabled.any { it.id == me } || enabled.any { it.resolveInfo.serviceInfo.packageName == context.packageName }
    }

    // ---------- 跳转 ----------

    fun openSettingsFor(context: Context, item: KeepAliveItem) {
        val intent = when (item) {
            KeepAliveItem.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}"))
            KeepAliveItem.EXACT_ALARM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:${context.packageName}"))
            else Intent(Settings.ACTION_SETTINGS)
            KeepAliveItem.AUTO_START -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
            KeepAliveItem.BACKGROUND -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
            KeepAliveItem.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            KeepAliveItem.NOTIFICATION -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        runCatching {
            if (item != KeepAliveItem.ACCESSIBILITY) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun openAppDetail(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openAutoStartManager(context: Context) {
        val intents = listOf(
            Intent().setComponent(
                android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            ),
            Intent().setComponent(
                android.content.ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            ),
            Intent().setComponent(
                android.content.ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            )
        )
        for (i in intents) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(i) }.isSuccess) return
        }
        openAppDetail(context)
    }

    // ---------- root ----------

    fun isRootAvailable(): Boolean = runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val out = process.inputStream.bufferedReader().readText()
        process.waitFor()
        out.contains("uid=0")
    }.getOrDefault(false)

    data class RootKeepAliveResult(val ok: Boolean, val log: List<String>)

    /** root 下把应用钉进各种白名单，配合前台服务尽力保活 */
    fun applyRootKeepAlive(context: Context): RootKeepAliveResult {
        val pkg = context.packageName
        val commands = listOf(
            "cmd deviceidle whitelist +$pkg",
            "dumpsys deviceidle whitelist | grep $pkg",
            "am set-standby-bucket $pkg active",
            "cmd appops set $pkg RUN_IN_BACKGROUND allow",
            "cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow",
            "settings put global package_verifier_enable 0"
        )
        val log = mutableListOf<String>()
        var ok = true
        for (cmd in commands) {
            val result = runRoot(cmd)
            log += "» $cmd\n${result.trim().ifBlank { "（无输出）" }}"
        }
        return RootKeepAliveResult(ok, log)
    }

    fun runRoot(command: String): String {
        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("su"))
            process.outputStream.bufferedWriter().use { it.write(command + "\nexit\n") }
            val text = process.inputStream.bufferedReader().readText()
            val err = process.errorStream.bufferedReader().readText()
            process.waitFor()
            text + err
        }.getOrDefault("${'$'}?=执行失败")
    }
}

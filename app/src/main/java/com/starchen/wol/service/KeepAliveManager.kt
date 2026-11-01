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
import com.starchen.wol.util.LogStore
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

data class KeepAliveState(val item: KeepAliveItem, val granted: Boolean?)

object KeepAliveManager {

    fun title(item: KeepAliveItem): String = when (item) {
        KeepAliveItem.NOTIFICATION -> "通知权限"
        KeepAliveItem.BATTERY -> "忽略电池优化"
        KeepAliveItem.EXACT_ALARM -> "精确闹钟"
        KeepAliveItem.AUTO_START -> "自启动"
        KeepAliveItem.BACKGROUND -> "耗电管理 · 完全允许"
        KeepAliveItem.ACCESSIBILITY -> "无障碍服务"
    }

    fun description(item: KeepAliveItem): String = when (item) {
        KeepAliveItem.NOTIFICATION -> "前台服务需要常驻通知，否则定时唤醒会被系统掐掉"
        KeepAliveItem.BATTERY -> "允许应用在电池优化中豁免，避免息屏后被冻结"
        KeepAliveItem.EXACT_ALARM -> "定时唤醒到点要准，非精确闹钟可能延迟几分钟"
        KeepAliveItem.AUTO_START -> "允许应用自启动。系统没有查询接口，读不到真实状态，点“去确认”跳到厂商的自启动管理页"
        KeepAliveItem.BACKGROUND -> "在耗电管理里选“完全允许”，否则息屏后可能被一刀切。系统读不到真实状态，点“去确认”直达"
        KeepAliveItem.ACCESSIBILITY -> "作为兜底：被系统回收后自动把应用重新拉起来"
    }

    /**
     * 真实状态三态：
     *   true  = 明确已获得
     *   false = 明确未获得
     *   null  = 系统没有查询接口，读不到（自启动、厂商耗电管理）
     */
    fun isGranted(context: Context, item: KeepAliveItem): Boolean? = runCatching {
        when (item) {
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
            // 系统没有对外暴露查询接口，只能如实显示“未知”
            KeepAliveItem.AUTO_START -> null
            // 标准 API 只能看到“后台受限”，看不到厂商的省电策略；受限时能确定，否则未知
            KeepAliveItem.BACKGROUND -> if (isBackgroundRestricted(context)) false else null
            KeepAliveItem.ACCESSIBILITY -> isAccessibilityServiceEnabled(context)
        }
    }.getOrNull()

    fun allStates(context: Context): List<KeepAliveState> =
        KeepAliveItem.entries.map { KeepAliveState(it, isGranted(context, it)) }

    /** 普通权限 / 需要跳系统设置的能力 */
    fun isRuntimePermission(item: KeepAliveItem): Boolean =
        item == KeepAliveItem.NOTIFICATION

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
        // 自启动、耗电管理没有统一 API，只能一家一家试
        if (item == KeepAliveItem.AUTO_START) {
            openAutoStartManager(context)
            return
        }
        if (item == KeepAliveItem.BACKGROUND) {
            openPowerManager(context)
            return
        }
        val intent = when (item) {
            KeepAliveItem.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}"))
            KeepAliveItem.EXACT_ALARM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:${context.packageName}"))
            else Intent(Settings.ACTION_SETTINGS)
            KeepAliveItem.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            KeepAliveItem.NOTIFICATION -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
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
        // 各家 ROM 的自启动管理页，按顺序试，谁先打开算谁的
        val entries = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
            "com.samsung.android.lool" to "com.samsung.android.lool.activity.SmManagerActivity",
            "com.meizu.safe" to "com.meizu.safe.permission.SmartBGActivity"
        )
        for ((pkg, cls) in entries) {
            val intent = Intent().setComponent(android.content.ComponentName(pkg, cls))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) {
                LogStore.info(context, "KEEPALIVE", "已跳转自启动管理：$pkg")
                return
            }
        }
        LogStore.warn(context, "KEEPALIVE", "没找到厂商自启动管理页，退回应用详情")
        openAppDetail(context)
    }

    /**
     * 耗电管理。用户要的是「耗电管理 → 完全允许」那一项，
     * 各家入口不同，逐个试，最后退回应用详情让用户自己点电池。
     */
    fun openPowerManager(context: Context) {
        val pkg = context.packageName
        val label = runCatching {
            val ai = context.packageManager.getApplicationInfo(pkg, 0)
            context.packageManager.getApplicationLabel(ai).toString()
        }.getOrDefault("StarWOL")

        val candidates = listOf(
            // MIUI：省电策略
            Intent().setComponent(
                android.content.ComponentName(
                    "com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
            ).putExtra("package_name", pkg).putExtra("package_label", label),
            // ColorOS / realme：应用耗电管理
            Intent().setComponent(
                android.content.ComponentName(
                    "com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"
                )
            ),
            Intent().setComponent(
                android.content.ComponentName(
                    "com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerConsumptionActivity"
                )
            ),
            // vivo：后台耗电管理
            Intent().setComponent(
                android.content.ComponentName(
                    "com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"
                )
            ),
            // 通用：应用详情页（里面有电池一项）
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$pkg"))
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) {
                LogStore.info(context, "KEEPALIVE", "已跳转耗电管理：${intent.component?.packageName ?: "应用详情"}")
                return
            }
        }
        openAppDetail(context)
    }

}

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

data class KeepAliveState(val item: KeepAliveItem, val granted: Boolean)

object KeepAliveManager {

    fun title(item: KeepAliveItem): String = when (item) {
        KeepAliveItem.NOTIFICATION -> "通知权限"
        KeepAliveItem.BATTERY -> "忽略电池优化"
        KeepAliveItem.EXACT_ALARM -> "精确闹钟"
        KeepAliveItem.AUTO_START -> "开机自启动"
        KeepAliveItem.BACKGROUND -> "耗电管理 · 完全允许"
        KeepAliveItem.ACCESSIBILITY -> "无障碍服务"
    }

    fun description(item: KeepAliveItem): String = when (item) {
        KeepAliveItem.NOTIFICATION -> "前台服务需要常驻通知，否则定时唤醒会被系统掐掉"
        KeepAliveItem.BATTERY -> "允许应用在电池优化中豁免，避免息屏后被冻结"
        KeepAliveItem.EXACT_ALARM -> "定时唤醒到点要准，非精确闹钟可能延迟几分钟"
        KeepAliveItem.AUTO_START -> "重启后自动恢复定时任务。系统读不到真实状态，点“请求”会跳到厂商的自启动管理页"
        KeepAliveItem.BACKGROUND -> "在耗电管理里选“完全允许”，否则息屏后可能被一刀切。各家叫法不同，点“请求”直达"
        KeepAliveItem.ACCESSIBILITY -> "作为兜底：被系统回收后自动把应用重新拉起来"
    }

    fun isGranted(context: Context, item: KeepAliveItem): Boolean = runCatching {
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
            KeepAliveItem.AUTO_START -> !hasVendorManager(context)
            KeepAliveItem.BACKGROUND ->
                !isBackgroundRestricted(context) && !hasVendorManager(context)
            KeepAliveItem.ACCESSIBILITY -> isAccessibilityServiceEnabled(context)
        }
    }.getOrDefault(false)

    fun allStates(context: Context): List<KeepAliveState> =
        KeepAliveItem.entries.map { KeepAliveState(it, isGranted(context, it)) }

    /** 普通权限 / 需要跳系统设置的能力 */
    fun isRuntimePermission(item: KeepAliveItem): Boolean =
        item == KeepAliveItem.NOTIFICATION

    private val VENDOR_MANAGERS = listOf(
        "com.miui.securitycenter",
        "com.huawei.systemmanager",
        "com.coloros.safecenter",
        "com.oppo.safe",
        "com.vivo.permissionmanager",
        "com.iqoo.secure",
        "com.samsung.android.lool",
        "com.meizu.safe"
    )

    private fun hasVendorManager(context: Context): Boolean = VENDOR_MANAGERS.any { pkg ->
        runCatching { context.packageManager.getApplicationInfo(pkg, 0) }.isSuccess
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

    // ---------- root ----------
    //
    // 原则：只碰自己这个包，不做任何全局设置。
    // 每条命令都是写死的模板，包名只经过一次字符校验，不存在拼接执行。

    fun isRootAvailable(): Boolean = runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val out = process.inputStream.bufferedReader().readText()
        process.waitFor()
        out.contains("uid=0")
    }.getOrDefault(false)

    /** 一条 root 操作：做什么 / 实际命令 / 撤销命令 */
    data class RootCommand(
        val label: String,
        val shell: String,
        val revertShell: String? = null
    )

    data class RootStep(val command: RootCommand, val output: String, val ok: Boolean)

    data class RootBatch(val steps: List<RootStep>) {
        val allOk: Boolean get() = steps.isNotEmpty() && steps.all { it.ok }
    }

    /**
     * 保活要执行的全部命令。
     * 只动应用自己：Doze 白名单 + 两条后台 op。
     * 不碰待机桶（set-standby-bucket active）—— 那条会让系统一直把你当活跃应用，最费电。
     */
    fun keepAlivePlan(pkg: String): List<RootCommand> = listOf(
        RootCommand(
            label = "加入 Doze 白名单",
            shell = "cmd deviceidle whitelist +$pkg",
            revertShell = "cmd deviceidle whitelist -$pkg"
        ),
        RootCommand(
            label = "允许后台活动",
            shell = "cmd appops set $pkg RUN_IN_BACKGROUND allow",
            revertShell = "cmd appops set $pkg RUN_IN_BACKGROUND default"
        ),
        RootCommand(
            label = "允许任意后台活动",
            shell = "cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow",
            revertShell = "cmd appops set $pkg RUN_ANY_IN_BACKGROUND default"
        )
    )

    /** 执行后核对（只读） */
    fun verifyPlan(pkg: String): List<RootCommand> = listOf(
        RootCommand("核对 Doze 白名单", "dumpsys deviceidle whitelist"),
        RootCommand("核对后台 op", "cmd appops get $pkg RUN_IN_BACKGROUND")
    )

    /** 撤销计划 */
    fun revertPlan(pkg: String): List<RootCommand> =
        keepAlivePlan(pkg).mapNotNull { cmd ->
            cmd.revertShell?.let { RootCommand("撤销：${cmd.label}", it) }
        }

    fun applyRootKeepAlive(context: Context, onStep: ((RootStep) -> Unit)? = null): RootBatch {
        val pkg = context.packageName
        if (!isValidPackageName(pkg)) {
            return RootBatch(listOf(RootStep(RootCommand("包名校验失败", pkg), "包名含非法字符", false)))
        }
        return executePlan(keepAlivePlan(pkg), onStep)
    }

    fun revertRootKeepAlive(context: Context, onStep: ((RootStep) -> Unit)? = null): RootBatch {
        val pkg = context.packageName
        return executePlan(revertPlan(pkg), onStep)
    }

    fun verifyRootKeepAlive(context: Context): RootBatch {
        val pkg = context.packageName
        return executePlan(verifyPlan(pkg), null)
    }

    private fun executePlan(plan: List<RootCommand>, onStep: ((RootStep) -> Unit)?): RootBatch {
        val steps = mutableListOf<RootStep>()
        for (cmd in plan) {
            val output = runRoot(cmd.shell)
            val ok = !output.startsWith("__FAIL__")
            val step = RootStep(cmd, output.removePrefix("__FAIL__"), ok)
            steps += step
            onStep?.invoke(step)
        }
        return RootBatch(steps)
    }

    /** 唯一的执行入口。只接受本类生成的命令模板，外部拿不到。 */
    private fun runRoot(shell: String): String {
        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", shell))
            val out = process.inputStream.bufferedReader().readText().trim()
            val err = process.errorStream.bufferedReader().readText().trim()
            val finished = process.waitFor(12, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                "__FAIL__超时（12s），可能没给 root 授权"
            } else if (process.exitValue() != 0 && out.isEmpty()) {
                "__FAIL__$err"
            } else {
                out.ifBlank { "（无输出）" }
            }
        }.getOrDefault("__FAIL__执行异常，可能是 su 不可用")
    }

    private fun isValidPackageName(pkg: String): Boolean =
        pkg.isNotEmpty() && pkg.length <= 128 && pkg.all { it.isLetterOrDigit() || it == '.' || it == '_' }
}

package com.starchen.wol.data

import kotlinx.serialization.Serializable
import java.util.UUID

/** 一台待唤醒设备。mac 永远保存为规范化形式 AA:BB:CC:DD:EE:FF。 */
@Serializable
data class Device(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val ip: String = "",
    val port: Int = 9,
    val mac: String = "",
    /** 单播发出后，再向 255.255.255.255 广播一遍 */
    val autoBroadcast: Boolean = false,
    /** 是否启用定时自动唤醒（每天该时刻） */
    val autoWakeEnabled: Boolean = false,
    val autoWakeHour: Int = 0,
    val autoWakeMinute: Int = 0
) {
    val hasSchedule: Boolean get() = autoWakeEnabled
    val timeLabel: String get() = "%02d:%02d".format(autoWakeHour, autoWakeMinute)
    val endpoint: String get() = "$ip:$port"
}

enum class WorkMode { NORMAL, ROOT }

@Serializable
data class AppSettings(
    val workMode: WorkMode = WorkMode.NORMAL,
    /** 设置页里显示日志面板 */
    val showLogs: Boolean = true,
    /** 运行过程中把日志写进文件 */
    val writeLogs: Boolean = true
)

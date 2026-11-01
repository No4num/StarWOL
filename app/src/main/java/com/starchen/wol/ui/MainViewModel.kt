package com.starchen.wol.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.starchen.wol.data.AppSettings
import com.starchen.wol.data.Device
import com.starchen.wol.data.WolStore
import com.starchen.wol.service.AutoWakeService
import com.starchen.wol.service.Scheduler
import com.starchen.wol.util.LogStore
import com.starchen.wol.wol.ProbeResult
import com.starchen.wol.wol.Prober
import com.starchen.wol.wol.SendReport
import com.starchen.wol.wol.WolSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WakeUiState(
    val deviceName: String,
    val sending: Boolean,
    val report: SendReport? = null,
    val probe: ProbeResult? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val context get() = getApplication<Application>()

    val devices: StateFlow<List<Device>> = WolStore.devices(context)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val settings: StateFlow<AppSettings> = WolStore.settings(context)
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val logs: StateFlow<List<LogStore.Entry>> = LogStore.entries

    var wakeState = androidx.compose.runtime.mutableStateOf<WakeUiState?>(null)
        private set

    init {
        // 把"是否落盘"的设置同步给 LogStore
        viewModelScope.launch {
            settings.collect { s ->
                LogStore.writeToFile = s.writeLogs
            }
        }
    }

    fun saveDevice(device: Device) {
        viewModelScope.launch {
            val isNew = devices.value.none { it.id == device.id }
            WolStore.upsert(context, device)
            LogStore.info(
                context, "DEVICE",
                (if (isNew) "新增设备 " else "修改设备 ") + "${device.name} / ${device.endpoint} / ${device.mac}" +
                    (if (device.autoWakeEnabled) " / 每天 ${device.timeLabel}" else "")
            )
            withContext(Dispatchers.IO) {
                if (device.autoWakeEnabled) {
                    Scheduler.schedule(context, device)
                    LogStore.info(context, "SCHEDULER", "已排期 ${device.name} 每天 ${device.timeLabel}")
                } else {
                    Scheduler.cancel(context, device.id)
                }
            }
            AutoWakeService.start(context)
        }
    }

    fun deleteDevice(device: Device) {
        viewModelScope.launch {
            WolStore.delete(context, device.id)
            LogStore.info(context, "DEVICE", "删除设备 ${device.name} / ${device.endpoint}")
            withContext(Dispatchers.IO) { Scheduler.cancel(context, device.id) }
        }
    }

    fun setShowLogs(value: Boolean) {
        viewModelScope.launch {
            WolStore.saveSettings(context, settings.value.copy(showLogs = value))
            LogStore.info(context, "SETTINGS", "界面显示日志 → $value")
        }
    }

    fun setWriteLogs(value: Boolean) {
        viewModelScope.launch {
            LogStore.writeToFile = value
            WolStore.saveSettings(context, settings.value.copy(writeLogs = value))
            LogStore.info(context, "SETTINGS", "写入日志文件 → $value")
        }
    }

    fun clearLogs() {
        LogStore.clear(context)
        LogStore.info(context, "LOG", "日志已清空")
    }

    fun wake(device: Device) {
        wakeState.value = WakeUiState(device.name, sending = true)
        LogStore.info(context, "WOL", "发送 ${device.name} → ${device.endpoint} / ${device.mac}" +
            if (device.autoBroadcast) " / 含广播" else "")
        viewModelScope.launch {
            val (report, socket) = WolSender.send(
                ip = device.ip,
                port = device.port,
                mac = device.mac,
                alsoBroadcast = device.autoBroadcast
            )
            report.steps.forEach { step ->
                if (step.ok) LogStore.info(context, "WOL", "${step.label} 成功 ${step.detail}")
                else LogStore.error(context, "WOL", "${step.label} 失败 ${step.detail}")
            }
            wakeState.value = WakeUiState(device.name, sending = false, report = report)

            val probe = Prober.probe(device.ip, socket)
            socket?.close()
            LogStore.info(context, "PROBE", "${device.ip} ${probe.title} · ${probe.summary().replace("\n", " ")}")
            wakeState.value = WakeUiState(device.name, sending = false, report = report, probe = probe)
        }
    }

    fun dismissWake() {
        wakeState.value = null
    }
}

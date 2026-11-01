package com.starchen.wol.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.starchen.wol.data.AppSettings
import com.starchen.wol.data.Device
import com.starchen.wol.data.WolStore
import com.starchen.wol.data.WorkMode
import com.starchen.wol.service.AutoWakeService
import com.starchen.wol.service.Scheduler
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

    var wakeState = androidx.compose.runtime.mutableStateOf<WakeUiState?>(null)
        private set

    fun saveDevice(device: Device) {
        viewModelScope.launch {
            WolStore.upsert(context, device)
            withContext(Dispatchers.IO) {
                if (device.autoWakeEnabled) Scheduler.schedule(context, device)
                else Scheduler.cancel(context, device.id)
            }
            AutoWakeService.start(context)
        }
    }

    fun deleteDevice(device: Device) {
        viewModelScope.launch {
            WolStore.delete(context, device.id)
            withContext(Dispatchers.IO) { Scheduler.cancel(context, device.id) }
        }
    }

    fun setWorkMode(mode: WorkMode) {
        viewModelScope.launch {
            WolStore.saveSettings(context, AppSettings(workMode = mode))
            AutoWakeService.start(context)
        }
    }

    fun wake(device: Device) {
        wakeState.value = WakeUiState(device.name, sending = true)
        viewModelScope.launch {
            val (report, socket) = WolSender.send(
                ip = device.ip,
                port = device.port,
                mac = device.mac,
                alsoBroadcast = device.autoBroadcast
            )
            wakeState.value = WakeUiState(device.name, sending = false, report = report)
            val probe = Prober.probe(device.ip, socket)
            socket?.close()
            wakeState.value = WakeUiState(device.name, sending = false, report = report, probe = probe)
        }
    }

    fun dismissWake() {
        wakeState.value = null
    }
}

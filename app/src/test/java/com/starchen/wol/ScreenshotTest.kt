package com.starchen.wol

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.starchen.wol.data.Device
import com.starchen.wol.ui.DeviceEditorScreen
import com.starchen.wol.ui.HomeScreen
import com.starchen.wol.ui.theme.StarWolTheme
import org.junit.Rule
import org.junit.Test

class ScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        showSystemUi = false
    )

    private val sampleDevices = listOf(
        Device(
            id = "1", name = "客厅台式机", ip = "192.168.1.100", port = 9,
            mac = "AA:BB:CC:DD:EE:FF", autoBroadcast = true,
            autoWakeEnabled = true, autoWakeHour = 19, autoWakeMinute = 59
        ),
        Device(
            id = "2", name = "书房 NAS", ip = "192.168.1.20", port = 7,
            mac = "11:22:33:44:55:66"
        ),
        Device(
            id = "3", name = "卧室", ip = "192.168.1.30", port = 9,
            mac = "DE:AD:BE:EF:00:01", autoWakeEnabled = true,
            autoWakeHour = 7, autoWakeMinute = 30
        )
    )

    @Test
    fun home_empty() {
        paparazzi.snapshot {
            StarWolTheme {
                HomeScreen(
                    devices = emptyList(),
                    onOpenSettings = {},
                    onAddDevice = {},
                    onEditDevice = {},
                    onDeleteDevice = {},
                    onWakeDevice = {}
                )
            }
        }
    }

    @Test
    fun home_list() {
        paparazzi.snapshot {
            StarWolTheme {
                HomeScreen(
                    devices = sampleDevices,
                    onOpenSettings = {},
                    onAddDevice = {},
                    onEditDevice = {},
                    onDeleteDevice = {},
                    onWakeDevice = {}
                )
            }
        }
    }

    @Test
    fun editor_new() {
        paparazzi.snapshot {
            StarWolTheme {
                DeviceEditorScreen(
                    existing = null,
                    onBack = {},
                    onSave = {}
                )
            }
        }
    }

    @Test
    fun editor_with_schedule() {
        paparazzi.snapshot {
            StarWolTheme {
                DeviceEditorScreen(
                    existing = sampleDevices[0],
                    onBack = {},
                    onSave = {}
                )
            }
        }
    }
}

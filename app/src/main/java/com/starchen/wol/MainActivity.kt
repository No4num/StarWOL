package com.starchen.wol

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.starchen.wol.data.Device
import com.starchen.wol.service.KeepAliveItem
import com.starchen.wol.service.KeepAliveManager
import com.starchen.wol.ui.DeviceEditorScreen
import com.starchen.wol.ui.HomeScreen
import com.starchen.wol.ui.MainViewModel
import com.starchen.wol.ui.SettingsScreen
import com.starchen.wol.ui.WakeUiState
import com.starchen.wol.ui.theme.Accent
import com.starchen.wol.ui.theme.Danger
import com.starchen.wol.ui.theme.Surface2
import com.starchen.wol.ui.theme.TextPrimary
import com.starchen.wol.ui.theme.TextSecondary
import com.starchen.wol.ui.theme.TextTertiary
import com.starchen.wol.ui.theme.StarWolTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var pendingPermissionCallback: ((Boolean) -> Unit)? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingPermissionCallback?.invoke(granted)
            pendingPermissionCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.starchen.wol.util.LogStore.info(this, "APP", "StarWOL 启动")
        setContent {
            StarWolTheme {
                AppRoot(
                    viewModel = viewModel,
                    requestNotificationPermission = { callback ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pendingPermissionCallback = callback
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            callback(true)
                        }
                    },
                    requestSettingsJump = { item ->
                        KeepAliveManager.openSettingsFor(this, item)
                    }
                )
            }
        }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class Editor(val device: Device?) : Screen
}

@Composable
private fun AppRoot(
    viewModel: MainViewModel,
    requestNotificationPermission: ((Boolean) -> Unit) -> Unit,
    requestSettingsJump: (KeepAliveItem) -> Unit
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    // 二级页面接管系统返回键与返回手势，避免直接退出到桌面
    BackHandler(enabled = screen !is Screen.Home) {
        screen = Screen.Home
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            (slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(200)))
                .togetherWith(slideOutHorizontally(tween(220)) { -it / 6 } + fadeOut(tween(160)))
        },
        label = "nav"
    ) { target ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0C0E))
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            when (target) {
                is Screen.Home -> HomeScreen(
                    devices = devices,
                    onOpenSettings = { screen = Screen.Settings },
                    onAddDevice = { screen = Screen.Editor(null) },
                    onEditDevice = { screen = Screen.Editor(it) },
                    onDeleteDevice = { viewModel.deleteDevice(it) },
                    onWakeDevice = { viewModel.wake(it) }
                )

                is Screen.Settings -> SettingsScreen(
                    showLogs = settings.showLogs,
                    writeLogs = settings.writeLogs,
                    logs = logs,
                    onBack = { screen = Screen.Home },
                    onShowLogsChange = { viewModel.setShowLogs(it) },
                    onWriteLogsChange = { viewModel.setWriteLogs(it) },
                    onClearLogs = { viewModel.clearLogs() },
                    onRequestPermission = { item ->
                        if (KeepAliveManager.isRuntimePermission(item)) {
                            requestNotificationPermission { }
                        } else {
                            requestSettingsJump(item)
                        }
                    }
                )

                is Screen.Editor -> DeviceEditorScreen(
                    existing = target.device,
                    onBack = { screen = Screen.Home },
                    onSave = { device ->
                        viewModel.saveDevice(device)
                        screen = Screen.Home
                    }
                )
            }
        }
    }

    viewModel.wakeState.value?.let { state ->
        WakeDialog(state = state, onDismiss = { viewModel.dismissWake() })
    }
}

@Composable
private fun WakeDialog(state: WakeUiState, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF15171B))
                    .border(1.dp, Color(0xFF24272D), RoundedCornerShape(24.dp))
                    .padding(22.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Accent
                        )
                    } else {
                        Icon(
                            if (state.report?.success == true) Icons.Outlined.CheckCircle
                            else Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = if (state.report?.success == true) Accent else Danger,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (state.sending) "正在发送…" else state.deviceName,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(14.dp))

                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    state.report?.steps?.forEach { step ->
                        Row(Modifier.padding(bottom = 8.dp)) {
                            Box(
                                Modifier
                                    .padding(top = 6.dp)
                                    .size(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (step.ok) Accent else Danger)
                            )
                            Spacer(Modifier.width(9.dp))
                            Column {
                                Text(
                                    step.label,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(step.detail, color = TextTertiary, fontSize = 11.sp)
                            }
                        }
                    }

                    if (!state.sending) {
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0xFF23262B))
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Router,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                state.probe?.title ?: "探测中…",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            state.probe?.summary() ?: "等待回包…",
                            color = TextTertiary,
                            fontSize = 11.sp,
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(Surface2)
                        .clickable { onDismiss() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("关闭", color = TextPrimary, fontSize = 14.sp)
                }
            }
        }
    }
}

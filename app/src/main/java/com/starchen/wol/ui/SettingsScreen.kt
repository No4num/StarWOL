package com.starchen.wol.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.starchen.wol.service.KeepAliveItem
import com.starchen.wol.service.KeepAliveManager
import com.starchen.wol.service.KeepAliveState
import com.starchen.wol.ui.theme.Accent
import com.starchen.wol.ui.theme.Danger
import com.starchen.wol.ui.theme.Success
import com.starchen.wol.ui.theme.Surface1
import com.starchen.wol.ui.theme.Surface2
import com.starchen.wol.ui.theme.TextPrimary
import com.starchen.wol.ui.theme.TextSecondary
import com.starchen.wol.ui.theme.TextTertiary
import com.starchen.wol.util.LogStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    showLogs: Boolean,
    writeLogs: Boolean,
    logs: List<LogStore.Entry>,
    confirmed: List<String>,
    onBack: () -> Unit,
    onConfirmPermission: (KeepAliveItem) -> Unit,
    onShowLogsChange: (Boolean) -> Unit,
    onWriteLogsChange: (Boolean) -> Unit,
    onClearLogs: () -> Unit,
    onRequestPermission: (KeepAliveItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 检测优先：能读出真实状态就如实显示；自启动、厂商耗电管理这类读不到的项，
    // 只有用户亲手确认过才算已获得，否则显示“未知”。
    val states = KeepAliveItem.entries.map {
        val detected = KeepAliveManager.isGranted(context, it)
        KeepAliveState(it, detected ?: (if (it.name in confirmed) true else null))
    }
    var selectedArchive by remember { mutableStateOf(0) }
    var archives by remember { mutableStateOf(emptyList<LogStore.Archive>()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                archives = LogStore.archives(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(archives) {
        if (selectedArchive != 0 && archives.none { it.index == selectedArchive }) {
            selectedArchive = 0
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0C0E))) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 18.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = TextSecondary
                    )
                }
                Text("设置", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 40.dp)
            ) {
                Spacer(Modifier.height(6.dp))
                SectionLabel("保活权限")
                Spacer(Modifier.height(10.dp))
                HintCard(
                    tint = Color(0xFF10202A),
                    iconTint = Accent,
                    text = "能读到的项会自动显示真实状态。自启动和耗电管理系统没有查询接口，会显示“未知”——点“去确认”跳过去亲手设置一次，回来就记为已获得；状态不对时也可以再点一次重新确认。",
                    textColor = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                states.forEach { state ->
                    PermissionRow(
                        state = state,
                        onRequest = {
                            onRequestPermission(state.item)
                            LogStore.info(context, "PERM", "请求 ${KeepAliveManager.title(state.item)}")
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // ---------- 日志 ----------
                Spacer(Modifier.height(26.dp))
                SectionLabel("日志")
                Spacer(Modifier.height(10.dp))

                ToggleRow(
                    title = "显示日志",
                    subtitle = "在设置页里展示运行日志",
                    checked = showLogs,
                    onCheckedChange = onShowLogsChange
                )
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    title = "生成日志文件",
                    subtitle = "把日志写到应用目录，方便导出",
                    checked = writeLogs,
                    onCheckedChange = onWriteLogsChange
                )

                AnimatedVisibility(visible = showLogs) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        LogSourceSelector(
                            archives = archives,
                            selected = selectedArchive,
                            onSelect = { selectedArchive = it }
                        )
                        Spacer(Modifier.height(8.dp))

                        val historyEntries = remember(selectedArchive, archives) {
                            if (selectedArchive == 0) emptyList()
                            else LogStore.readArchive(context, selectedArchive)
                        }
                        val displayEntries = if (selectedArchive == 0) logs else historyEntries

                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF0A0B0D))
                                .border(1.dp, Color(0xFF1B1E23), RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            if (displayEntries.isEmpty()) {
                                Text("还没有日志", color = TextTertiary, fontSize = 11.sp)
                            } else {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    displayEntries.takeLast(150).forEach { entry ->
                                        Text(
                                            entry.format(),
                                            color = when (entry.level) {
                                                LogStore.Level.ERROR -> Danger
                                                LogStore.Level.WARN -> Color(0xFFFFB86B)
                                                LogStore.Level.INFO -> TextSecondary
                                            },
                                            fontSize = 9.5.sp,
                                            lineHeight = 14.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(bottom = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (selectedArchive == 0) {
                                "当前记录 · 共 ${logs.size} 条"
                            } else {
                                "历史文件 starwol.$selectedArchive.log · 共 ${displayEntries.size} 条，当前日志仍在正常记录"
                            },
                            color = TextTertiary,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Row {
                            SmallButton("导出当前展示日志") { exportLogs(context, selectedArchive) }
                            Spacer(Modifier.width(10.dp))
                            SmallButton("清空") { onClearLogs() }
                        }
                    }
                }

                Spacer(Modifier.height(30.dp))
                Text(
                    "StarWOL 1.0.11",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun exportLogs(context: android.content.Context, index: Int = 0) {
    val file = LogStore.fileFor(context, index)
    if (!file.exists() || file.length() == 0L) {
        LogStore.warn(context, "LOG", "导出失败：日志文件为空")
        return
    }
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "StarWOL 日志")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "导出当前展示日志"))
        LogStore.info(context, "LOG", "导出日志：${file.name} (${file.length()} 字节)")
    }.onFailure {
        LogStore.error(context, "LOG", "导出失败：${it.message}")
    }
}

@Composable
private fun LogSourceSelector(
    archives: List<LogStore.Archive>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (selected == 0) {
        "当前记录"
    } else {
        archives.firstOrNull { it.index == selected }?.label() ?: "前${selected}个"
    }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Surface1)
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("查看", color = TextTertiary, fontSize = 11.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text("▾", color = TextSecondary, fontSize = 12.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("当前记录", fontSize = 13.sp) },
                onClick = {
                    onSelect(0)
                    expanded = false
                }
            )
            archives.forEach { a ->
                DropdownMenuItem(
                    text = { Text(a.label(), fontSize = 13.sp) },
                    onClick = {
                        onSelect(a.index)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = TextSecondary, fontSize = 12.sp, letterSpacing = 0.6.sp)
}

@Composable
private fun HintCard(tint: Color, iconTint: Color, text: String, textColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tint)
            .padding(14.dp)
    ) {
        Icon(
            Icons.Outlined.Shield,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(text, color = textColor, fontSize = 11.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun PermissionRow(state: KeepAliveState, onRequest: () -> Unit) {
    val granted = state.granted
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                KeepAliveManager.title(state.item),
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(3.dp))
            Text(
                KeepAliveManager.description(state.item),
                color = TextTertiary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
        Spacer(Modifier.width(10.dp))
        when (granted) {
            true -> Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Success.copy(alpha = 0.16f))
                    .clickable { onRequest() }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = Success,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("已获得", color = Success, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            else -> Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (granted == null) "未知" else "未获得",
                    color = if (granted == null) TextTertiary else Danger,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(5.dp))
                SmallButton(if (granted == null) "去确认" else "请求", onRequest)
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = TextTertiary, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF06212B),
                checkedTrackColor = Accent,
                checkedBorderColor = Accent,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = Surface2,
                uncheckedBorderColor = Color(0xFF32363D)
            )
        )
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Accent.copy(alpha = 0.16f))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BigButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) Accent else Surface2)
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) Color(0xFF06212B) else TextTertiary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun GhostButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .border(1.dp, Color(0xFF2A2E34), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1C20),
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text(title, fontSize = 16.sp) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(body, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 16.sp)
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text(confirmText, color = Accent)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        }
    )
}

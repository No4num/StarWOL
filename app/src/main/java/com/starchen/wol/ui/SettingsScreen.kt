package com.starchen.wol.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import com.starchen.wol.data.WorkMode
import com.starchen.wol.service.KeepAliveItem
import com.starchen.wol.service.KeepAliveManager
import com.starchen.wol.service.KeepAliveModule
import com.starchen.wol.service.KeepAliveState
import com.starchen.wol.ui.components.SegmentedMode
import com.starchen.wol.ui.theme.Accent
import com.starchen.wol.ui.theme.Danger
import com.starchen.wol.ui.theme.Success
import com.starchen.wol.ui.theme.Surface1
import com.starchen.wol.ui.theme.Surface2
import com.starchen.wol.ui.theme.TextPrimary
import com.starchen.wol.ui.theme.TextSecondary
import com.starchen.wol.ui.theme.TextTertiary
import com.starchen.wol.util.LogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    workMode: WorkMode,
    showLogs: Boolean,
    writeLogs: Boolean,
    logs: List<LogStore.Entry>,
    onBack: () -> Unit,
    onWorkModeChange: (WorkMode) -> Unit,
    onShowLogsChange: (Boolean) -> Unit,
    onWriteLogsChange: (Boolean) -> Unit,
    onClearLogs: () -> Unit,
    onRequestPermission: (KeepAliveItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var states by remember { mutableStateOf(KeepAliveManager.allStates(context)) }
    var rootAvailable by remember { mutableStateOf<Boolean?>(null) }
    var applying by remember { mutableStateOf(false) }
    var confirmApply by remember { mutableStateOf(false) }
    var confirmRevert by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<KeepAliveManager.RootBatch?>(null) }
    var showCommands by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                states = KeepAliveManager.allStates(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(workMode) {
        if (workMode == WorkMode.ROOT && rootAvailable == null) {
            rootAvailable = withContext(Dispatchers.IO) { KeepAliveManager.isRootAvailable() }
            if (rootAvailable == true) LogStore.info(context, "ROOT", "检测到 root 可用")
        }
    }

    val plan = remember { KeepAliveManager.keepAlivePlan(context.packageName) }

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
                SectionLabel("工作模式")
                Spacer(Modifier.height(10.dp))
                SegmentedMode(
                    options = listOf("正常", "Root"),
                    selectedIndex = if (workMode == WorkMode.NORMAL) 0 else 1,
                    onSelect = { idx ->
                        onWorkModeChange(if (idx == 0) WorkMode.NORMAL else WorkMode.ROOT)
                    }
                )
                Spacer(Modifier.height(18.dp))

                // ---------- 正常模式：权限 ----------
                AnimatedVisibility(
                    visible = workMode == WorkMode.NORMAL,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        HintCard(
                            tint = Color(0xFF10202A),
                            iconTint = Accent,
                            text = "下面这些是保活需要的权限，缺一个定时任务都可能被系统掐断。可以逐项点右边的按钮去授权。",
                            textColor = TextSecondary
                        )
                        Spacer(Modifier.height(12.dp))
                        states.forEach { state ->
                            PermissionRow(
                                state = state,
                                onRequest = {
                                    onRequestPermission(state.item)
                                    LogStore.info(context, "PERM", "请求 ${KeepAliveManager.title(state.item)}")
                                    scope.launch {
                                        delay(600)
                                        states = KeepAliveManager.allStates(context)
                                    }
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                // ---------- Root 模式 ----------
                AnimatedVisibility(
                    visible = workMode == WorkMode.ROOT,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        HintCard(
                            tint = Color(0xFF20170F),
                            iconTint = Color(0xFFFFB86B),
                            text = "root 只做四件事：把自己加进 Doze 白名单、待机桶设为 active、允许两条后台 op。不动全局设置，不碰其他应用。",
                            textColor = Color(0xFFC9B49B)
                        )
                        Spacer(Modifier.height(12.dp))

                        // 检测状态
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Surface1)
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when (rootAvailable) {
                                        true -> "已获取 root"
                                        false -> "未检测到 root"
                                        null -> "检测中…"
                                    },
                                    color = when (rootAvailable) {
                                        true -> Success
                                        false -> Danger
                                        null -> TextSecondary
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "检测方式是执行 su -c id，会弹系统授权框",
                                    color = TextTertiary,
                                    fontSize = 11.sp
                                )
                            }
                            if (rootAvailable == false) {
                                SmallButton("重试") {
                                    scope.launch {
                                        rootAvailable = withContext(Dispatchers.IO) {
                                            KeepAliveManager.isRootAvailable()
                                        }
                                    }
                                }
                            }
                        }

                        if (rootAvailable == true) {
                            Spacer(Modifier.height(12.dp))

                            // 命令预览
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Surface1)
                                    .clickable { showCommands = !showCommands }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "将执行 ${plan.size} 条命令",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    if (showCommands) "收起" else "展开",
                                    color = Accent,
                                    fontSize = 12.sp
                                )
                            }
                            AnimatedVisibility(visible = showCommands) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF0D0E11))
                                        .padding(14.dp)
                                ) {
                                    plan.forEach { cmd ->
                                        Text(
                                            "› ${cmd.label}",
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(bottom = 2.dp)
                                        )
                                        Text(
                                            cmd.shell,
                                            color = Accent.copy(alpha = 0.75f),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(bottom = 10.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            BigButton(
                                text = if (applying) "正在执行…" else "应用 root 保活",
                                enabled = !applying
                            ) { confirmApply = true }

                            Spacer(Modifier.height(8.dp))
                            GhostButton("撤销 root 保活（恢复默认）") { confirmRevert = true }

                            Spacer(Modifier.height(14.dp))
                            Text(
                                "进程层面的兜底",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                "上面的白名单只能让系统不主动杀你。真要“杀了还能活”，得让 root 侧替你看门。",
                                color = TextTertiary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            GhostButton("导出守护模块（Magisk）") { exportKeepAliveModule(context) }
                        }

                        lastResult?.let { batch ->
                            Spacer(Modifier.height(12.dp))
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF0D0E11))
                                    .padding(14.dp)
                            ) {
                                Text(
                                    "执行结果 ${batch.steps.count { it.ok }}/${batch.steps.size}",
                                    color = if (batch.allOk) Success else Danger,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                batch.steps.forEach { step ->
                                    Row(Modifier.padding(bottom = 6.dp)) {
                                        Box(
                                            Modifier
                                                .padding(top = 5.dp)
                                                .size(5.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(if (step.ok) Success else Danger)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                step.command.label,
                                                color = TextPrimary,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                step.command.shell,
                                                color = TextTertiary,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            if (step.output.isNotBlank() && step.output != "（无输出）") {
                                                Text(
                                                    step.output.take(200),
                                                    color = TextTertiary,
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
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
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF0A0B0D))
                                .border(1.dp, Color(0xFF1B1E23), RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            if (logs.isEmpty()) {
                                Text("还没有日志", color = TextTertiary, fontSize = 11.sp)
                            } else {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    logs.takeLast(150).forEach { entry ->
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
                        Spacer(Modifier.height(10.dp))
                        Row {
                            SmallButton("导出日志") { exportLogs(context) }
                            Spacer(Modifier.width(10.dp))
                            SmallButton("清空") { onClearLogs() }
                        }
                    }
                }

                Spacer(Modifier.height(30.dp))
                Text(
                    "StarWOL 1.0.1",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // 执行前确认
    if (confirmApply) {
        ConfirmDialog(
            title = "确认执行 root 命令",
            body = plan.joinToString("\n") { "${it.label}\n  ${it.shell}" },
            confirmText = "执行",
            onConfirm = {
                confirmApply = false
                applying = true
                scope.launch {
                    LogStore.info(context, "ROOT", "用户确认，开始应用 root 保活")
                    val result = withContext(Dispatchers.IO) {
                        KeepAliveManager.applyRootKeepAlive(context) { step ->
                            LogStore.append(
                                context,
                                if (step.ok) LogStore.Level.INFO else LogStore.Level.ERROR,
                                "ROOT",
                                "${step.command.label}: ${step.output.take(300)}"
                            )
                        }
                    }
                    // 只读核对一遍
                    val verify = withContext(Dispatchers.IO) {
                        KeepAliveManager.verifyRootKeepAlive(context)
                    }
                    verify.steps.forEach {
                        LogStore.info(context, "ROOT", "${it.command.label}: ${it.output.take(200)}")
                    }
                    lastResult = result
                    applying = false
                }
            },
            onDismiss = { confirmApply = false }
        )
    }

    if (confirmRevert) {
        ConfirmDialog(
            title = "撤销 root 保活",
            body = "会把本应用从 Doze 白名单移除、待机桶恢复默认、两条后台 op 设回 default。不会影响其他应用。",
            confirmText = "撤销",
            onConfirm = {
                confirmRevert = false
                applying = true
                scope.launch {
                    LogStore.info(context, "ROOT", "用户确认，撤销 root 保活")
                    val result = withContext(Dispatchers.IO) {
                        KeepAliveManager.revertRootKeepAlive(context)
                    }
                    result.steps.forEach {
                        LogStore.append(
                            context,
                            if (it.ok) LogStore.Level.INFO else LogStore.Level.ERROR,
                            "ROOT",
                            "${it.command.label}: ${it.output.take(300)}"
                        )
                    }
                    lastResult = result
                    applying = false
                }
            },
            onDismiss = { confirmRevert = false }
        )
    }
}

private fun exportKeepAliveModule(context: android.content.Context) {
    val file = KeepAliveModule.build(context) ?: return
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "StarWOL 守护模块")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "导出守护模块"))
    }.onFailure {
        LogStore.error(context, "ROOT", "导出守护模块失败：${it.message}")
    }
}

private fun exportLogs(context: android.content.Context) {    val file = LogStore.logFile(context)
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
        context.startActivity(Intent.createChooser(intent, "导出日志"))
        LogStore.info(context, "LOG", "导出日志：${file.name} (${file.length()} 字节)")
    }.onFailure {
        LogStore.error(context, "LOG", "导出失败：${it.message}")
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
        if (state.granted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = Success,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("已获得", color = Success, fontSize = 12.sp)
            }
        } else {
            SmallButton("请求", onRequest)
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

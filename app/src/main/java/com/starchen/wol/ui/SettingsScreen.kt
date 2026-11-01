package com.starchen.wol.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starchen.wol.data.WorkMode
import com.starchen.wol.service.KeepAliveItem
import com.starchen.wol.service.KeepAliveManager
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    workMode: WorkMode,
    onBack: () -> Unit,
    onWorkModeChange: (WorkMode) -> Unit,
    onRequestPermission: (KeepAliveItem) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var states by remember { mutableStateOf(KeepAliveManager.allStates(context)) }
    var rootAvailable by remember { mutableStateOf<Boolean?>(null) }
    var rootLog by remember { mutableStateOf<List<String>>(emptyList()) }
    var checkingRoot by remember { mutableStateOf(false) }
    var applyingRoot by remember { mutableStateOf(false) }

    // 回到前台时刷新一次权限状态
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                states = KeepAliveManager.allStates(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(workMode) {
        if (workMode == WorkMode.ROOT && rootAvailable == null) {
            rootAvailable = withContext(Dispatchers.IO) { KeepAliveManager.isRootAvailable() }
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

                AnimatedVisibility(
                    visible = workMode == WorkMode.NORMAL,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        KeepAliveHint()
                        Spacer(Modifier.height(12.dp))
                        states.forEach { state ->
                            PermissionRow(
                                state = state,
                                onRequest = {
                                    onRequestPermission(state.item)
                                    scope.launch {
                                        kotlinx.coroutines.delay(600)
                                        states = KeepAliveManager.allStates(context)
                                    }
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                AnimatedVisibility(
                    visible = workMode == WorkMode.ROOT,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        RootHint()
                        Spacer(Modifier.height(12.dp))
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
                                    "root 仅用于保活（白名单 / 待机桶），不碰你的数据和系统设置",
                                    color = TextTertiary,
                                    fontSize = 11.sp
                                )
                            }
                            if (rootAvailable == false) {
                                SmallButton("重试") {
                                    checkingRoot = true
                                    scope.launch {
                                        rootAvailable = withContext(Dispatchers.IO) {
                                            KeepAliveManager.isRootAvailable()
                                        }
                                        checkingRoot = false
                                    }
                                }
                            }
                        }

                        if (rootAvailable == true) {
                            Spacer(Modifier.height(10.dp))
                            BigButton(
                                text = if (applyingRoot) "正在写入…" else "应用 root 保活",
                                enabled = !applyingRoot
                            ) {
                                applyingRoot = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        KeepAliveManager.applyRootKeepAlive(context)
                                    }
                                    rootLog = result.log
                                    applyingRoot = false
                                    states = KeepAliveManager.allStates(context)
                                }
                            }
                        }

                        if (rootLog.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF0D0E11))
                                    .padding(14.dp)
                            ) {
                                rootLog.forEach { line ->
                                    Text(
                                        line,
                                        color = TextTertiary,
                                        fontSize = 10.sp,
                                        lineHeight = 15.sp,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(30.dp))
                Text(
                    "StarWOL 1.0.0",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
private fun KeepAliveHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF10202A))
            .padding(14.dp)
    ) {
        Icon(
            Icons.Outlined.Shield,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "下面这些是保活需要的权限，缺一个定时任务都可能被系统掐断。可以逐项点右边的按钮去授权。",
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun RootHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF20170F))
            .padding(14.dp)
    ) {
        Icon(
            Icons.Outlined.Shield,
            contentDescription = null,
            tint = Color(0xFFFFB86B),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "root 模式下会把应用写进 Doze 白名单、待机桶和后台 op 允许列表，配合前台服务保活。",
            color = Color(0xFFC9B49B),
            fontSize = 11.sp,
            lineHeight = 17.sp
        )
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

package com.starchen.wol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starchen.wol.data.Device
import com.starchen.wol.ui.theme.Accent
import com.starchen.wol.ui.theme.AccentDim
import com.starchen.wol.ui.theme.Surface1
import com.starchen.wol.ui.theme.Surface2
import com.starchen.wol.ui.theme.TextPrimary
import com.starchen.wol.ui.theme.TextSecondary
import com.starchen.wol.ui.theme.TextTertiary

@Composable
fun HomeScreen(
    devices: List<Device>,
    onOpenSettings: () -> Unit,
    onAddDevice: () -> Unit,
    onEditDevice: (Device) -> Unit,
    onDeleteDevice: (Device) -> Unit,
    onWakeDevice: (Device) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<Device?>(null) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0C0E))
    ) {
        Column(Modifier.fillMaxSize()) {
            HomeTopBar(onOpenSettings)
            if (devices.isEmpty()) {
                EmptyState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 96.dp)
                )
            } else {
                Text(
                    "长按卡片删除，点击插头按钮以唤醒设备",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 18.dp, end = 18.dp, top = 6.dp, bottom = 112.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(devices, key = { it.id }) { device ->
                        DeviceCard(
                            device = device,
                            onWake = { onWakeDevice(device) },
                            onEdit = { onEditDevice(device) },
                            onLongClick = { pendingDelete = device }
                        )
                    }
                }
            }
        }

        pendingDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                containerColor = androidx.compose.ui.graphics.Color(0xFF1A1C20),
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary,
                title = { Text("删除设备") },
                text = { Text("确定要删除「${target.name.ifBlank { "未命名设备" }}」吗？") },
                confirmButton = {
                    TextButton(onClick = {
                        onDeleteDevice(target)
                        pendingDelete = null
                    }) { Text("删除", color = com.starchen.wol.ui.theme.Danger) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text("取消", color = TextSecondary)
                    }
                }
            )
        }

        FloatingActionButton(
            onClick = onAddDevice,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 30.dp)
                .size(60.dp),
            shape = CircleShape,
            containerColor = Color.White,
            contentColor = Color.Black,
            elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp
            )
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = "添加设备",
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun HomeTopBar(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 12.dp, top = 18.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "局域网唤醒",
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Wake on LAN",
                color = TextTertiary,
                fontSize = 12.sp,
                letterSpacing = 2.sp
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = "设置",
                tint = TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Surface1)
                    .border(1.dp, Surface2, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Power,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.height(18.dp))
            Text("暂无设备", color = TextSecondary, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "点右下角的 + 添加一台",
                color = TextTertiary,
                fontSize = 13.sp
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceCard(
    device: Device,
    onWake: () -> Unit,
    onEdit: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF17191D), Color(0xFF121316))
                )
            )
            .border(1.dp, Color(0xFF22252A), RoundedCornerShape(20.dp))
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 14.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(AccentDim),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Devices,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.name.ifBlank { "未命名设备" },
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    device.endpoint,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        device.mac,
                        color = TextTertiary,
                        fontSize = 12.sp,
                        letterSpacing = 0.6.sp
                    )
                    if (device.autoWakeEnabled) {
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            device.timeLabel,
                            color = Accent,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            // 唤醒：给足点击面积，浅青底
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AccentDim)
                    .clickable(onClick = onWake),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Power,
                    contentDescription = "唤醒 ${device.name}",
                    tint = Accent,
                    modifier = Modifier.size(26.dp)
                )            }
            Spacer(Modifier.width(10.dp))
            // 编辑：和唤醒同高的方块底，不再是一个孤零零的小图标
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface1)
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "编辑 ${device.name}",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

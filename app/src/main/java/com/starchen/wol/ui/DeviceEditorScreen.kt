package com.starchen.wol.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starchen.wol.data.Device
import com.starchen.wol.ui.components.WheelPicker
import com.starchen.wol.ui.theme.Accent
import com.starchen.wol.ui.theme.Danger
import com.starchen.wol.ui.theme.Surface1
import com.starchen.wol.ui.theme.Surface2
import com.starchen.wol.ui.theme.TextPrimary
import com.starchen.wol.ui.theme.TextSecondary
import com.starchen.wol.ui.theme.TextTertiary
import com.starchen.wol.util.LogStore
import com.starchen.wol.wol.ArpLookup
import com.starchen.wol.wol.MacUtils
import kotlinx.coroutines.launch

@Composable
fun DeviceEditorScreen(
    existing: Device?,
    onBack: () -> Unit,
    onSave: (Device) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var ip by rememberSaveable { mutableStateOf(existing?.ip ?: "") }
    var port by rememberSaveable { mutableStateOf((existing?.port ?: 9).toString()) }
    var mac by rememberSaveable { mutableStateOf(existing?.mac ?: "") }
    var broadcast by rememberSaveable { mutableStateOf(existing?.autoBroadcast ?: false) }
    var autoWake by rememberSaveable { mutableStateOf(existing?.autoWakeEnabled ?: false) }
    var hour by rememberSaveable { mutableStateOf(existing?.autoWakeHour ?: 8) }
    var minute by rememberSaveable { mutableStateOf(existing?.autoWakeMinute ?: 0) }
    var macTouched by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var arpBusy by remember { mutableStateOf(false) }
    var arpHint by remember { mutableStateOf<String?>(null) }
    var arpHintOk by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val macValid = MacUtils.normalize(mac) != null
    val ipValid = isValidIpv4(ip.trim())
    val portValid = port.toIntOrNull()?.let { it in 1..65535 } == true
    val nameValid = name.isNotBlank()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0C0E))
    ) {
        Column(Modifier.fillMaxSize()) {
            EditorTopBar(
                title = if (existing == null) "添加设备" else "编辑设备",
                onBack = onBack
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 120.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                WolField(
                    label = "设备名",
                    placeholder = "客厅台式机",
                    value = name,
                    onValueChange = { name = it },
                    error = if (submitted && !nameValid) "不能为空" else null
                )
                Spacer(Modifier.height(16.dp))
                WolField(
                    label = "IP 地址",
                    placeholder = "192.168.1.10",
                    value = ip,
                    onValueChange = { ip = it },
                    keyboardType = KeyboardType.Uri,
                    error = when {
                        submitted && ip.isBlank() -> "不能为空"
                        submitted && !ipValid -> "IPv4 格式不对"
                        else -> null
                    }
                )
                Spacer(Modifier.height(16.dp))
                WolField(
                    label = "端口",
                    placeholder = "9",
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
                    keyboardType = KeyboardType.Number,
                    error = when {
                        submitted && port.isBlank() -> "不能为空"
                        submitted && !portValid -> "1 - 65535"
                        else -> null
                    }
                )
                Spacer(Modifier.height(16.dp))

                // MAC + 完成按钮
                Row(verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.weight(1f)) {
                        WolField(
                            label = "MAC 地址",
                            placeholder = "AA:BB:CC:DD:EE:FF",
                            value = mac,
                            onValueChange = {
                                mac = it
                                macTouched = true
                            },
                            error = when {
                                submitted && mac.isBlank() -> "不能为空"
                                macTouched && mac.isNotBlank() && !macValid -> "格式无法识别"
                                else -> null
                            }
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (macValid) Accent.copy(alpha = 0.16f) else Surface2)
                            .clickable {
                                mac = MacUtils.normalize(mac) ?: mac.uppercase()
                                macTouched = true
                            }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "完成",
                            color = if (macValid) Accent else TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (arpBusy) Surface2 else Accent.copy(alpha = 0.13f))
                            .clickable(enabled = !arpBusy) {
                                val target = ip.trim()
                                if (!isValidIpv4(target)) {
                                    arpHintOk = false
                                    arpHint = "先填一个正确的 IP"
                                    return@clickable
                                }
                                arpBusy = true
                                arpHint = null
                                scope.launch {
                                    val found = ArpLookup.resolve(target)
                                    arpBusy = false
                                    if (found != null) {
                                        mac = found
                                        macTouched = true
                                        arpHintOk = true
                                        arpHint = "已获取 $found"
                                        LogStore.info(context, "ARP", "$target → $found")
                                    } else {
                                        arpHintOk = false
                                        arpHint = if (ArpLookup.readable()) {
                                            "没找到记录，设备可能不在线"
                                        } else {
                                            "系统不允许读 ARP 表，只能手填"
                                        }
                                        LogStore.warn(context, "ARP", "获取 $target 的 MAC 失败")
                                    }
                                }
                            }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (arpBusy) "获取中…" else "自动获取",
                            color = if (arpBusy) TextTertiary else Accent,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    arpHint?.let { hint ->
                        Spacer(Modifier.width(10.dp))
                        Text(
                            hint,
                            color = if (arpHintOk) Accent.copy(alpha = 0.85f) else TextTertiary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "支持 AABB-CCDD-EEFF / AA-BB-CC-DD-EE-FF / AABB:CCDD:EEFF / AA:BB:CC:DD:EE:FF",
                    color = Danger.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(Modifier.height(26.dp))

                // 自动广播
                SettingRow(
                    title = "同时发送广播",
                    subtitle = "单播发完后再向 255.255.255.255 发一遍",
                    checked = broadcast,
                    onCheckedChange = { broadcast = it }
                )

                Spacer(Modifier.height(10.dp))

                // 自动唤醒
                SettingRow(
                    title = "自动唤醒",
                    subtitle = "每天到点自动发送 WOL",
                    checked = autoWake,
                    onCheckedChange = { enabled ->
                        autoWake = enabled
                        if (enabled) requestExactAlarmIfNeeded(context)
                    }
                )

                AnimatedVisibility(
                    visible = autoWake,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    TimeWheel(hour = hour, minute = minute, onHour = { hour = it }, onMinute = { minute = it })
                }

                Spacer(Modifier.height(28.dp))
            }
        }

        // 保存
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 28.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Accent)
                    .clickable {
                        submitted = true
                        val normalized = MacUtils.normalize(mac)
                        if (nameValid && ipValid && portValid && normalized != null) {
                            onSave(
                                Device(
                                    id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    ip = ip.trim(),
                                    port = port.toInt(),
                                    mac = normalized,
                                    autoBroadcast = broadcast,
                                    autoWakeEnabled = autoWake,
                                    autoWakeHour = hour,
                                    autoWakeMinute = minute
                                )
                            )
                        }
                    }
                    .padding(horizontal = 28.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("保存", color = Color(0xFF06212B), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun EditorTopBar(title: String, onBack: () -> Unit) {
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
        Text(title, color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun WolField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    error: String? = null
) {
    Column {
        Text(label, color = TextSecondary, fontSize = 12.sp, letterSpacing = 0.4.sp)
        Spacer(Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Surface1)
                .border(
                    1.dp,
                    if (error != null) Danger.copy(alpha = 0.6f) else Color(0xFF23262B),
                    RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 15.sp
                ),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(placeholder, color = TextTertiary, fontSize = 15.sp)
                        }
                        inner()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (error != null) {
            Spacer(Modifier.height(5.dp))
            Text(error, color = Danger, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingRow(
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
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 13.dp),
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
private fun TimeWheel(
    hour: Int,
    minute: Int,
    onHour: (Int) -> Unit,
    onMinute: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0F1013))
            .border(1.dp, Color(0xFF1F2227), RoundedCornerShape(20.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "每天 %02d:%02d 自动发送".format(hour, minute),
            color = TextTertiary,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            WheelPicker(
                count = 24,
                value = hour,
                onValueChange = onHour,
                modifier = Modifier.width(74.dp)
            )
            Text(
                ":",
                color = TextPrimary,
                fontSize = 40.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            WheelPicker(
                count = 60,
                value = minute,
                onValueChange = onMinute,
                modifier = Modifier.width(74.dp)
            )
        }
    }
}

/** 打开自动唤醒时，顺手把精确闹钟权限要了，不然到点会被系统拖到几分钟后 */
private fun requestExactAlarmIfNeeded(context: android.content.Context) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return
    val am = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
    if (am.canScheduleExactAlarms()) return
    runCatching {
        context.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}

private fun isValidIpv4(text: String): Boolean {
    if (text.isBlank()) return false
    val parts = text.split(".")
    if (parts.size != 4) return false
    return parts.all { p ->
        p.isNotEmpty() && p.length <= 3 && p.all { it.isDigit() } &&
            p.toInt() in 0..255 && (p.length == 1 || !p.startsWith("0"))
    }
}

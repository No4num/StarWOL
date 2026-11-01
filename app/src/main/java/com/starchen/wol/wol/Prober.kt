package com.starchen.wol.wol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * "回包"探测。
 *
 * WOL 本身是单向的 —— 网卡收到 magic packet 就开机，不会回 WOL 包。
 * 所以这里在发出后做三件事，任意一件有结果都算"有回包"：
 *   1. 监听发送用的 UDP socket，看有没有设备往这个源端口发包
 *   2. ping 一下目的 IP
 *   3. 探几个常见 TCP 端口，判断机器是不是已经起来了
 */
data class ProbeResult(
    val udpPackets: List<UdpReply> = emptyList(),
    val pingOk: Boolean? = null,
    val pingDetail: String = "",
    val openPorts: List<Int> = emptyList(),
    val elapsedMs: Long = 0
) {
    val answered: Boolean get() = udpPackets.isNotEmpty() || pingOk == true || openPorts.isNotEmpty()

    val title: String
        get() = when {
            udpPackets.isNotEmpty() -> "收到回包"
            pingOk == true -> "设备可达"
            openPorts.isNotEmpty() -> "端口有响应"
            else -> "无回包"
        }

    fun summary(): String = buildString {
        if (udpPackets.isNotEmpty()) {
            udpPackets.take(3).forEach {
                appendLine("UDP 回包 ← ${it.from}（${it.size} 字节）")
                if (it.preview.isNotBlank()) appendLine("   ${it.preview}")
            }
        }
        if (pingOk == true) appendLine("ICMP 响应：${pingDetail.ifBlank { "通" }}")
        if (openPorts.isNotEmpty()) appendLine("TCP 开放端口：${openPorts.joinToString(", ")}")
        if (!answered) appendLine("${elapsedMs}ms 内没有收到任何响应")
    }.trim()
}

data class UdpReply(val from: String, val size: Int, val preview: String)

object Prober {

    private val COMMON_PORTS = listOf(9, 7, 4000, 3389, 445, 22, 80, 5900)

    suspend fun probe(
        ip: String,
        socket: DatagramSocket?,
        timeoutMs: Long = 2500
    ): ProbeResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val udpReplies = mutableListOf<UdpReply>()

        coroutineScope {
            val pingJob = async { ping(ip, timeoutMs) }
            val portJob = async { scanPorts(ip) }

            // 监听 UDP，最多 timeoutMs
            if (socket != null) {
                val deadline = System.currentTimeMillis() + timeoutMs
                val buf = ByteArray(2048)
                try {
                    while (true) {
                        val remain = deadline - System.currentTimeMillis()
                        if (remain <= 0) break
                        socket.soTimeout = remain.toInt().coerceAtLeast(1)
                        val packet = DatagramPacket(buf, buf.size)
                        try {
                            socket.receive(packet)
                        } catch (_: java.net.SocketTimeoutException) {
                            break
                        }
                        val from = "${packet.address?.hostAddress ?: "?"}:${packet.port}"
                        val data = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                        udpReplies += UdpReply(from, packet.length, preview(data))
                    }
                } catch (_: Exception) {
                    // socket 已关闭之类，直接结束
                }
            }

            val (pingOk, pingDetail) = pingJob.await()
            val ports = portJob.await()
            ProbeResult(
                udpPackets = udpReplies.toList(),
                pingOk = pingOk,
                pingDetail = pingDetail,
                openPorts = ports,
                elapsedMs = System.currentTimeMillis() - started
            )
        }
    }

    private fun preview(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val printable = data.take(48).all { it in 32..126 }
        return if (printable) {
            String(data.copyOf(minOf(data.size, 48)))
        } else {
            data.take(16).joinToString(" ") { "%02X".format(it) }
        }
    }

    /** 调系统 ping，普通应用也能用 /system/bin/ping */
    private fun ping(ip: String, timeoutMs: Long): Pair<Boolean?, String> {
        return try {
            val process = ProcessBuilder(
                "ping", "-c", "1", "-W", "${(timeoutMs / 1000).coerceAtLeast(1)}", ip
            ).redirectErrorStream(true).start()
            val text = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val finished = process.waitFor()
            val line = text.lineSequence().firstOrNull { it.contains("bytes from") || it.contains("time=") }
            (finished == 0) to (line?.trim() ?: if (finished == 0) "通" else "")
        } catch (e: Exception) {
            null to (e.message ?: "")
        }
    }

    private fun scanPorts(ip: String): List<Int> {
        val open = mutableListOf<Int>()
        for (port in COMMON_PORTS) {
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(ip, port), 350)
                    open += port
                }
            } catch (_: Exception) {
            }
        }
        return open
    }

    fun resolve(ip: String): String? = runCatching {
        InetAddress.getByName(ip).hostAddress
    }.getOrNull()
}

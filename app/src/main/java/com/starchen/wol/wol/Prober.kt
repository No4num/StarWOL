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
 * 一次探测里抓到的一条报文，尽量按 Wireshark 那种口吻记录。
 *
 * 能不能拿到真实字节，取决于协议和权限：
 *   - 自己发出去的 WOL 包：当然有
 *   - UDP 回包：有
 *   - TCP banner（SSH / FTP 这类服务一连接就主动吐一串）：有
 *   - ICMP：没有。系统 ping 是独立进程，普通应用拿不到 raw socket，
 *     只能把它打印的那行文本记下来
 *
 * ARP 也是同理 —— 以太网帧必须 AF_PACKET，而 Android 的 untrusted_app
 * 没有 CAP_NET_RAW，SELinux 也不放行，所以这里没有任何 ARP 报文。
 */
data class PacketRecord(
    val dir: String,
    val proto: String,
    val src: String,
    val dst: String,
    val bytes: ByteArray? = null,
    val note: String = ""
) {
    val size: Int get() = bytes?.size ?: 0

    val headline: String
        get() = buildString {
            append("$dir  $proto")
            append("   $src → $dst")
            if (bytes != null) append("   ${size} B")
            if (note.isNotBlank()) append("   · $note")
        }
}

data class UdpReply(val from: String, val size: Int, val preview: String)

data class ProbeResult(
    val packets: List<PacketRecord> = emptyList(),
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

    /** 有用的报文：有字节的，或者说明收到响应的 */
    val interesting: List<PacketRecord>
        get() = packets.filter { it.bytes != null || it.proto != "ICMP" || it.note.startsWith("echo reply") }
}

object Prober {

    private val COMMON_PORTS = listOf(9, 7, 4000, 3389, 445, 22, 80, 5900)

    suspend fun probe(
        ip: String,
        socket: DatagramSocket?,
        timeoutMs: Long = 2500,
        txPacket: ByteArray? = null,
        txPort: Int = 9
    ): ProbeResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val records = mutableListOf<PacketRecord>()
        val udpReplies = mutableListOf<UdpReply>()

        // 发出去的 WOL 包，先记一笔 —— 这是唯一 100% 拿得到原始字节的报文
        if (txPacket != null) {
            records += PacketRecord(
                dir = "TX",
                proto = "UDP",
                src = "本机",
                dst = "$ip:$txPort",
                bytes = txPacket,
                note = "WOL magic packet"
            )
        }

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
                        records += PacketRecord(
                            dir = "RX",
                            proto = "UDP",
                            src = from,
                            dst = "本机:${socket.localPort}",
                            bytes = data,
                            note = "单播回包"
                        )
                    }
                } catch (_: Exception) {
                    // socket 已关闭之类，直接结束
                }
            }

            val (pingOk, pingDetail) = pingJob.await()
            val hits = portJob.await()

            records += PacketRecord(
                dir = "RX",
                proto = "ICMP",
                src = ip,
                dst = "本机",
                bytes = null,
                note = if (pingOk == true) {
                    "echo reply · ${pingDetail.ifBlank { "通" }}"
                } else {
                    "无响应（系统 ping 拿不到原始帧）"
                }
            )

            hits.forEach { hit ->
                records += PacketRecord(
                    dir = "RX",
                    proto = "TCP",
                    src = "$ip:${hit.port}",
                    dst = "本机",
                    bytes = null,
                    note = "SYN-ACK，连接建立"
                )
                hit.banner?.let { b ->
                    records += PacketRecord(
                        dir = "RX",
                        proto = "TCP",
                        src = "$ip:${hit.port}",
                        dst = "本机",
                        bytes = b,
                        note = "服务端主动下发的 banner"
                    )
                }
            }

            ProbeResult(
                packets = records.toList(),
                udpPackets = udpReplies.toList(),
                pingOk = pingOk,
                pingDetail = pingDetail,
                openPorts = hits.map { it.port },
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
            data.take(16).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
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

    private data class PortHit(val port: Int, val banner: ByteArray?)

    /**
     * 连一下常见端口。连上之后顺带读一下 —— 有些服务（SSH、FTP、SMTP）
     * 一建立连接就会主动吐一行 banner，那就是真实的应用层字节。
     */
    private fun scanPorts(ip: String): List<PortHit> {
        val hits = mutableListOf<PortHit>()
        for (port in COMMON_PORTS) {
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(ip, port), 350)
                    s.soTimeout = 350
                    val banner = try {
                        val buf = ByteArray(1024)
                        val n = s.getInputStream().read(buf)
                        if (n > 0) buf.copyOf(n) else null
                    } catch (_: Exception) {
                        null
                    }
                    hits += PortHit(port, banner)
                }
            } catch (_: Exception) {
            }
        }
        return hits
    }

    fun resolve(ip: String): String? = runCatching {
        InetAddress.getByName(ip).hostAddress
    }.getOrNull()

    /** 标准 hexdump：16 字节一行，左偏移、中十六进制、右 ASCII */
    fun hexDump(bytes: ByteArray, maxBytes: Int = 512): String {
        val shown = if (bytes.size > maxBytes) bytes.copyOf(maxBytes) else bytes
        return buildString {
            var i = 0
            while (i < shown.size) {
                val chunk = shown.copyOfRange(i, minOf(i + 16, shown.size))
                append("%04X  ".format(i))
                for (j in 0 until 16) {
                    if (j < chunk.size) {
                        append("%02X ".format(chunk[j].toInt() and 0xFF))
                    } else {
                        append("   ")
                    }
                    if (j == 7) append(' ')
                }
                append(' ')
                chunk.forEach { b ->
                    val c = b.toInt() and 0xFF
                    append(if (c in 32..126) c.toChar() else '.')
                }
                if (i + 16 < shown.size) append('\n')
                i += 16
            }
            if (bytes.size > maxBytes) {
                if (isNotEmpty()) append('\n')
                append("… 共 ${bytes.size} 字节，只显示前 $maxBytes 字节")
            }
        }
    }
}

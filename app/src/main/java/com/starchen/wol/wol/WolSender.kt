package com.starchen.wol.wol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** 发送结果，一条一条记给 UI 看 */
data class SendStep(val label: String, val ok: Boolean, val detail: String)

data class SendReport(
    val steps: List<SendStep>,
    val packet: ByteArray? = null,
    val startedAt: Long = System.currentTimeMillis()
) {
    val success: Boolean get() = steps.isNotEmpty() && steps.first().ok
}

object WolSender {

    private const val BROADCAST_ADDR = "255.255.255.255"
    private const val BIND_TIMEOUT_MS = 3000

    /** 6 字节 0xFF + 16 次 MAC = 102 字节 */
    fun magicPacket(macBytes: ByteArray): ByteArray {
        val payload = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) payload[i] = 0xFF.toByte()
        for (i in 0 until 16) {
            System.arraycopy(macBytes, 0, payload, 6 + i * 6, 6)
        }
        return payload
    }

    /**
     * 单播 →（可选）广播。
     * socket 会返回给调用方，用来顺带监听回包。
     */
    suspend fun send(
        ip: String,
        port: Int,
        mac: String,
        alsoBroadcast: Boolean
    ): Pair<SendReport, DatagramSocket?> = withContext(Dispatchers.IO) {
        val steps = mutableListOf<SendStep>()
        val macBytes = MacUtils.toBytes(mac)
        if (macBytes == null) {
            return@withContext SendReport(
                listOf(SendStep("MAC 格式错误", false, mac))
            ) to null
        }
        val packet = magicPacket(macBytes)
        var socket: DatagramSocket? = null

        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = BIND_TIMEOUT_MS
            }
            val address = InetAddress.getByName(ip)
            val unicast = DatagramPacket(packet, packet.size, address, port)
            socket.send(unicast)
            steps += SendStep("单播 WOL", true, "$ip:$port · ${packet.size} 字节")

            if (alsoBroadcast) {
                // 广播帧在以太网层的目的 MAC 天然就是 FF:FF:FF:FF:FF:FF，
                // 这里把 WOL 报文再发一遍到 255.255.255.255。
                runCatching {
                    val bAddr = InetAddress.getByName(BROADCAST_ADDR)
                    socket.send(DatagramPacket(packet, packet.size, bAddr, port))
                    steps += SendStep("广播 WOL", true, "$BROADCAST_ADDR:$port")
                }.onFailure {
                    steps += SendStep("广播 WOL", false, it.message ?: "发送失败")
                }
            }
        } catch (e: Exception) {
            steps += SendStep("单播 WOL", false, e.message ?: e.javaClass.simpleName)
        }
        SendReport(steps, packet) to socket
    }
}

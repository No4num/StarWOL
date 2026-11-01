package com.starchen.wol.wol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 从内核 ARP 表里找某个 IP 对应的 MAC。
 *
 * 原理很简单：同网段的设备要通信，内核就会在 /proc/net/arp 里记下它的 MAC。
 * 所以顺序是「先查表，查不到就 ping 一下逼内核发 ARP 请求，再查」。
 *
 * 注意：部分 Android 10+ 设备把 /proc/net/arp 也收紧了，读不到就返回 null，
 * 这时候只能手填。不是所有机器都能用。
 */
object ArpLookup {

    /** 只查表，不主动触发。返回规范化 MAC（AA:BB:CC:DD:EE:FF），没记录返回 null。 */
    fun fromProc(ip: String): String? = runCatching {
        val file = File("/proc/net/arp")
        if (!file.canRead()) return@runCatching null
        file.readLines().drop(1).firstNotNullOfOrNull { line ->
            val parts = line.trim().split(' ').filter { it.isNotEmpty() }
            if (parts.size >= 4 && parts[0] == ip) normalize(parts[3]) else null
        }
    }.getOrNull()

    /** 先查表；没有就 ping 一次触发 ARP，再轮询最多 [timeoutMs]。 */
    suspend fun resolve(ip: String, timeoutMs: Long = 4000): String? = withContext(Dispatchers.IO) {
        fromProc(ip)?.let { return@withContext it }

        runCatching {
            val p = ProcessBuilder("ping", "-c", "1", "-W", "1", ip)
                .redirectErrorStream(true)
                .start()
            p.waitFor(1500, TimeUnit.MILLISECONDS)
            p.destroy()
        }

        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            fromProc(ip)?.let { return@withContext it }
            delay(200)
        }
        null
    }

    /** 系统能不能读 ARP 表，用来给用户一句人话。 */
    fun readable(): Boolean = runCatching { File("/proc/net/arp").canRead() }.getOrDefault(false)

    private fun normalize(raw: String): String? {
        if (raw == "00:00:00:00:00:00") return null
        val hex = raw.lowercase().filter { it in '0'..'9' || it in 'a'..'f' }
        if (hex.length != 12) return null
        return hex.chunked(2).joinToString(":") { it.uppercase() }
    }
}

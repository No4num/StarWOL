package com.starchen.wol.wol

/**
 * MAC 地址处理。
 *
 * 能识别这些写法：
 *   AABB-CCDD-EEFF
 *   AA-BB-CC-DD-EE-FF
 *   AABB:CCDD:EEFF
 *   AA:BB:CC:DD:EE:FF
 *   AABBCCDDEEFF
 * 统一输出：AA:BB:CC:DD:EE:FF
 */
object MacUtils {

    private val NORMALIZED = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")
    private val ALLOWED = "0123456789abcdefABCDEF:-. ".toSet()

    /** 输入是否已经是标准写法 */
    fun isNormalized(input: String): Boolean = NORMALIZED.matches(input.trim())

    /** 任意合法写法 → 标准写法；非法返回 null */
    fun normalize(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        // 出现十六进制和分隔符以外的字符，直接判非法，避免 "FFGG" 被悄悄吃掉
        if (text.any { it !in ALLOWED }) return null
        val hex = text.filter { it.isHexDigit() }.uppercase()
        if (hex.length != 12) return null
        return hex.chunked(2).joinToString(":")
    }

    /** 把标准写法转成 6 字节 */
    fun toBytes(mac: String): ByteArray? {
        val normalized = normalize(mac) ?: return null
        return normalized.split(":").map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    /** 常见的局域网广播末端（给提示用） */
    fun describe(ip: String): String = if (ip.count { it == '.' } == 3) "IPv4" else "地址"
}

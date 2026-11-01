package com.starchen.wol.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用日志。
 *
 * 内存里留最近 N 条给设置页看，同时按需落到 filesDir/logs/ 下的文件。
 * 写文件的开关由 AppSettings.writeLogs 控制，界面显示的开关由 showLogs 控制。
 */
object LogStore {

    private const val MAX_MEMORY = 300
    private const val MAX_FILE_BYTES = 512 * 1024L
    private const val LOG_DIR = "logs"
    private const val LOG_NAME = "starwol.log"

    enum class Level { INFO, WARN, ERROR }

    data class Entry(
        val time: Long,
        val level: Level,
        val tag: String,
        val message: String
    ) {
        fun format(): String {
            val t = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(time))
            return "$t ${level.name}/$tag: $message"
        }

        fun formatFull(): String {
            val t = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(time))
            return "$t ${level.name}/$tag: $message"
        }
    }

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    /** 由 ViewModel 根据设置同步过来 */
    @Volatile
    var writeToFile: Boolean = true

    @Volatile
    var dirty: Boolean = false

    private val ioLock = Any()

    fun append(context: Context?, level: Level, tag: String, message: String) {
        val entry = Entry(System.currentTimeMillis(), level, tag, message)
        val current = _entries.value
        val next = if (current.size >= MAX_MEMORY) {
            current.subList(current.size - MAX_MEMORY + 1, current.size) + entry
        } else {
            current + entry
        }
        _entries.value = next

        if (writeToFile && context != null) {
            runCatching { writeLine(context, entry.formatFull()) }
        }
    }

    fun info(context: Context?, tag: String, message: String) = append(context, Level.INFO, tag, message)
    fun warn(context: Context?, tag: String, message: String) = append(context, Level.WARN, tag, message)
    fun error(context: Context?, tag: String, message: String) = append(context, Level.ERROR, tag, message)

    private fun writeLine(context: Context, line: String) {
        synchronized(ioLock) {
            val dir = File(context.filesDir, LOG_DIR).apply { mkdirs() }
            val file = File(dir, LOG_NAME)
            if (file.exists() && file.length() > MAX_FILE_BYTES) {
                // 简单滚动：旧文件改名留一份
                val old = File(dir, "$LOG_NAME.1")
                if (old.exists()) old.delete()
                file.renameTo(old)
            }
            file.appendText(line + "\n")
        }
    }

    fun logFile(context: Context): File {
        val dir = File(context.filesDir, LOG_DIR).apply { mkdirs() }
        return File(dir, LOG_NAME)
    }

    fun hasLogFile(context: Context): Boolean = logFile(context).let { it.exists() && it.length() > 0 }

    fun readFile(context: Context): String =
        runCatching { logFile(context).takeIf { it.exists() }?.readText() ?: "" }.getOrDefault("")

    fun clear(context: Context) {
        _entries.value = emptyList()
        runCatching {
            val dir = File(context.filesDir, LOG_DIR)
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    /** 追加一条会话头，方便导出后对照 */
    fun markSession(context: Context, note: String) {
        info(context, "APP", "— $note —")
    }
}

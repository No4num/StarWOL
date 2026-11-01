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
 *
 * 文件一共保留 5 份：`starwol.log` 是正在写的，`starwol.1.log` ~ `starwol.4.log`
 * 是历史。当前文件写满 512KB 就整体后移一位，最老的一份被删掉。
 * 设置页可以切换查看任意一份，切换不影响当前日志继续记录。
 *
 * 写文件的开关由 AppSettings.writeLogs 控制，界面显示的开关由 showLogs 控制。
 */
object LogStore {

    private const val MAX_MEMORY = 300
    private const val MAX_FILE_BYTES = 512 * 1024L
    private const val LOG_DIR = "logs"
    private const val LOG_NAME = "starwol.log"

    /** 历史份数（不含当前），加上当前一共 5 份 */
    const val KEEP_ARCHIVES = 4

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

    /** 一份已经轮转出去的历史日志。index 从 1 开始，1 是最新的一份。 */
    data class Archive(val index: Int, val time: Long) {
        fun label(): String {
            val t = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(time))
            return "前${index}个_$t"
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
            if (file.exists() && file.length() > MAX_FILE_BYTES) rotate(dir, file)
            file.appendText(line + "\n")
        }
    }

    /** 当前 → .1 → .2 → .3 → .4，最后一份直接丢掉 */
    private fun rotate(dir: File, current: File) {
        File(dir, fileNameFor(KEEP_ARCHIVES)).delete()
        for (i in KEEP_ARCHIVES - 1 downTo 1) {
            val from = File(dir, fileNameFor(i))
            if (from.exists()) from.renameTo(File(dir, fileNameFor(i + 1)))
        }
        current.renameTo(File(dir, fileNameFor(1)))
    }

    private fun fileNameFor(index: Int): String =
        if (index == 0) LOG_NAME else "starwol.$index.log"

    /** 当前日志文件（index = 0）或某一份历史文件 */
    fun fileFor(context: Context, index: Int = 0): File {
        val dir = File(context.filesDir, LOG_DIR).apply { mkdirs() }
        return File(dir, fileNameFor(index))
    }

    /** 已经存在的历史日志，1 最新 */
    fun archives(context: Context): List<Archive> {
        val dir = File(context.filesDir, LOG_DIR)
        return (1..KEEP_ARCHIVES).mapNotNull { i ->
            val f = File(dir, fileNameFor(i))
            if (f.exists() && f.length() > 0) Archive(i, f.lastModified()) else null
        }
    }

    fun logFile(context: Context): File = fileFor(context, 0)

    fun hasLogFile(context: Context): Boolean = logFile(context).let { it.exists() && it.length() > 0 }

    fun readFile(context: Context): String =
        runCatching { logFile(context).takeIf { it.exists() }?.readText() ?: "" }.getOrDefault("")

    /** 读某一份历史日志并解析成条目；解析不出来的行直接跳过 */
    fun readArchive(context: Context, index: Int): List<Entry> {
        val file = fileFor(context, index)
        if (!file.exists()) return emptyList()
        return runCatching { parse(file) }.getOrDefault(emptyList())
    }

    private fun parse(file: File): List<Entry> {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        return file.readLines().mapNotNull { line ->
            val first = line.indexOf(' ')
            if (first <= 0) return@mapNotNull null
            val second = line.indexOf(' ', first + 1)
            if (second <= 0) return@mapNotNull null
            val time = runCatching { fmt.parse(line.substring(0, second))?.time }.getOrNull()
                ?: return@mapNotNull null
            val rest = line.substring(second + 1)
            val slash = rest.indexOf('/')
            val colon = rest.indexOf(": ")
            if (slash <= 0 || colon < slash) return@mapNotNull null
            val level = runCatching { Level.valueOf(rest.substring(0, slash)) }.getOrDefault(Level.INFO)
            Entry(time, level, rest.substring(slash + 1, colon), rest.substring(colon + 2))
        }
    }

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

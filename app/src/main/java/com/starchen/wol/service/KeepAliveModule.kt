package com.starchen.wol.service

import android.content.Context
import com.starchen.wol.MainActivity
import com.starchen.wol.util.LogStore
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 生成一个 Magisk 模块 zip。
 *
 * 这是"应用被杀也能活"唯一真正可靠的手段：守护脚本跑在 root 侧，
 * 完全不受应用进程生命周期影响，应用死多少次它就拉多少次。
 * 其他所有办法（前台服务、闹钟、无障碍）都依赖应用自己还活着。
 *
 * 保守起见：只生成 zip 让用户自己在 Magisk 里安装，不往 /data/adb 写任何东西。
 */
object KeepAliveModule {

    const val MODULE_ID = "starwol_keepalive"

    fun build(context: Context): File? = runCatching {
        val pkg = context.packageName
        val serviceClass = AutoWakeService::class.java.name
        val activityClass = MainActivity::class.java.name

        val dir = File(context.filesDir, "modules").apply { mkdirs() }
        val out = File(dir, "$MODULE_ID.zip")
        if (out.exists()) out.delete()

        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("module.prop"))
            zip.write(moduleProp().toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("service.sh"))
            zip.write(serviceScript(pkg, serviceClass, activityClass).toByteArray())
            zip.closeEntry()
        }
        LogStore.info(context, "ROOT", "已生成守护模块 ${out.name}（${out.length() / 1024} KB）")
        out
    }.onFailure {
        LogStore.error(context, "ROOT", "生成守护模块失败：${it.message}")
    }.getOrNull()

    private fun moduleProp(): String = """
        id=$MODULE_ID
        name=StarWOL 保活守护
        version=v1.0
        versionCode=1
        author=No4num
        description=开机后用 root 侧守护进程定期检查 StarWOL，进程不在就把它拉起来。卸载模块即完全移除。
    """.trimIndent()

    private fun serviceScript(pkg: String, serviceClass: String, activityClass: String): String = """
        #!/system/bin/sh
        #
        # StarWOL 保活守护（Magisk 开机脚本）
        # 每 120 秒检查一次应用进程，不在就拉起前台服务；再不在就拉起界面。
        # 只做 pidof + am，不监听、不联网、不碰其他应用。
        # 想停掉：Magisk 里禁用/卸载本模块，或删除 /data/adb/modules/$MODULE_ID

        (
          until [ "${'$'}(getprop sys.boot_completed)" = "1" ]; do sleep 5; done
          sleep 30
          while true; do
            sleep 120
            if ! pidof $pkg >/dev/null 2>&1; then
              am start-foreground-service -n $pkg/$serviceClass --user 0 >/dev/null 2>&1
            fi
            sleep 3
            if ! pidof $pkg >/dev/null 2>&1; then
              am start -n $pkg/$activityClass --user 0 >/dev/null 2>&1
            fi
          done
        ) &
    """.trimIndent()
}

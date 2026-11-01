package com.starchen.wol

import android.app.Application
import android.util.Log
import com.starchen.wol.service.AutoWakeService
import com.starchen.wol.service.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class StarWolApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 冷启动时把已有设备的定时任务重新挂上，防止被系统清掉。
        // 放后台读，别在主线程里等 DataStore。
        scope.launch {
            delay(300)
            runCatching {
                Scheduler.scheduleAll(this@StarWolApp)
                AutoWakeService.start(this@StarWolApp)
            }.onFailure { Log.e("StarWol.App", "初始化失败: ${it.message}") }
        }
    }
}

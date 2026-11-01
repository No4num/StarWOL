package com.starchen.wol.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.starchen.wol.util.LogStore

/**
 * 系统级兜底：15 分钟一次的持久化任务。
 *
 * 应用进程被系统回收后，到点系统会重新拉起进程执行 onStartJob，
 * 这是不依赖任何保活技巧、也不消耗额外电量的正统做法。
 */
class KeepAliveJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        LogStore.info(this, "SERVICE", "系统任务调度触发，检查保活服务")
        runCatching { AutoWakeService.start(this) }
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = true

    companion object {
        private const val JOB_ID = 7001

        fun schedule(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java) ?: return
            if (runCatching { js.getPendingJob(JOB_ID) }.getOrNull() != null) return
            val info = JobInfo.Builder(
                JOB_ID,
                ComponentName(context, KeepAliveJobService::class.java)
            )
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .build()
            runCatching { js.schedule(info) }
                .onFailure { LogStore.warn(context, "SERVICE", "系统兜底任务注册失败：${it.message}") }
        }
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ninjin0802.keyboard.app.clipboard

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import java.util.concurrent.Executors

/** Daily best-effort cleanup, including when the keyboard has not been opened recently. */
class ClipboardCleanupJob : JobService() {
    private val executor = Executors.newSingleThreadExecutor()
    override fun onStartJob(params: JobParameters): Boolean {
        executor.execute {
            runCatching { ClipboardHistoryStore(applicationContext).prune() }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val JOB_ID = 0x4D434C50
        fun ensureScheduled(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (scheduler.getPendingJob(JOB_ID) != null) return
            val info = JobInfo.Builder(JOB_ID, ComponentName(context, ClipboardCleanupJob::class.java))
                .setPeriodic(24L * 60L * 60L * 1000L)
                .setPersisted(true)
                .build()
            scheduler.schedule(info)
        }
    }
}

package com.senpaiscanner.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.senpaiscanner.data.SettingsRepository
import com.senpaiscanner.model.ProbeMode
import com.senpaiscanner.model.ScanConfig
import com.senpaiscanner.service.ScanForegroundService
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class ScheduledScanWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val form = SettingsRepository.scanForm.first()
        val app = SettingsRepository.appSettings.first()
        val cfg = ScanConfig(
            count = form.count,
            concurrency = form.concurrency,
            timeoutMs = form.timeoutSec * 1000L,
            port = form.port,
            mode = form.mode,
            cidr = form.cidr,
            sni = app.probeSni,
            wsPath = app.probePath,
            healthyOnly = true,
            useIpv6 = app.useIpv6,
            stopAfterHealthy = app.stopAfterHealthy,
            skipKnownFailed = app.skipKnownFailed,
            maxResults = app.maxResults
        )
        ScanForegroundService.start(applicationContext, cfg)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "scheduled_cf_scan"

        fun schedule(context: Context, intervalHours: Int) {
            val request = PeriodicWorkRequestBuilder<ScheduledScanWorker>(
                intervalHours.toLong().coerceAtLeast(1),
                TimeUnit.HOURS
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

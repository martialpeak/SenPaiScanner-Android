package com.senpaiscanner.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.senpaiscanner.R
import com.senpaiscanner.model.ProbeMode
import com.senpaiscanner.model.ScanConfig
import com.senpaiscanner.scanner.ScanRepository
import com.senpaiscanner.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ScanForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val engine get() = ScanRepository.engine

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                engine.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val cfg = intent.toScanConfig() ?: return START_NOT_STICKY
                createChannel()
                startForeground(NOTIFICATION_ID, buildNotification(0, 0, cfg.count))
                engine.cancel()
                scope.launch {
                    launch {
                        engine.stats.collectLatest { stats ->
                            val notification = buildNotification(
                                stats.tested,
                                stats.healthy,
                                stats.totalTargets.coerceAtLeast(cfg.count)
                            )
                            val nm = getSystemService(NotificationManager::class.java)
                            nm.notify(NOTIFICATION_ID, notification)
                        }
                    }
                    engine.start(cfg, scope)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(tested: Int, healthy: Int, total: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, ScanForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle(getString(R.string.notification_scan_title))
            .setContentText(getString(R.string.notification_scan_text, tested, total, healthy))
            .setContentIntent(open)
            .addAction(0, getString(R.string.btn_stop), stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "scan"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.senpaiscanner.START_SCAN"
        const val ACTION_STOP = "com.senpaiscanner.STOP_SCAN"

        fun start(context: Context, cfg: ScanConfig) {
            val intent = Intent(context, ScanForegroundService::class.java).apply {
                action = ACTION_START
                putScanConfig(cfg)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ScanForegroundService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}

private fun Intent.putScanConfig(cfg: ScanConfig) {
    putExtra("count", cfg.count)
    putExtra("concurrency", cfg.concurrency)
    putExtra("timeoutMs", cfg.timeoutMs)
    putExtra("tries", cfg.tries)
    putExtra("port", cfg.port)
    putExtra("mode", cfg.mode.name)
    putExtra("cidr", cfg.cidr)
    putExtra("sni", cfg.sni)
    putExtra("wsPath", cfg.wsPath)
    putExtra("healthyOnly", cfg.healthyOnly)
    putExtra("useIpv6", cfg.useIpv6)
    putExtra("stopAfterHealthy", cfg.stopAfterHealthy)
    putExtra("skipKnownFailed", cfg.skipKnownFailed)
    putExtra("maxResults", cfg.maxResults)
}

private fun Intent.toScanConfig(): ScanConfig? = try {
    ScanConfig(
        count = getIntExtra("count", 500),
        concurrency = getIntExtra("concurrency", 50),
        timeoutMs = getLongExtra("timeoutMs", 5000L),
        tries = getIntExtra("tries", 3),
        port = getIntExtra("port", 443),
        mode = ProbeMode.valueOf(getStringExtra("mode") ?: "HTTP"),
        cidr = getStringExtra("cidr") ?: "",
        sni = getStringExtra("sni") ?: "speed.cloudflare.com",
        wsPath = getStringExtra("wsPath") ?: "/cdn-cgi/trace",
        healthyOnly = getBooleanExtra("healthyOnly", false),
        useIpv6 = getBooleanExtra("useIpv6", false),
        stopAfterHealthy = getIntExtra("stopAfterHealthy", 0),
        skipKnownFailed = getBooleanExtra("skipKnownFailed", false),
        maxResults = getIntExtra("maxResults", 500)
    )
} catch (_: Exception) {
    null
}

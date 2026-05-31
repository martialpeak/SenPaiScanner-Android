package com.senpaiscanner.model

data class ScanResult(
    val ip: String,
    val port: Int,
    val latencyMs: Long,
    val loss: Float,
    val tlsOk: Boolean,
    val wsOk: Boolean,
    val httpStatus: Int,
    val colo: String,
    val throughputKbps: Double,
    val probeMode: ProbeMode = ProbeMode.HTTP
) {
    val isHealthy: Boolean
        get() {
            if (latencyMs <= 0 || loss >= 100f) return false
            return when (probeMode) {
                ProbeMode.TCP -> true
                ProbeMode.TLS -> tlsOk
                ProbeMode.HTTP -> httpResponseAlive(httpStatus)
            }
        }

    val endpoint: String get() = "$ip:$port"

    companion object {
        fun httpResponseAlive(code: Int): Boolean = when (code) {
            in 200..299, 101 -> true
            301, 302, 307, 308, 400, 403, 404, 421 -> true
            in 520..530 -> true
            else -> false
        }
    }

    val qualityLabel: String
        get() = when {
            !isHealthy -> "✗"
            latencyMs < 80 -> "★★★"
            latencyMs < 200 -> "★★☆"
            else -> "★☆☆"
        }

    val latencyLabel: String
        get() = if (latencyMs > 0) "${latencyMs}ms" else "—"

    val lossLabel: String
        get() = if (loss == 0f) "0%" else "${"%.0f".format(loss)}%"
}

data class ScanConfig(
    val count: Int = 500,
    val concurrency: Int = 50,
    val timeoutMs: Long = 5000,
    val tries: Int = 3,
    val port: Int = 443,
    val mode: ProbeMode = ProbeMode.HTTP,
    val cidr: String = "",
    val sni: String = "speed.cloudflare.com",
    val wsPath: String = "/cdn-cgi/trace",
    val healthyOnly: Boolean = false,
    val useIpv6: Boolean = false,
    val stopAfterHealthy: Int = 0,
    val skipKnownFailed: Boolean = false,
    val maxResults: Int = 500
)

enum class ProbeMode { TCP, TLS, HTTP }

data class ScanStats(
    val tested: Int = 0,
    val healthy: Int = 0,
    val failed: Int = 0,
    val inFlight: Int = 0,
    val totalTargets: Int = 0,
    val elapsedMs: Long = 0,
    val etaSeconds: Int = 0,
    val ipsPerSecond: Float = 0f
)

enum class ScanPreset(
    val count: Int,
    val concurrency: Int,
    val timeoutSec: Int,
    val mode: ProbeMode
) {
    QUICK(200, 80, 3, ProbeMode.TCP),
    NORMAL(500, 50, 5, ProbeMode.HTTP),
    DEEP(2000, 50, 10, ProbeMode.HTTP)
}

data class AppSettings(
    val probeSni: String = "speed.cloudflare.com",
    val probePath: String = "/cdn-cgi/trace",
    val maxResults: Int = 500,
    val stopAfterHealthy: Int = 20,
    val skipKnownFailed: Boolean = true,
    val vibrateOnHealthy: Boolean = false,
    val useForegroundService: Boolean = true,
    val useIpv6: Boolean = false,
    val scheduledScanEnabled: Boolean = false,
    val scheduledIntervalHours: Int = 24
)

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
    val probeMode: ProbeMode = ProbeMode.HTTP   // ← track which mode was used
) {
    /**
     * Fix: isHealthy was always false in TCP mode because tlsOk is never set.
     * Now mode-aware.
     */
    val isHealthy: Boolean
        get() {
            if (latencyMs <= 0 || loss > 20f) return false
            return when (probeMode) {
                ProbeMode.TCP  -> true   // TCP: connect succeeded = healthy
                ProbeMode.TLS  -> tlsOk  // TLS: handshake must succeed
                ProbeMode.HTTP -> tlsOk && (httpStatus in 200..299 || httpStatus == 101)
            }
        }

    val qualityLabel: String
        get() = when {
            !isHealthy     -> "✗"
            latencyMs < 80  -> "★★★"
            latencyMs < 200 -> "★★☆"
            else            -> "★☆☆"
        }

    val latencyLabel: String
        get() = if (latencyMs > 0) "${latencyMs}ms" else "—"

    val lossLabel: String
        get() = if (loss == 0f) "0%" else "${"%.0f".format(loss)}%"

    val speedLabel: String
        get() = when {
            throughputKbps <= 0    -> "—"
            throughputKbps >= 1024 -> "${"%.1f".format(throughputKbps / 1024)} MB/s"
            else                   -> "${"%.0f".format(throughputKbps)} KB/s"
        }
}

data class ScanConfig(
    val count: Int        = 500,
    val concurrency: Int  = 50,
    val timeoutMs: Long   = 5000,
    val tries: Int        = 3,
    val port: Int         = 443,
    val mode: ProbeMode   = ProbeMode.HTTP,
    val useV4: Boolean    = true,
    val useV6: Boolean    = false,
    val cidr: String      = "",
    val configUrl: String = "",
    val sni: String       = "speed.cloudflare.com",
    val wsPath: String    = "/cdn-cgi/trace",
    val transport: String = "tcp",
    // NEW: filter — only emit healthy results to UI
    val healthyOnly: Boolean = false
)

enum class ProbeMode { TCP, TLS, HTTP }

data class ScanStats(
    val tested: Int   = 0,
    val healthy: Int  = 0,
    val failed: Int   = 0,
    val inFlight: Int = 0
)

package com.senpaiscanner.model

data class ScanResult(
    val ip: String,
    val port: Int,
    val latencyMs: Long,       // avg latency in ms, 0 = failed
    val loss: Float,           // 0.0 – 100.0
    val tlsOk: Boolean,
    val wsOk: Boolean,
    val httpStatus: Int,       // 0 if not probed
    val colo: String,          // e.g. "THR", "AMS"
    val throughputKbps: Double // 0 if not measured
) {
    val isHealthy: Boolean
        get() = latencyMs > 0 && loss < 100f

    val latencyLabel: String
        get() = if (latencyMs > 0) "${latencyMs}ms" else "—"

    val lossLabel: String
        get() = if (loss == 0f) "0%" else "${"%.0f".format(loss)}%"

    val speedLabel: String
        get() = when {
            throughputKbps <= 0 -> "—"
            throughputKbps >= 1024 -> "${"%.1f".format(throughputKbps / 1024)} MB/s"
            else -> "${"%.0f".format(throughputKbps)} KB/s"
        }
}

data class ScanConfig(
    val count: Int = 500,
    val concurrency: Int = 50,
    val timeoutMs: Long = 5000,
    val tries: Int = 4,
    val port: Int = 443,
    val mode: ProbeMode = ProbeMode.HTTP,
    val useV4: Boolean = true,
    val useV6: Boolean = false,
    val cidr: String = "",
    val configUrl: String = ""
)

enum class ProbeMode { TCP, TLS, HTTP }

data class ScanStats(
    val tested: Int = 0,
    val healthy: Int = 0,
    val failed: Int = 0,
    val inFlight: Int = 0
)

package com.senpaiscanner.scanner

import com.senpaiscanner.model.ProbeMode
import com.senpaiscanner.model.ScanConfig
import com.senpaiscanner.model.ScanResult
import kotlinx.coroutines.delay
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

object Prober {

    // ─── Shared trust-all SSL context ────────────────────────────────────────
    private val trustManager = object : X509TrustManager {
        override fun checkClientTrusted(c: Array<out X509Certificate>?, a: String?) {}
        override fun checkServerTrusted(c: Array<out X509Certificate>?, a: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private val trustAllSsl: SSLContext by lazy {
        SSLContext.getInstance("TLS").also { it.init(null, arrayOf(trustManager), null) }
    }

    /**
     * FIX: Shared OkHttpClient with connection pool.
     * Previously a new client was created per-probe (very expensive).
     * Now one client handles all HTTP probes concurrently.
     */
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(100, 30, TimeUnit.SECONDS))
            .sslSocketFactory(trustAllSsl.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
    }

    // ─── Public entry point ───────────────────────────────────────────────────
    suspend fun probe(ip: String, cfg: ScanConfig): ScanResult {
        val latencies   = mutableListOf<Long>()
        var tlsOk       = false
        var httpStatus  = 0
        var colo        = ""
        var successCount = 0

        repeat(cfg.tries) { i ->
            when (cfg.mode) {
                ProbeMode.TCP -> {
                    val lat = probeTcp(ip, cfg.port, cfg.timeoutMs)
                    if (lat > 0) { latencies += lat; successCount++ }
                }
                ProbeMode.TLS -> {
                    val (lat, ok) = probeTls(ip, cfg.port, cfg.sni, cfg.timeoutMs)
                    if (lat > 0) latencies += lat
                    if (ok) { successCount++; tlsOk = true }
                }
                ProbeMode.HTTP -> {
                    val r = probeHttp(ip, cfg.port, cfg.sni, cfg.wsPath, cfg.timeoutMs)
                    if (r.latencyMs > 0) latencies += r.latencyMs
                    if (r.tlsOk)    tlsOk = true
                    if (r.success)  successCount++
                    if (r.httpStatus != 0) httpStatus = r.httpStatus
                    if (r.colo.isNotEmpty()) colo = r.colo
                }
            }
            // small back-off between retries (skip on last)
            if (i < cfg.tries - 1) delay(if (i == 0) 15L else 30L)
        }

        val loss   = if (cfg.tries == 0) 100f
                     else ((cfg.tries - successCount).toFloat() / cfg.tries) * 100f
        val avgLat = if (latencies.isEmpty()) 0L else latencies.average().toLong()

        return ScanResult(
            ip             = ip,
            port           = cfg.port,
            latencyMs      = avgLat,
            loss           = loss,
            tlsOk          = tlsOk,
            wsOk           = httpStatus == 101,
            httpStatus     = httpStatus,
            colo           = colo,
            throughputKbps = 0.0,
            probeMode      = cfg.mode        // ← pass mode so isHealthy works correctly
        )
    }

    // ─── TCP ──────────────────────────────────────────────────────────────────
    private fun probeTcp(ip: String, port: Int, timeoutMs: Long): Long {
        return try {
            val start = System.currentTimeMillis()
            Socket().use { it.connect(InetSocketAddress(ip, port), timeoutMs.toInt()) }
            System.currentTimeMillis() - start
        } catch (_: Exception) { 0L }
    }

    // ─── TLS ──────────────────────────────────────────────────────────────────
    private fun probeTls(ip: String, port: Int, sni: String, timeoutMs: Long): Pair<Long, Boolean> {
        return try {
            val start = System.currentTimeMillis()
            val sock  = Socket()
            sock.connect(InetSocketAddress(ip, port), timeoutMs.toInt())
            val ssl = trustAllSsl.socketFactory
                .createSocket(sock, sni, port, true) as SSLSocket
            ssl.soTimeout = timeoutMs.toInt()
            ssl.startHandshake()
            val lat = System.currentTimeMillis() - start
            ssl.close()
            Pair(lat, true)
        } catch (_: Exception) { Pair(0L, false) }
    }

    // ─── HTTP ─────────────────────────────────────────────────────────────────
    private data class HttpProbeResult(
        val latencyMs: Long,
        val tlsOk: Boolean,
        val success: Boolean,
        val httpStatus: Int,
        val colo: String
    )

    private fun probeHttp(
        ip: String,
        port: Int,
        sni: String,
        path: String,
        timeoutMs: Long
    ): HttpProbeResult {
        return try {
            val scheme    = if (port == 80 || port == 8080) "http" else "https"
            val probePath = if (path.isNotBlank() && path != "/") path else "/cdn-cgi/trace"
            val authority = when {
                scheme == "https" && port != 443 -> "$sni:$port"
                scheme == "http" && port != 80 -> "$sni:$port"
                else -> sni
            }
            val url       = "$scheme://$authority$probePath"

            // IP-pin: force DNS to resolve to the target IP regardless of SNI
            val fixedDns = object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    listOf(InetAddress.getByName(ip))
            }

            // FIX: build per-probe client with corrected timeouts but reuse
            // the shared connection pool and SSL context
            val client = httpClient.newBuilder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)      // FIX: was timeoutMs/4
                .readTimeout(timeoutMs + 2000, TimeUnit.MILLISECONDS)
                .callTimeout(timeoutMs * 2, TimeUnit.MILLISECONDS)
                .dns(fixedDns)
                .build()

            val req = Request.Builder()
                .url(url)
                .header("Host", sni)
                .header("User-Agent", "SenPaiScanner/2.0")
                .header("Accept", "*/*")
                .build()

            val start = System.currentTimeMillis()
            val resp  = client.newCall(req).execute()
            val lat   = System.currentTimeMillis() - start
            val body  = resp.body?.string() ?: ""

            val colo = if (probePath == "/cdn-cgi/trace") {
                parseColoCdn(body).ifEmpty { parseColoRay(resp.header("CF-Ray") ?: "") }
            } else {
                parseColoRay(resp.header("CF-Ray") ?: "")
            }

            val success = ScanResult.httpResponseAlive(resp.code)
            val tlsHandshakeOk = scheme == "https"
            resp.close()

            HttpProbeResult(lat, tlsHandshakeOk, success, resp.code, colo)
        } catch (_: Exception) {
            HttpProbeResult(0L, false, false, 0, "")
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    private fun parseColoCdn(body: String): String {
        return body.lineSequence()
            .firstOrNull { it.trimEnd('\r').startsWith("colo=") }
            ?.trimEnd('\r')?.removePrefix("colo=")?.trim()
            ?: ""
    }

    private fun parseColoRay(ray: String): String {
        val parts = ray.split("-")
        if (parts.size < 2) return ""
        val colo = parts.last().trim()
        return if (colo.length >= 3) colo.take(3).uppercase() else ""
    }
}

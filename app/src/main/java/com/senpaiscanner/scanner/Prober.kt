package com.senpaiscanner.scanner

import com.senpaiscanner.model.ProbeMode
import com.senpaiscanner.model.ScanResult
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

    // ثابت نگه داشتن SNI برای نتایج قابل اعتماد
    private const val PROBE_SNI = "speed.cloudflare.com"

    private val trustAllSsl: SSLContext by lazy {
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        SSLContext.getInstance("TLS").also { it.init(null, arrayOf(tm), null) }
    }

    fun probe(ip: String, port: Int, mode: ProbeMode, tries: Int, timeoutMs: Long): ScanResult {
        val latencies = mutableListOf<Long>()
        var tlsOk = false
        var httpStatus = 0
        var colo = ""
        var successCount = 0

        for (i in 0 until tries) {
            when (mode) {
                ProbeMode.TCP -> {
                    val lat = probeTcp(ip, port, timeoutMs)
                    if (lat > 0) { latencies.add(lat); successCount++ }
                }
                ProbeMode.TLS -> {
                    val (lat, ok) = probeTls(ip, port, PROBE_SNI, timeoutMs)
                    if (lat > 0) latencies.add(lat)
                    if (ok) { successCount++; tlsOk = true }
                }
                ProbeMode.HTTP -> {
                    val r = probeHttp(ip, port, timeoutMs)
                    if (r.latencyMs > 0) latencies.add(r.latencyMs)
                    if (r.tlsOk) tlsOk = true
                    if (r.success) successCount++
                    if (r.httpStatus != 0) httpStatus = r.httpStatus
                    if (r.colo.isNotEmpty()) colo = r.colo
                }
            }
            if (i < tries - 1) Thread.sleep(20)
        }

        val loss = if (tries == 0) 100f else ((tries - successCount).toFloat() / tries) * 100f
        val avgLat = if (latencies.isEmpty()) 0L else latencies.average().toLong()

        return ScanResult(
            ip = ip,
            port = port,
            latencyMs = avgLat,
            loss = loss,
            tlsOk = tlsOk,
            wsOk = false,
            httpStatus = httpStatus,
            colo = colo,
            throughputKbps = 0.0
        )
    }

    private fun probeTcp(ip: String, port: Int, timeoutMs: Long): Long {
        return try {
            val start = System.currentTimeMillis()
            Socket().use { sock ->
                sock.connect(InetSocketAddress(ip, port), timeoutMs.toInt())
            }
            System.currentTimeMillis() - start
        } catch (e: Exception) { 0L }
    }

    private fun probeTls(ip: String, port: Int, sni: String, timeoutMs: Long): Pair<Long, Boolean> {
        return try {
            val start = System.currentTimeMillis()
            val sock = Socket()
            sock.connect(InetSocketAddress(ip, port), timeoutMs.toInt())
            val ssl = trustAllSsl.socketFactory.createSocket(sock, sni, port, true) as SSLSocket
            ssl.soTimeout = timeoutMs.toInt()
            ssl.startHandshake()
            val lat = System.currentTimeMillis() - start
            ssl.close()
            Pair(lat, true)
        } catch (e: Exception) { Pair(0L, false) }
    }

    private data class HttpProbeResult(
        val latencyMs: Long,
        val tlsOk: Boolean,
        val success: Boolean,
        val httpStatus: Int,
        val colo: String
    )

    private fun probeHttp(ip: String, port: Int, timeoutMs: Long): HttpProbeResult {
        return try {
            val scheme = if (port == 80) "http" else "https"
            val url = "$scheme://$PROBE_SNI/cdn-cgi/trace"

            val fixedDns = object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    return listOf(InetAddress.getByName(ip))
                }
            }

            val trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(timeoutMs / 4, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .sslSocketFactory(trustAllSsl.socketFactory, trustManager)
                .hostnameVerifier { _, _ -> true }
                .dns(fixedDns)
                .build()

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "senpaiscanner/1.0")
                .build()

            val start = System.currentTimeMillis()
            val resp = client.newCall(req).execute()
            val lat = System.currentTimeMillis() - start

            val body = resp.body?.string() ?: ""
            // IP باید واقعاً Cloudflare باشه — اگه colo خالیه، IP جعلیه
            val colo = parseColoCdn(body).ifEmpty {
                parseColoRay(resp.header("CF-Ray") ?: "")
            }

            // IP فقط اگه colo داشته باشه واقعاً Cloudflare است
            val isRealCf = colo.isNotEmpty()
            val success = resp.code == 200 && isRealCf

            HttpProbeResult(lat, true, success, resp.code, colo)
        } catch (e: Exception) {
            HttpProbeResult(0L, false, false, 0, "")
        }
    }

    private fun parseColoCdn(body: String): String {
        for (line in body.lines()) {
            if (line.trimEnd('\r').startsWith("colo=")) {
                return line.trimEnd('\r').removePrefix("colo=").trim()
            }
        }
        return ""
    }

    private fun parseColoRay(ray: String): String {
        val parts = ray.split("-")
        if (parts.size < 2) return ""
        val colo = parts.last().trim()
        return if (colo.length >= 3) colo.take(3).uppercase() else ""
    }
}

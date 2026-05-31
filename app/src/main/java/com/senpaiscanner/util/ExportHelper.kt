package com.senpaiscanner.util

import com.senpaiscanner.model.ScanResult

object ExportHelper {

    fun toPlainLines(results: List<ScanResult>, healthyOnly: Boolean = true): String {
        val list = if (healthyOnly) results.filter { it.isHealthy } else results
        return list.joinToString("\n") { "${it.ip}:${it.port}" }
    }

    fun toCsv(results: List<ScanResult>, healthyOnly: Boolean = true): String {
        val header = "ip,port,latency_ms,loss_pct,colo,http_status,tls_ok"
        val list = if (healthyOnly) results.filter { it.isHealthy } else results
        val rows = list.joinToString("\n") { r ->
            "${r.ip},${r.port},${r.latencyMs},${"%.0f".format(r.loss)},${r.colo},${r.httpStatus},${r.tlsOk}"
        }
        return "$header\n$rows"
    }

    fun toClash(results: List<ScanResult>, sni: String): String =
        results.filter { it.isHealthy }.joinToString("\n") { r ->
            "  - {name: ${r.ip}, server: ${r.ip}, port: ${r.port}, type: http, tls: true, servername: $sni}"
        }.let { if (it.isEmpty()) "" else "proxies:\n$it" }

    fun toSingBox(results: List<ScanResult>, sni: String): String =
        results.filter { it.isHealthy }.joinToString(",\n") { r ->
            """  {"type":"tls","server":"${r.ip}","server_port":${r.port},"tls":{"enabled":true,"server_name":"$sni"}}"""
        }.let { if (it.isEmpty()) "" else "[\n$it\n]" }

    fun toV2rayUri(results: List<ScanResult>): String =
        results.filter { it.isHealthy }.take(20).joinToString("\n") { r ->
            "vless://00000000-0000-0000-0000-000000000000@${r.ip}:${r.port}?security=tls&sni=speed.cloudflare.com#${r.ip}"
        }
}

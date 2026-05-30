package com.senpaiscanner.scanner

import android.net.Uri
import com.senpaiscanner.model.ProbeMode
import com.senpaiscanner.model.ScanConfig

/**
 * Parses a VLESS or Trojan config URL and extracts scan-relevant fields.
 * Mirrors the config URL parsing in internal/ui/cmds.go
 */
object ConfigParser {

    data class ParsedConfig(
        val port: Int,
        val sni: String,
        val mode: ProbeMode,
        val wsPath: String,
        val transport: String // ws, grpc, xhttp, tcp
    )

    fun parse(url: String): ParsedConfig? {
        return try {
            val uri = Uri.parse(url.trim())
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme !in listOf("vless", "trojan", "vmess")) return null

            val port = uri.port.takeIf { it > 0 } ?: 443
            val params = uri.fragment?.let { parseFragment(it) } ?: parseQueryParams(uri)

            val security = params["security"]?.lowercase() ?: "tls"
            val type = params["type"]?.lowercase() ?: "tcp"
            val sni = params["sni"] ?: params["host"] ?: uri.host ?: "cloudflare.com"
            val wsPath = params["path"] ?: "/"

            val mode = when {
                type == "ws" || type == "websocket" -> ProbeMode.HTTP
                security == "tls" || security == "reality" -> ProbeMode.TLS
                else -> ProbeMode.TCP
            }

            ParsedConfig(port, sni, mode, wsPath, type)
        } catch (e: Exception) { null }
    }

    fun toScanConfig(url: String, base: ScanConfig = ScanConfig()): ScanConfig {
        val parsed = parse(url) ?: return base
        return base.copy(
            port = parsed.port,
            mode = parsed.mode,
            configUrl = url
        )
    }

    private fun parseFragment(fragment: String): Map<String, String> {
        // Some clients encode params in the fragment
        return fragment.split("&").mapNotNull {
            val kv = it.split("=", limit = 2)
            if (kv.size == 2) kv[0] to Uri.decode(kv[1]) else null
        }.toMap()
    }

    private fun parseQueryParams(uri: Uri): Map<String, String> {
        return uri.queryParameterNames.associateWith { key ->
            uri.getQueryParameter(key) ?: ""
        }
    }
}

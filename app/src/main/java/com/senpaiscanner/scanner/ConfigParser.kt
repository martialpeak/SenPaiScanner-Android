package com.senpaiscanner.scanner

import android.net.Uri
import com.senpaiscanner.model.ProbeMode
import com.senpaiscanner.model.ScanConfig

object ConfigParser {

    data class ParsedConfig(
        val port: Int,
        val sni: String,
        val mode: ProbeMode,
        val wsPath: String,
        val transport: String
    )

    private val SUPPORTED_SCHEMES = setOf("vless", "trojan", "vmess", "ss")

    fun parse(url: String): ParsedConfig? {
        return try {
            val uri    = Uri.parse(url.trim())
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme !in SUPPORTED_SCHEMES) return null

            val port   = uri.port.takeIf { it > 0 } ?: 443
            val params = uri.fragment?.let { parseFragment(it) } ?: parseQueryParams(uri)

            val security  = params["security"]?.lowercase() ?: "tls"
            val type      = params["type"]?.lowercase() ?: "tcp"
            val sni       = params["sni"] ?: params["host"] ?: params["peer"]
                            ?: uri.host ?: "speed.cloudflare.com"
            val wsPath    = params["path"]?.let { Uri.decode(it) } ?: "/cdn-cgi/trace"

            val mode = when {
                type == "ws" || type == "websocket"            -> ProbeMode.HTTP
                type == "grpc"                                  -> ProbeMode.TLS
                security == "tls" || security == "reality"     -> ProbeMode.TLS
                security == "none"                              -> ProbeMode.TCP
                else                                            -> ProbeMode.HTTP
            }

            ParsedConfig(port, sni, mode, wsPath, type)
        } catch (_: Exception) { null }
    }

    fun toScanConfig(url: String, base: ScanConfig = ScanConfig()): ScanConfig {
        val parsed = parse(url) ?: return base
        return base.copy(
            port      = parsed.port,
            mode      = parsed.mode,
            sni       = parsed.sni,
            wsPath    = parsed.wsPath,
            transport = parsed.transport,
            configUrl = url
        )
    }

    private fun parseFragment(fragment: String): Map<String, String> =
        fragment.split("&").mapNotNull {
            val kv = it.split("=", limit = 2)
            if (kv.size == 2) kv[0] to Uri.decode(kv[1]) else null
        }.toMap()

    private fun parseQueryParams(uri: Uri): Map<String, String> =
        uri.queryParameterNames.associateWith { key ->
            uri.getQueryParameter(key) ?: ""
        }
}

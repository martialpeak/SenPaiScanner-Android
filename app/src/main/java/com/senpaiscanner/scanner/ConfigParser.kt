package com.senpaiscanner.scanner

import android.net.Uri
import android.util.Base64
import com.senpaiscanner.model.ProbeMode
import com.senpaiscanner.model.ScanConfig
import org.json.JSONObject

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
        val trimmed = url.trim()
        return when {
            trimmed.lowercase().startsWith("vmess://") -> parseVmess(trimmed)
            else -> parseUri(trimmed)
        }
    }

    fun toScanConfig(url: String, base: ScanConfig = ScanConfig()): ScanConfig {
        val parsed = parse(url) ?: return base
        return base.copy(
            port = parsed.port,
            mode = parsed.mode,
            sni = parsed.sni,
            wsPath = parsed.wsPath,
            transport = parsed.transport,
            configUrl = url
        )
    }

    private fun parseUri(url: String): ParsedConfig? {
        return try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme !in SUPPORTED_SCHEMES) return null

            val port = uri.port.takeIf { it > 0 } ?: 443
            val params = uri.fragment?.let { parseFragment(it) } ?: parseQueryParams(uri)

            val security = params["security"]?.lowercase() ?: "tls"
            val type = params["type"]?.lowercase() ?: "tcp"
            val sni = params["sni"] ?: params["host"] ?: params["peer"]
                ?: uri.host ?: "speed.cloudflare.com"
            val wsPath = params["path"]?.let { Uri.decode(it) } ?: "/cdn-cgi/trace"
            val mode = probeModeFor(type, security)

            ParsedConfig(port, sni, mode, wsPath, type)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseVmess(url: String): ParsedConfig? {
        return try {
            val encoded = url.substringAfter("://", missingDelimiterValue = "")
            if (encoded.isBlank()) return null
            val json = decodeVmessPayload(encoded) ?: return null
            val obj = JSONObject(json)

            val port = when (val p = obj.opt("port")) {
                is Number -> p.toInt()
                is String -> p.toIntOrNull() ?: 443
                else -> 443
            }
            val sni = obj.optString("host").ifBlank {
                obj.optString("sni").ifBlank {
                    obj.optString("add", "speed.cloudflare.com")
                }
            }
            val type = obj.optString("net", "tcp").lowercase()
            val tlsFlag = obj.optString("tls", "").lowercase()
            val security = obj.optString("security", "").lowercase().ifBlank {
                if (tlsFlag == "tls") "tls" else "none"
            }
            val wsPath = obj.optString("path", "/cdn-cgi/trace").let {
                if (it.isBlank()) "/cdn-cgi/trace" else Uri.decode(it)
            }
            val mode = probeModeFor(type, security)

            ParsedConfig(port, sni, mode, wsPath, type)
        } catch (_: Exception) {
            null
        }
    }

    private fun probeModeFor(type: String, security: String): ProbeMode = when {
        type == "ws" || type == "websocket" -> ProbeMode.HTTP
        type == "grpc" -> ProbeMode.TLS
        security == "tls" || security == "reality" -> ProbeMode.TLS
        security == "none" -> ProbeMode.TCP
        else -> ProbeMode.HTTP
    }

    private fun decodeVmessPayload(encoded: String): String? {
        val normalized = encoded.trim()
            .replace('-', '+')
            .replace('_', '/')
            .let { pad ->
                when (pad.length % 4) {
                    2 -> "$pad=="
                    3 -> "$pad="
                    else -> pad
                }
            }
        val bytes = try {
            Base64.decode(normalized, Base64.DEFAULT)
        } catch (_: IllegalArgumentException) {
            try {
                Base64.decode(normalized, Base64.URL_SAFE)
            } catch (_: IllegalArgumentException) {
                return null
            }
        }
        return String(bytes, Charsets.UTF_8)
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

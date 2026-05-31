package com.senpaiscanner.scanner

import android.content.Context
import org.json.JSONObject
import java.net.InetAddress
import java.nio.ByteBuffer
import kotlin.random.Random

object IpSource {

    private var v4Ranges: List<CidrRange> = emptyList()
    private var v6Ranges: List<CidrRange> = emptyList()

    private val DEFAULT_V4 = listOf(
        "173.245.48.0/20",
        "103.21.244.0/22",
        "103.22.200.0/22",
        "103.31.4.0/22",
        "141.101.64.0/18",
        "108.162.192.0/18",
        "190.93.240.0/20",
        "188.114.96.0/20",
        "197.234.240.0/22",
        "198.41.128.0/17",
        "162.158.0.0/15",
        "104.16.0.0/13",
        "104.24.0.0/14",
        "172.64.0.0/13",
        "131.0.72.0/22"
    )

    internal data class CidrRange(
        val baseInt: Long,
        val hostBits: Int,
        val isV6: Boolean,
        val baseBytes: ByteArray? = null
    ) {
        val size: Long get() = if (isV6) 1L shl minOf(hostBits, 63) else 1L shl hostBits
    }

    fun init(context: Context) {
        runCatching {
            val json = context.assets.open("cf_ranges.json").bufferedReader().readText()
            val obj = JSONObject(json)
            v4Ranges = jsonArrayToV4(obj.optJSONArray("v4")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: DEFAULT_V4)
            v6Ranges = jsonArrayToV6(obj.optJSONArray("v6")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList())
        }.onFailure {
            v4Ranges = jsonArrayToV4(DEFAULT_V4)
            v6Ranges = emptyList()
        }
    }

    private fun jsonArrayToV4(cidrs: List<String>): List<CidrRange> =
        cidrs.mapNotNull { runCatching { parseCidrV4(it) }.getOrNull() }

    private fun jsonArrayToV6(cidrs: List<String>): List<CidrRange> =
        cidrs.mapNotNull { runCatching { parseCidrV6(it) }.getOrNull() }

    private fun parseCidrV4(cidr: String): CidrRange {
        val (ip, prefix) = cidr.split("/")
        val prefixLen = prefix.toInt()
        val bytes = InetAddress.getByName(ip).address
        val ipInt = ByteBuffer.wrap(bytes).int.toLong() and 0xFFFFFFFFL
        return CidrRange(ipInt, 32 - prefixLen, false)
    }

    private fun parseCidrV6(cidr: String): CidrRange {
        val (ip, prefix) = cidr.split("/")
        val prefixLen = prefix.toInt().coerceIn(0, 128)
        val bytes = InetAddress.getByName(ip).address
        return CidrRange(0, 128 - prefixLen, true, bytes)
    }

    fun parseCidr(cidr: String): CidrRange? =
        runCatching {
            if (cidr.contains(':')) parseCidrV6(cidr.trim()) else parseCidrV4(cidr.trim())
        }.getOrNull()

    fun generateV4(count: Int, extraCidrs: List<String> = emptyList()): List<String> {
        val ranges = if (extraCidrs.isNotEmpty()) {
            extraCidrs.mapNotNull { parseCidr(it) }.filter { !it.isV6 }
        } else {
            v4Ranges.ifEmpty { jsonArrayToV4(DEFAULT_V4) }
        }
        return generateFromRanges(count, ranges) { offset, range ->
            intToIpV4((range.baseInt + offset) and 0xFFFFFFFFL)
        }
    }

    fun generateV6(count: Int, extraCidrs: List<String> = emptyList()): List<String> {
        val ranges = if (extraCidrs.isNotEmpty()) {
            extraCidrs.mapNotNull { parseCidr(it) }.filter { it.isV6 }
        } else {
            v6Ranges
        }
        if (ranges.isEmpty()) return emptyList()
        return generateFromRanges(count, ranges) { offset, range ->
            randomIpv6InRange(range.baseBytes!!, range.hostBits, offset)
        }
    }

    private fun generateFromRanges(
        count: Int,
        ranges: List<CidrRange>,
        formatter: (Long, CidrRange) -> String
    ): List<String> {
        if (ranges.isEmpty()) return emptyList()
        val totalSize = ranges.sumOf { it.size }
        if (totalSize <= 0) return emptyList()
        val result = ArrayList<String>(count)
        repeat(count) {
            var pick = Random.nextLong().and(Long.MAX_VALUE) % totalSize
            val range = ranges.first { r ->
                pick -= r.size
                pick < 0
            }
            val offset = Random.nextLong().and(Long.MAX_VALUE) % range.size.coerceAtLeast(1)
            result.add(formatter(offset, range))
        }
        return result
    }

    private fun intToIpV4(n: Long): String =
        "${(n shr 24) and 0xFF}.${(n shr 16) and 0xFF}.${(n shr 8) and 0xFF}.${n and 0xFF}"

    private fun randomIpv6InRange(base: ByteArray, hostBits: Int, offset: Long): String {
        val bytes = base.copyOf()
        var remaining = offset
        for (i in bytes.indices.reversed()) {
            if (hostBits <= 0) break
            val bits = minOf(8, hostBits)
            val mask = (1 shl bits) - 1
            val add = (remaining and mask.toLong()).toInt()
            bytes[i] = ((bytes[i].toInt() and 0xFF) or add).toByte()
            remaining = remaining shr bits
            hostBits -= bits
        }
        return InetAddress.getByAddress(bytes).hostAddress.trim('[', ']')
    }
}

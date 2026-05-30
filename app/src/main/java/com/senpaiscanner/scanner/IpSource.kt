package com.senpaiscanner.scanner

import java.net.InetAddress
import java.nio.ByteBuffer
import kotlin.random.Random

/**
 * Generates random IPs from Cloudflare's published CIDR ranges.
 * Mirrors the logic in internal/ipsrc/ipsrc.go
 */
object IpSource {

    private val CF_RANGES_V4 = listOf(
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
        val isV6: Boolean
    ) {
        val size: Long get() = 1L shl hostBits
    }

    private val v4Ranges: List<CidrRange> by lazy {
        CF_RANGES_V4.map { parseCidrV4(it) }
    }

    private fun parseCidrV4(cidr: String): CidrRange {
        val (ip, prefix) = cidr.split("/")
        val prefixLen = prefix.toInt()
        val bytes = InetAddress.getByName(ip).address
        val ipInt = ByteBuffer.wrap(bytes).int.toLong() and 0xFFFFFFFFL
        return CidrRange(ipInt, 32 - prefixLen, false)
    }

    /**
     * Parse a custom CIDR string (IPv4 only for now).
     */
    internal fun parseCidr(cidr: String): CidrRange? {
        return try { parseCidrV4(cidr.trim()) } catch (e: Exception) { null }
    }

    /**
     * Generate [count] random Cloudflare IPv4 addresses.
     */
    fun generateV4(count: Int, extraCidrs: List<String> = emptyList()): List<String> {
        val ranges = if (extraCidrs.isNotEmpty()) {
            extraCidrs.mapNotNull { parseCidr(it) }
        } else {
            v4Ranges
        }
        if (ranges.isEmpty()) return emptyList()

        // Weighted random selection proportional to range size
        val totalSize = ranges.sumOf { it.size }
        val result = mutableListOf<String>()
        repeat(count) {
            var pick = (Random.nextLong().and(Long.MAX_VALUE)) % totalSize
            val range = ranges.firstOrNull { r ->
                pick -= r.size
                pick < 0
            } ?: ranges.last()

            val offset = (Random.nextLong().and(Long.MAX_VALUE)) % range.size
            val ipInt = (range.baseInt + offset) and 0xFFFFFFFFL
            result.add(intToIpV4(ipInt))
        }
        return result
    }

    private fun intToIpV4(n: Long): String {
        return "${(n shr 24) and 0xFF}.${(n shr 16) and 0xFF}.${(n shr 8) and 0xFF}.${n and 0xFF}"
    }
}

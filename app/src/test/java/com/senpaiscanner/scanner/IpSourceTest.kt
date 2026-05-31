package com.senpaiscanner.scanner

import org.junit.Assert.*
import org.junit.Test

class IpSourceTest {

    @Test
    fun parseCidrV4Valid() {
        val range = IpSource.parseCidr("104.16.0.0/13")
        assertNotNull(range)
        assertFalse(range!!.isV6)
        assertTrue(range.size > 0)
    }

    @Test
    fun generateV4ReturnsRequestedCount() {
        val ips = IpSource.generateV4(25, emptyList())
        assertEquals(25, ips.size)
        assertTrue(ips.all { it.count { c -> c == '.' } == 3 })
    }

    @Test
    fun invalidCidrReturnsEmptyForCustomOnly() {
        val ips = IpSource.generateV4(10, listOf("not-a-cidr"))
        assertTrue(ips.isEmpty())
    }
}

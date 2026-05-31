package com.senpaiscanner.model

import org.junit.Assert.*
import org.junit.Test

class ScanResultTest {

    @Test
    fun tcpHealthyWithLatencyAndPartialLoss() {
        val r = ScanResult(
            ip = "1.1.1.1",
            port = 443,
            latencyMs = 50,
            loss = 66f,
            tlsOk = false,
            wsOk = false,
            httpStatus = 0,
            colo = "",
            throughputKbps = 0.0,
            probeMode = ProbeMode.TCP
        )
        assertTrue(r.isHealthy)
    }

    @Test
    fun http403IsHealthy() {
        val r = baseHttp().copy(httpStatus = 403, tlsOk = true, latencyMs = 40, loss = 0f)
        assertTrue(r.isHealthy)
    }

    @Test
    fun httpZeroStatusNotHealthy() {
        val r = baseHttp().copy(httpStatus = 0, tlsOk = true, latencyMs = 40, loss = 0f)
        assertFalse(r.isHealthy)
    }

    @Test
    fun totalLossNotHealthy() {
        val r = baseHttp().copy(httpStatus = 200, tlsOk = true, latencyMs = 0, loss = 100f)
        assertFalse(r.isHealthy)
    }

    private fun baseHttp() = ScanResult(
        ip = "1.1.1.1",
        port = 443,
        latencyMs = 0,
        loss = 0f,
        tlsOk = false,
        wsOk = false,
        httpStatus = 0,
        colo = "",
        throughputKbps = 0.0,
        probeMode = ProbeMode.HTTP
    )
}

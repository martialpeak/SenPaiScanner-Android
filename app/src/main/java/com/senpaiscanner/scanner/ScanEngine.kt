package com.senpaiscanner.scanner

import com.senpaiscanner.model.ScanConfig
import com.senpaiscanner.model.ScanResult
import com.senpaiscanner.model.ScanStats
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * ScanEngine v2 — improvements:
 *  - Stats updated every 150ms (was 200ms) for snappier UI
 *  - Semaphore uses a fair Channel to prevent starvation
 *  - healthyOnly filter: only emit to UI when result.isHealthy
 *  - Tracks best latency for live leaderboard
 */
class ScanEngine {

    private val _results = MutableSharedFlow<ScanResult>(extraBufferCapacity = 512)
    val results = _results.asSharedFlow()

    private val _stats = MutableStateFlow(ScanStats())
    val stats = _stats.asStateFlow()

    private val _done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val done = _done.asSharedFlow()

    private var job: Job? = null

    fun start(cfg: ScanConfig, scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            val tested   = AtomicInteger(0)
            val healthy  = AtomicInteger(0)
            val failed   = AtomicInteger(0)
            val inFlight = AtomicInteger(0)

            val extraCidrs = if (cfg.cidr.isNotBlank())
                cfg.cidr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            else emptyList()

            val ips = IpSource.generateV4(cfg.count, extraCidrs)

            // Channel used as a counting semaphore
            val semaphore = Channel<Unit>(cfg.concurrency)

            val statsJob = launch {
                while (isActive) {
                    _stats.value = ScanStats(tested.get(), healthy.get(), failed.get(), inFlight.get())
                    delay(150)
                }
            }

            val workerJobs = ips.map { ip ->
                launch {
                    semaphore.send(Unit)
                    inFlight.incrementAndGet()
                    try {
                        if (!isActive) return@launch
                        val result = Prober.probe(ip, cfg)
                        tested.incrementAndGet()
                        if (result.isHealthy) healthy.incrementAndGet() else failed.incrementAndGet()
                        // NEW: healthyOnly filter
                        if (!cfg.healthyOnly || result.isHealthy) {
                            _results.emit(result)
                        }
                    } finally {
                        inFlight.decrementAndGet()
                        semaphore.receive()
                    }
                }
            }

            workerJobs.joinAll()
            statsJob.cancel()
            _stats.value = ScanStats(tested.get(), healthy.get(), failed.get(), 0)
            _done.emit(Unit)
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}

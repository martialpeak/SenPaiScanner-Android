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
 * Mirrors internal/engine/engine.go — runs concurrent probes and emits results.
 */
class ScanEngine {

    private val _results = MutableSharedFlow<ScanResult>(extraBufferCapacity = 256)
    val results = _results.asSharedFlow()

    private val _stats = MutableStateFlow(ScanStats())
    val stats = _stats.asStateFlow()

    private val _done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val done = _done.asSharedFlow()

    private var job: Job? = null

    fun start(cfg: ScanConfig, scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            val tested = AtomicInteger(0)
            val healthy = AtomicInteger(0)
            val failed = AtomicInteger(0)
            val inFlight = AtomicInteger(0)

            // Generate IPs
            val extraCidrs = if (cfg.cidr.isNotBlank())
                cfg.cidr.split(",").map { it.trim() }
            else emptyList()

            val ips = IpSource.generateV4(cfg.count, extraCidrs)

            val semaphore = Channel<Unit>(cfg.concurrency)

            // Stats updater
            val statsJob = launch {
                while (isActive) {
                    _stats.value = ScanStats(tested.get(), healthy.get(), failed.get(), inFlight.get())
                    delay(200)
                }
            }

            // Worker jobs
            val workerJobs = ips.map { ip ->
                launch {
                    semaphore.send(Unit)
                    inFlight.incrementAndGet()
                    try {
                        if (!isActive) return@launch
                        val result = Prober.probe(ip, cfg.port, cfg.mode, cfg.tries, cfg.timeoutMs)
                        tested.incrementAndGet()
                        if (result.isHealthy) healthy.incrementAndGet() else failed.incrementAndGet()
                        _results.emit(result)
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

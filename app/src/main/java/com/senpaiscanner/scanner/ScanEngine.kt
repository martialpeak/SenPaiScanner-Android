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
 * Fixed worker pool: [concurrency] coroutines drain a shared IP channel
 * (avoids spawning one coroutine per IP — safe for 100k+ targets).
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
            val tested = AtomicInteger(0)
            val healthy = AtomicInteger(0)
            val failed = AtomicInteger(0)
            val inFlight = AtomicInteger(0)

            val extraCidrs = if (cfg.cidr.isNotBlank()) {
                cfg.cidr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                emptyList()
            }

            val ips = IpSource.generateV4(cfg.count, extraCidrs)
            if (ips.isEmpty()) {
                _stats.value = ScanStats(0, 0, 0, 0)
                _done.emit(Unit)
                return@launch
            }
            val workers = cfg.concurrency.coerceIn(1, 500)
            val ipChannel = Channel<String>(capacity = workers * 8)

            val feeder = launch {
                try {
                    for (ip in ips) {
                        ipChannel.send(ip)
                    }
                } finally {
                    ipChannel.close()
                }
            }

            val statsJob = launch {
                while (isActive) {
                    _stats.value = ScanStats(
                        tested.get(),
                        healthy.get(),
                        failed.get(),
                        inFlight.get()
                    )
                    delay(150)
                }
            }

            val workerJobs = List(workers) {
                launch {
                    for (ip in ipChannel) {
                        if (!isActive) break
                        inFlight.incrementAndGet()
                        try {
                            val result = Prober.probe(ip, cfg)
                            tested.incrementAndGet()
                            if (result.isHealthy) {
                                healthy.incrementAndGet()
                            } else {
                                failed.incrementAndGet()
                            }
                            if (!cfg.healthyOnly || result.isHealthy) {
                                _results.emit(result)
                            }
                        } finally {
                            inFlight.decrementAndGet()
                        }
                    }
                }
            }

            workerJobs.joinAll()
            feeder.join()
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

package com.senpaiscanner.scanner

import com.senpaiscanner.data.FailedIpRepository
import com.senpaiscanner.model.ScanConfig
import com.senpaiscanner.model.ScanResult
import com.senpaiscanner.model.ScanStats
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ScanEngine {

    private val _results = MutableSharedFlow<ScanResult>(extraBufferCapacity = 512)
    val results = _results.asSharedFlow()

    private val _stats = MutableStateFlow(ScanStats())
    val stats = _stats.asStateFlow()

    private val _done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val done = _done.asSharedFlow()

    private val _healthyFound = MutableSharedFlow<ScanResult>(extraBufferCapacity = 8)
    val healthyFound = _healthyFound.asSharedFlow()

    private var job: Job? = null
    private val stopEarly = AtomicBoolean(false)

    fun start(cfg: ScanConfig, scope: CoroutineScope) {
        job?.cancel()
        stopEarly.set(false)
        job = scope.launch(Dispatchers.IO) {
            val tested = AtomicInteger(0)
            val healthy = AtomicInteger(0)
            val failed = AtomicInteger(0)
            val inFlight = AtomicInteger(0)
            val startMs = System.currentTimeMillis()

            val extraCidrs = if (cfg.cidr.isNotBlank()) {
                cfg.cidr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } else emptyList()

            val skipSet = if (cfg.skipKnownFailed) FailedIpRepository.getSet() else emptySet()

            val ips = buildList {
                if (cfg.useIpv6) {
                    val half = (cfg.count / 2).coerceAtLeast(1)
                    addAll(IpSource.generateV4(half, extraCidrs))
                    addAll(IpSource.generateV6(cfg.count - half, extraCidrs))
                } else {
                    addAll(IpSource.generateV4(cfg.count, extraCidrs))
                }
            }.filter { it !in skipSet }.distinct()

            val totalTargets = ips.size
            if (totalTargets == 0) {
                _stats.value = ScanStats(totalTargets = 0)
                _done.emit(Unit)
                return@launch
            }

            val workers = cfg.concurrency.coerceIn(1, 500)
            val ipChannel = Channel<String>(capacity = workers * 8)

            val feeder = launch {
                try {
                    for (ip in ips) {
                        if (!isActive || stopEarly.get()) break
                        ipChannel.send(ip)
                    }
                } finally {
                    ipChannel.close()
                }
            }

            val statsJob = launch {
                while (isActive) {
                    val t = tested.get()
                    val elapsed = System.currentTimeMillis() - startMs
                    val rate = if (elapsed > 0) t * 1000f / elapsed else 0f
                    val remaining = (totalTargets - t).coerceAtLeast(0)
                    val eta = if (rate > 0f) (remaining / rate).toInt() else 0
                    _stats.value = ScanStats(
                        tested = t,
                        healthy = healthy.get(),
                        failed = failed.get(),
                        inFlight = inFlight.get(),
                        totalTargets = totalTargets,
                        elapsedMs = elapsed,
                        etaSeconds = eta,
                        ipsPerSecond = rate
                    )
                    delay(150)
                }
            }

            val workerJobs = List(workers) {
                launch {
                    for (ip in ipChannel) {
                        if (!isActive || stopEarly.get()) break
                        inFlight.incrementAndGet()
                        try {
                            val result = Prober.probe(ip, cfg)
                            tested.incrementAndGet()
                            if (result.isHealthy) {
                                healthy.incrementAndGet()
                                _healthyFound.emit(result)
                                if (cfg.stopAfterHealthy > 0 && healthy.get() >= cfg.stopAfterHealthy) {
                                    stopEarly.set(true)
                                }
                            } else {
                                failed.incrementAndGet()
                                if (cfg.skipKnownFailed) FailedIpRepository.record(ip)
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

            val elapsed = System.currentTimeMillis() - startMs
            _stats.value = ScanStats(
                tested = tested.get(),
                healthy = healthy.get(),
                failed = failed.get(),
                inFlight = 0,
                totalTargets = totalTargets,
                elapsedMs = elapsed,
                etaSeconds = 0,
                ipsPerSecond = if (elapsed > 0) tested.get() * 1000f / elapsed else 0f
            )
            _done.emit(Unit)
        }
    }

    fun cancel() {
        stopEarly.set(true)
        job?.cancel()
        job = null
    }
}

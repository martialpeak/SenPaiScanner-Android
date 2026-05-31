package com.senpaiscanner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senpaiscanner.model.ScanConfig
import com.senpaiscanner.model.ScanResult
import com.senpaiscanner.model.ScanStats
import com.senpaiscanner.scanner.ConfigParser
import com.senpaiscanner.scanner.ScanEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainViewModel : ViewModel() {

    private val engine = ScanEngine()

    private val _results = MutableStateFlow<List<ScanResult>>(emptyList())
    val results: StateFlow<List<ScanResult>> = _results

    val stats: StateFlow<ScanStats> = engine.stats

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done

    var config = ScanConfig()

    private val resultsMutex = Mutex()

    private val resultOrder = compareBy<ScanResult>(
        { !it.isHealthy },
        { it.latencyMs.takeIf { l -> l > 0 } ?: Long.MAX_VALUE }
    )

    init {
        viewModelScope.launch {
            engine.results.collect { result -> addResult(result) }
        }
        viewModelScope.launch {
            engine.done.collect {
                _scanning.value = false
                _done.value = true
            }
        }
    }

    private suspend fun addResult(result: ScanResult) {
        resultsMutex.withLock {
            val list = _results.value.toMutableList()
            val insertAt = list.binarySearch { resultOrder.compare(it, result) }
                .let { if (it >= 0) it else -it - 1 }
            list.add(insertAt, result)
            if (list.size > MAX_RESULTS) {
                list.subList(MAX_RESULTS, list.size).clear()
            }
            _results.value = list
        }
    }

    fun startScan(cfg: ScanConfig) {
        _results.value = emptyList()
        _scanning.value = true
        _done.value = false
        config = cfg
        engine.start(cfg, viewModelScope)
    }

    fun stopScan() {
        engine.cancel()
        _scanning.value = false
    }

    fun applyConfigUrl(url: String): ScanConfig =
        ConfigParser.toScanConfig(url, config)

    fun getHealthyIps(): List<String> =
        _results.value
            .filter { it.isHealthy }
            .take(20)
            .map { "${it.ip}:${it.port}" }

    fun exportCsv(): String {
        val header = "ip,port,latency_ms,loss_pct,colo,http_status,tls_ok"
        val rows = _results.value.filter { it.isHealthy }.joinToString("\n") { r ->
            "${r.ip},${r.port},${r.latencyMs},${"%.0f".format(r.loss)},${r.colo},${r.httpStatus},${r.tlsOk}"
        }
        return "$header\n$rows"
    }

    override fun onCleared() {
        super.onCleared()
        engine.cancel()
    }

    private companion object {
        const val MAX_RESULTS = 500
    }
}

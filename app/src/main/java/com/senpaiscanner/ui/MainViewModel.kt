package com.senpaiscanner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senpaiscanner.model.ScanConfig
import com.senpaiscanner.model.ScanResult
import com.senpaiscanner.model.ScanStats
import com.senpaiscanner.scanner.ConfigParser
import com.senpaiscanner.scanner.ScanEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

    init {
        viewModelScope.launch {
            engine.results.collect { result ->
                val updated = (_results.value + result)
                    .sortedWith(
                        compareBy(
                            { !it.isHealthy },
                            { it.latencyMs.takeIf { l -> l > 0 } ?: Long.MAX_VALUE }
                        )
                    )
                    .take(500)
                _results.value = updated
            }
        }
        viewModelScope.launch {
            engine.done.collect {
                _scanning.value = false
                _done.value = true
            }
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

    /** Returns top healthy IPs formatted as ip:port, sorted by latency */
    fun getHealthyIps(): List<String> =
        _results.value
            .filter { it.isHealthy }
            .sortedBy { it.latencyMs }
            .take(20)
            .map { "${it.ip}:${it.port}" }

    /** Full CSV export: ip,port,latency,loss,colo,status,tls */
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
}

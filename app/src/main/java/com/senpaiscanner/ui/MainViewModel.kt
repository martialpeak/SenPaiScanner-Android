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
                _results.value = (_results.value + result)
                    .sortedWith(compareBy({ !it.isHealthy }, { it.latencyMs.takeIf { l -> l > 0 } ?: Long.MAX_VALUE }))
                    .take(500) // keep top 500
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

    fun applyConfigUrl(url: String): ScanConfig {
        return ConfigParser.toScanConfig(url, config)
    }

    fun getHealthyIps(): List<String> {
        return _results.value
            .filter { it.isHealthy }
            .sortedBy { it.latencyMs }
            .take(20)
            .map { "${it.ip}:${it.port}" }
    }

    override fun onCleared() {
        super.onCleared()
        engine.cancel()
    }
}

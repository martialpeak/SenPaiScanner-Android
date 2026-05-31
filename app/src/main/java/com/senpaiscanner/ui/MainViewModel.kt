package com.senpaiscanner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.senpaiscanner.data.ScanHistoryRepository
import com.senpaiscanner.data.SettingsRepository
import com.senpaiscanner.model.ScanConfig
import com.senpaiscanner.model.ScanResult
import com.senpaiscanner.model.ScanStats
import com.senpaiscanner.scanner.ScanRepository
import com.senpaiscanner.service.ScanForegroundService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = ScanRepository.engine

    private val _results = MutableStateFlow<List<ScanResult>>(emptyList())
    val results: StateFlow<List<ScanResult>> = _results.asStateFlow()

    private val _coloFilter = MutableStateFlow<String?>(null)
    val coloFilter: StateFlow<String?> = _coloFilter.asStateFlow()

    val displayResults: StateFlow<List<ScanResult>> = combine(_results, _coloFilter) { list, colo ->
        if (colo.isNullOrBlank()) list
        else list.filter { it.colo.equals(colo, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stats: StateFlow<ScanStats> = engine.stats

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    private val _compareCount = MutableStateFlow(0)
    val compareCount: StateFlow<Int> = _compareCount.asStateFlow()

    val appSettings = SettingsRepository.appSettings.stateIn(
        viewModelScope, SharingStarted.Eagerly, com.senpaiscanner.model.AppSettings()
    )

    var config = ScanConfig()
    private var maxResultsLimit = 500

    private val resultsMutex = Mutex()
    private val pendingMutex = Mutex()
    private val pendingResults = mutableListOf<ScanResult>()

    private val resultOrder = compareBy<ScanResult>(
        { !it.isHealthy },
        { it.latencyMs.takeIf { l -> l > 0 } ?: Long.MAX_VALUE }
    )

    init {
        viewModelScope.launch {
            engine.results.collect { result ->
                pendingMutex.withLock { pendingResults.add(result) }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(UI_FLUSH_MS)
                flushPendingResults()
            }
        }
        viewModelScope.launch {
            engine.done.collect {
                flushPendingResults()
                _scanning.value = false
                _done.value = true
                persistAfterScan()
            }
        }
        viewModelScope.launch {
            engine.healthyFound.collect {
                _onHealthyFound.tryEmit(Unit)
            }
        }
    }

    private val _onHealthyFound = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val onHealthyFound: SharedFlow<Unit> = _onHealthyFound.asSharedFlow()

    private suspend fun flushPendingResults() {
        val batch = pendingMutex.withLock {
            if (pendingResults.isEmpty()) return
            pendingResults.toList().also { pendingResults.clear() }
        }
        resultsMutex.withLock {
            val list = _results.value.toMutableList()
            for (result in batch) {
                val insertAt = list.binarySearch { resultOrder.compare(it, result) }
                    .let { if (it >= 0) it else -it - 1 }
                list.add(insertAt, result)
            }
            if (list.size > maxResultsLimit) {
                list.subList(maxResultsLimit, list.size).clear()
            }
            _results.value = list
        }
    }

    private suspend fun persistAfterScan() {
        val healthy = _results.value.filter { it.isHealthy }.map { it.endpoint }
        SettingsRepository.saveLastResults(healthy)
        val stable = ScanHistoryRepository.compareWithPrevious(healthy.toSet())
        _compareCount.value = stable
        ScanHistoryRepository.saveScan(healthy)
    }

    fun setColoFilter(colo: String?) {
        _coloFilter.value = colo?.takeIf { it.isNotBlank() }
    }

    fun availableColos(): List<String> =
        _results.value.map { it.colo }.filter { it.isNotBlank() }.distinct().sorted()

    fun startScan(cfg: ScanConfig, useForegroundService: Boolean) {
        viewModelScope.launch {
            pendingMutex.withLock { pendingResults.clear() }
        }
        _results.value = emptyList()
        _scanning.value = true
        _done.value = false
        _compareCount.value = 0
        config = cfg
        maxResultsLimit = cfg.maxResults

        if (useForegroundService) {
            ScanForegroundService.start(getApplication(), cfg)
        } else {
            engine.start(cfg, viewModelScope)
        }
    }

    fun stopScan() {
        engine.cancel()
        ScanForegroundService.stop(getApplication())
        _scanning.value = false
        viewModelScope.launch { flushPendingResults() }
    }

    fun saveForm(form: SettingsRepository.ScanFormState) {
        viewModelScope.launch { SettingsRepository.saveScanForm(form) }
    }

    fun getHealthyIps(): List<String> =
        _results.value.filter { it.isHealthy }.take(20).map { it.endpoint }

    fun allResults(): List<ScanResult> = _results.value

    override fun onCleared() {
        super.onCleared()
    }

    private companion object {
        const val UI_FLUSH_MS = 200L
    }
}

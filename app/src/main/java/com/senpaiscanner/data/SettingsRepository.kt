package com.senpaiscanner.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.senpaiscanner.model.AppSettings
import com.senpaiscanner.model.ProbeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("settings")

object SettingsRepository {

    private lateinit var store: DataStore<Preferences>

    private val KEY_PORT = intPreferencesKey("port")
    private val KEY_COUNT = intPreferencesKey("count")
    private val KEY_CONCURRENCY = intPreferencesKey("concurrency")
    private val KEY_TIMEOUT_SEC = intPreferencesKey("timeout_sec")
    private val KEY_MODE = stringPreferencesKey("mode")
    private val KEY_CIDR = stringPreferencesKey("cidr")
    private val KEY_HEALTHY_ONLY = booleanPreferencesKey("healthy_only")
    private val KEY_PROBE_SNI = stringPreferencesKey("probe_sni")
    private val KEY_PROBE_PATH = stringPreferencesKey("probe_path")
    private val KEY_MAX_RESULTS = intPreferencesKey("max_results")
    private val KEY_STOP_AFTER = intPreferencesKey("stop_after_healthy")
    private val KEY_SKIP_FAILED = booleanPreferencesKey("skip_failed")
    private val KEY_VIBRATE = booleanPreferencesKey("vibrate")
    private val KEY_FOREGROUND = booleanPreferencesKey("foreground")
    private val KEY_IPV6 = booleanPreferencesKey("ipv6")
    private val KEY_SCHEDULED = booleanPreferencesKey("scheduled")
    private val KEY_SCHEDULE_HOURS = intPreferencesKey("schedule_hours")
    private val KEY_LAST_RESULTS = stringPreferencesKey("last_results")

    fun init(context: Context) {
        store = context.applicationContext.settingsStore
    }

    val appSettings: Flow<AppSettings> get() = store.data.map { p ->
        AppSettings(
            probeSni = p[KEY_PROBE_SNI] ?: "speed.cloudflare.com",
            probePath = p[KEY_PROBE_PATH] ?: "/cdn-cgi/trace",
            maxResults = p[KEY_MAX_RESULTS] ?: 500,
            stopAfterHealthy = p[KEY_STOP_AFTER] ?: 20,
            skipKnownFailed = p[KEY_SKIP_FAILED] ?: true,
            vibrateOnHealthy = p[KEY_VIBRATE] ?: false,
            useForegroundService = p[KEY_FOREGROUND] ?: true,
            useIpv6 = p[KEY_IPV6] ?: false,
            scheduledScanEnabled = p[KEY_SCHEDULED] ?: false,
            scheduledIntervalHours = p[KEY_SCHEDULE_HOURS] ?: 24
        )
    }

    data class ScanFormState(
        val port: Int = 443,
        val count: Int = 500,
        val concurrency: Int = 50,
        val timeoutSec: Int = 5,
        val mode: ProbeMode = ProbeMode.HTTP,
        val cidr: String = "",
        val healthyOnly: Boolean = false
    )

    val scanForm: Flow<ScanFormState> get() = store.data.map { p ->
        ScanFormState(
            port = p[KEY_PORT] ?: 443,
            count = p[KEY_COUNT] ?: 500,
            concurrency = p[KEY_CONCURRENCY] ?: 50,
            timeoutSec = p[KEY_TIMEOUT_SEC] ?: 5,
            mode = runCatching { ProbeMode.valueOf(p[KEY_MODE] ?: "HTTP") }.getOrDefault(ProbeMode.HTTP),
            cidr = p[KEY_CIDR] ?: "",
            healthyOnly = p[KEY_HEALTHY_ONLY] ?: false
        )
    }

    suspend fun saveScanForm(form: ScanFormState) {
        store.edit {
            it[KEY_PORT] = form.port
            it[KEY_COUNT] = form.count
            it[KEY_CONCURRENCY] = form.concurrency
            it[KEY_TIMEOUT_SEC] = form.timeoutSec
            it[KEY_MODE] = form.mode.name
            it[KEY_CIDR] = form.cidr
            it[KEY_HEALTHY_ONLY] = form.healthyOnly
        }
    }

    suspend fun saveAppSettings(settings: AppSettings) {
        store.edit {
            it[KEY_PROBE_SNI] = settings.probeSni
            it[KEY_PROBE_PATH] = settings.probePath
            it[KEY_MAX_RESULTS] = settings.maxResults
            it[KEY_STOP_AFTER] = settings.stopAfterHealthy
            it[KEY_SKIP_FAILED] = settings.skipKnownFailed
            it[KEY_VIBRATE] = settings.vibrateOnHealthy
            it[KEY_FOREGROUND] = settings.useForegroundService
            it[KEY_IPV6] = settings.useIpv6
            it[KEY_SCHEDULED] = settings.scheduledScanEnabled
            it[KEY_SCHEDULE_HOURS] = settings.scheduledIntervalHours
        }
    }

    suspend fun saveLastResults(lines: List<String>) {
        store.edit { it[KEY_LAST_RESULTS] = lines.joinToString("\n") }
    }

    val lastResults: Flow<List<String>> get() = store.data.map { p ->
        p[KEY_LAST_RESULTS]?.lines()?.filter { it.isNotBlank() } ?: emptyList()
    }
}

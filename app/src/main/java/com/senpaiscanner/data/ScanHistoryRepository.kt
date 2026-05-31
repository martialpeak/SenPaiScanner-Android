package com.senpaiscanner.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.historyStore: DataStore<androidx.datastore.preferences.core.Preferences> by
    preferencesDataStore("scan_history")

object ScanHistoryRepository {

    private val KEY_PREVIOUS = stringPreferencesKey("previous_healthy")
    private val KEY_WIDGET = stringPreferencesKey("widget_summary")

    private lateinit var store: DataStore<androidx.datastore.preferences.core.Preferences>

    fun init(context: Context) {
        store = context.applicationContext.historyStore
    }

    suspend fun getPreviousHealthy(): Set<String> {
        val raw = store.data.map { it[KEY_PREVIOUS] ?: "" }.first()
        return raw.split('\n').filter { it.isNotBlank() }.toSet()
    }

    suspend fun saveScan(healthyEndpoints: List<String>) {
        store.edit {
            it[KEY_PREVIOUS] = healthyEndpoints.joinToString("\n")
            it[KEY_WIDGET] = healthyEndpoints.take(3).joinToString(" · ").ifBlank { "—" }
        }
    }

    suspend fun compareWithPrevious(current: Set<String>): Int {
        val prev = getPreviousHealthy()
        if (prev.isEmpty()) return 0
        return current.count { it in prev }
    }

    suspend fun widgetSummary(): String =
        store.data.map { it[KEY_WIDGET] ?: "—" }.first()
}

package com.senpaiscanner.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.failedStore: DataStore<androidx.datastore.preferences.core.Preferences> by
    preferencesDataStore("failed_ips")

object FailedIpRepository {

    private const val MAX_STORED = 5000
    private val KEY_SET = stringPreferencesKey("ips")

    private lateinit var store: DataStore<androidx.datastore.preferences.core.Preferences>

    fun init(context: Context) {
        store = context.applicationContext.failedStore
    }

    suspend fun getSet(): Set<String> {
        val raw = store.data.map { it[KEY_SET] ?: "" }.first()
        if (raw.isEmpty()) return emptySet()
        return raw.split(',').filter { it.isNotBlank() }.toSet()
    }

    suspend fun record(ip: String) {
        store.edit { prefs ->
            val current = (prefs[KEY_SET] ?: "").split(',').filter { it.isNotBlank() }.toMutableSet()
            current.add(ip)
            val trimmed = if (current.size > MAX_STORED) {
                current.toList().takeLast(MAX_STORED).toSet()
            } else {
                current
            }
            prefs[KEY_SET] = trimmed.joinToString(",")
        }
    }

    suspend fun clear() {
        store.edit { it.remove(KEY_SET) }
    }
}

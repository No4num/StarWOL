package com.starchen.wol.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "starwol")

private val KEY_DEVICES = stringPreferencesKey("devices_json")
private val KEY_SETTINGS = stringPreferencesKey("settings_json")

object WolStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun devices(context: Context): Flow<List<Device>> =
        context.dataStore.data.map { p ->
            p[KEY_DEVICES]?.let { raw ->
                runCatching { json.decodeFromString<List<Device>>(raw) }.getOrNull()
            } ?: emptyList()
        }

    fun settings(context: Context): Flow<AppSettings> =
        context.dataStore.data.map { p ->
            p[KEY_SETTINGS]?.let { raw ->
                runCatching { json.decodeFromString<AppSettings>(raw) }.getOrNull()
            } ?: AppSettings()
        }

    suspend fun upsert(context: Context, device: Device) {
        val current = devices(context).first().toMutableList()
        val idx = current.indexOfFirst { it.id == device.id }
        if (idx >= 0) current[idx] = device else current.add(device)
        saveDevices(context, current)
    }

    suspend fun delete(context: Context, id: String) {
        val current = devices(context).first().filterNot { it.id == id }
        saveDevices(context, current)
    }

    suspend fun saveSettings(context: Context, settings: AppSettings) {
        context.dataStore.edit { it[KEY_SETTINGS] = json.encodeToString(settings) }
    }

    private suspend fun saveDevices(context: Context, list: List<Device>) {
        context.dataStore.edit { it[KEY_DEVICES] = json.encodeToString(list) }
    }

    /** 阻塞式读取（服务 / 广播接收器里用） */
    fun devicesBlocking(context: Context): List<Device> =
        runCatching { kotlinx.coroutines.runBlocking { devices(context).first() } }.getOrDefault(emptyList())
}

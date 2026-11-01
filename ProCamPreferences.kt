package com.procam.camera.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.procamDataStore by preferencesDataStore(name = "procam_settings")
class ProCamPreferences(private val context: Context) {
    private object Keys {
        val biometric = booleanPreferencesKey("biometric_enabled")
        val autoLock = intPreferencesKey("auto_lock_seconds")
        val hold = intPreferencesKey("shutter_hold_seconds")
        val grid = booleanPreferencesKey("camera_grid")
        val haptics = booleanPreferencesKey("haptics")
        val theme = stringPreferencesKey("theme")
    }
    val biometric: Flow<Boolean> = context.procamDataStore.data.map { it[Keys.biometric] ?: false }
    val autoLock: Flow<Int> = context.procamDataStore.data.map { it[Keys.autoLock] ?: 30 }
    val hold: Flow<Int> = context.procamDataStore.data.map { it[Keys.hold] ?: 5 }
    val grid: Flow<Boolean> = context.procamDataStore.data.map { it[Keys.grid] ?: false }
    val haptics: Flow<Boolean> = context.procamDataStore.data.map { it[Keys.haptics] ?: true }
    val theme: Flow<String> = context.procamDataStore.data.map { it[Keys.theme] ?: "dark" }
    suspend fun setBiometric(v: Boolean) { context.procamDataStore.edit { it[Keys.biometric] = v } }
    suspend fun setAutoLock(v: Int) { context.procamDataStore.edit { it[Keys.autoLock] = v } }
    suspend fun setHold(v: Int) { context.procamDataStore.edit { it[Keys.hold] = v } }
    suspend fun setGrid(v: Boolean) { context.procamDataStore.edit { it[Keys.grid] = v } }
    suspend fun setHaptics(v: Boolean) { context.procamDataStore.edit { it[Keys.haptics] = v } }
    suspend fun setTheme(v: String) { context.procamDataStore.edit { it[Keys.theme] = v } }
}

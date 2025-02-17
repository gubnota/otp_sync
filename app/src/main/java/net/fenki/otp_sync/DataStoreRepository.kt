package net.fenki.otp_sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreRepository(context: Context) {
    private val dataStore = context.dataStore

    companion object {
        private val BACKEND_URL = stringPreferencesKey("backend_url")
        private val SECRET = stringPreferencesKey("secret")
        private val NOTIFY_BACKEND = booleanPreferencesKey("notify_backend")
        private val IDS = stringPreferencesKey("ids")
    }

    val backendUrl: Flow<String> = dataStore.data.map { preferences ->
        preferences[BACKEND_URL] ?: BuildConfig.backend_url
    }

    val secret: Flow<String> = dataStore.data.map { preferences ->
        preferences[SECRET] ?: BuildConfig.secret
    }

    val notifyBackend: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[NOTIFY_BACKEND] ?: false
    }

    val ids: Flow<String> = dataStore.data.map { preferences ->
        preferences[IDS] ?: BuildConfig.ids
    }

    suspend fun saveBackendUrl(value: String) {
        dataStore.edit { settings ->
            settings[BACKEND_URL] = value
        }
    }

    suspend fun saveSecret(value: String) {
        dataStore.edit { settings ->
            settings[SECRET] = value
        }
    }

    suspend fun saveNotifyBackend(value: Boolean) {
        dataStore.edit { settings ->
            settings[NOTIFY_BACKEND] = value
        }
    }

    suspend fun saveIds(value: String) {
        dataStore.edit { settings ->
            settings[IDS] = value
        }
    }
}
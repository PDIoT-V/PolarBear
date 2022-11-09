package pdiot.v.polarbear

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settingPrefs")

class SettingPrefs(private val context: Context) {
    companion object {
        private val soundKey = stringPreferencesKey("sound")
        private val vibrateKey = booleanPreferencesKey("vibrate")
    }

    val getSound: Flow<String>
        get() = context.dataStore.data.map {
            it[soundKey] ?: ""
        }

    suspend fun setSound(value: String) {
        context.dataStore.edit { it[soundKey] = value }
    }

    val getVibration: Flow<Boolean>
        get() = context.dataStore.data.map {
            it[vibrateKey] ?: true
        }

    suspend fun setVibration(value: Boolean) {
        context.dataStore.edit { it[vibrateKey] = value }
    }
}
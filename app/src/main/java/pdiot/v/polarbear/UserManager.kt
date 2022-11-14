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
        private val respeckAccXKey = stringPreferencesKey("respeckAccX")
        private val respeckAccYKey = stringPreferencesKey("respeckAccY")
        private val respeckAccZKey = stringPreferencesKey("respeckAccZ")
        private val respeckGyrXKey = stringPreferencesKey("respeckGyrX")
        private val respeckGyrYKey = stringPreferencesKey("respeckGyrY")
        private val respeckGyrZKey = stringPreferencesKey("respeckGyrZ")

        private val thingyAccXKey = stringPreferencesKey("thingyAccX")
        private val thingyAccYKey = stringPreferencesKey("thingyAccY")
        private val thingyAccZKey = stringPreferencesKey("thingyAccZ")
        private val thingyGyrXKey = stringPreferencesKey("thingyGyrX")
        private val thingyGyrYKey = stringPreferencesKey("thingyGyrY")
        private val thingyGyrZKey = stringPreferencesKey("thingyGyrZ")
        private val thingyMagXKey = stringPreferencesKey("thingyMagX")
        private val thingyMagYKey = stringPreferencesKey("thingyMagY")
        private val thingyMagZKey = stringPreferencesKey("thingyMagZ")

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
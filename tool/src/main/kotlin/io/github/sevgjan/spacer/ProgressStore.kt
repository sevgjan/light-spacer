package io.github.sevgjan.spacer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What survives between plays. A run can be continued from the start of the last stage reached,
 * with the score it had then.
 */
@Serializable
data class Progress(
    val best: Int = 0,
    /** Stage to continue from, or null when there's no run to continue. */
    val checkpointStage: Int? = null,
    val checkpointScore: Int = 0,
    val completed: Boolean = false,
    val sound: Boolean = true,
)

class ProgressStore(private val dataStore: DataStore<Preferences>) {
    suspend fun load(): Progress = dataStore.data.first()[KEY]
        ?.let { runCatching { json.decodeFromString(Progress.serializer(), it) }.getOrNull() } ?: Progress()

    suspend fun save(progress: Progress) {
        dataStore.edit { it[KEY] = json.encodeToString(Progress.serializer(), progress) }
    }

    private companion object {
        val KEY = stringPreferencesKey("progress")
        val json = Json { ignoreUnknownKeys = true }
    }
}

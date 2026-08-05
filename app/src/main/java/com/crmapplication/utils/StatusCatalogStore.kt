package com.crmapplication.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.statusCatalogDataStore by preferencesDataStore(name = "status_catalog")

/** Record separator for the packed list. Not a legal character inside a status name. */
private const val SEPARATOR = "\n"

/**
 * Caches the server's lead statuses so the filter chips and status dropdown are populated on first
 * frame and offline. Exposes a [Flow], so a fresh sync pushes straight into any collecting
 * ViewModel — chips and dropdowns update without the screen being recreated.
 *
 * Separate DataStore file from [ProductCatalogStore] rather than two keys in one: the two lists come
 * from the same endpoint but are consumed by different screens, and keeping them apart means a
 * migration to either one can't corrupt the other.
 */
@Singleton
class StatusCatalogStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val STATUSES_KEY = stringPreferencesKey("statuses")
    }

    val statuses: Flow<List<String>> = context.statusCatalogDataStore.data.map { prefs ->
        prefs[STATUSES_KEY]
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    suspend fun save(statuses: List<String>) {
        context.statusCatalogDataStore.edit {
            it[STATUSES_KEY] = statuses.joinToString(SEPARATOR)
        }
    }
}

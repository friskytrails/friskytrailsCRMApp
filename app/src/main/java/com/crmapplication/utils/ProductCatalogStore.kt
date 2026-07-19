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

private val Context.productCatalogDataStore by preferencesDataStore(name = "product_catalog")

/** Record separator for the packed list. Not a legal character inside a product name. */
private const val SEPARATOR = "\n"

/**
 * Caches the server's product catalog so the Add Lead dropdown is populated on first frame and
 * offline. Exposes a [Flow], so a fresh sync pushes straight into any collecting ViewModel — the
 * dropdown updates without the screen being recreated.
 *
 * DataStore rather than Room: this is one ordered list of strings, so a Room entity would cost a
 * DAO plus a [com.crmapplication.LeadDetailVM.local.CrmDatabase] version bump for no query benefit.
 */
@Singleton
class ProductCatalogStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val PRODUCTS_KEY = stringPreferencesKey("products")
    }

    val products: Flow<List<String>> = context.productCatalogDataStore.data.map { prefs ->
        prefs[PRODUCTS_KEY]
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    suspend fun save(products: List<String>) {
        context.productCatalogDataStore.edit {
            it[PRODUCTS_KEY] = products.joinToString(SEPARATOR)
        }
    }
}

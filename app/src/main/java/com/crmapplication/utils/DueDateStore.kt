package com.crmapplication.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dueDateDataStore by preferencesDataStore(name = "lead_due_dates")

/**
 * Durable per-lead reminder dates (date **and** time), held outside Room on purpose.
 *
 * The reminder now round-trips to the backend via `PUT /api/leads/:id/reminder`, arriving back as
 * `dates.reminderDate`, so this is no longer the only copy. It is still the *resilient* one:
 * `CrmDatabase` runs with `fallbackToDestructiveMigration()`, so any schema bump in an app update
 * drops the `leads` table, and DataStore isn't versioned against the Room schema — it survives that
 * wipe (and a sync prune, and an empty sync) and rehydrates the table. It also covers the window
 * where a push failed: the reminder still fires locally and still shows in the list.
 *
 * `LeadsRepository.syncLeads` keeps this the union of every reminder known — it writes server-sourced
 * reminders in here too, and prefers a stored value over a server null (see the precedence comment
 * there for why null is ambiguous).
 *
 * Only uninstall / "clear data" clears these.
 */
@Singleton
class DueDateStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /**
     * Sets, or with a null [millis] clears, the date for one lead. Clearing removes the key rather
     * than storing 0 so [all] never hands back a stale date for a lead the agent deliberately reset.
     */
    suspend fun set(leadId: String, millis: Long?) {
        context.dueDateDataStore.edit { prefs ->
            val key = keyFor(leadId)
            if (millis == null) prefs.remove(key) else prefs[key] = millis
        }
    }

    /** Every saved date, keyed by lead id. Read once per sync to rehydrate the `leads` table. */
    suspend fun all(): Map<String, Long> =
        context.dueDateDataStore.data.first().asMap()
            .mapNotNull { (key, value) ->
                val leadId = key.name.removePrefix(KEY_PREFIX).takeIf { it != key.name } ?: return@mapNotNull null
                (value as? Long)?.let { leadId to it }
            }
            .toMap()

    /**
     * Drops dates for leads that no longer exist, mirroring `deleteLeadsNotIn`. Without this the
     * store would grow for the life of the install, since nothing else removes a key.
     *
     * An empty [keepIds] is deliberately a no-op rather than "clear everything". A zero-lead sync
     * response is ambiguous — genuinely no assigned leads, or a backend hiccup — and these dates
     * exist nowhere else, so they can't be re-fetched if that guess is wrong. Keeping a handful of
     * orphan keys costs nothing; deleting a date the agent set is not recoverable.
     */
    suspend fun retainOnly(keepIds: Collection<String>) {
        if (keepIds.isEmpty()) return
        val keep = keepIds.toSet()
        context.dueDateDataStore.edit { prefs ->
            prefs.asMap().keys.toList()
                .filter { it.name.startsWith(KEY_PREFIX) && it.name.removePrefix(KEY_PREFIX) !in keep }
                .forEach { prefs.remove(longPreferencesKey(it.name)) }
        }
    }

    private fun keyFor(leadId: String) = longPreferencesKey("$KEY_PREFIX$leadId")

    private companion object {
        const val KEY_PREFIX = "due_"
    }
}

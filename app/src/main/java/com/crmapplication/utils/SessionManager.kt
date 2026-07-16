package com.crmapplication.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "session")

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val TOKEN_KEY = stringPreferencesKey("auth_token")
        private val AGENT_KEY = stringPreferencesKey("agent_name")
        private val EMAIL_KEY = stringPreferencesKey("agent_email")
        private val AGENT_ID_KEY = stringPreferencesKey("agent_id")
    }

    fun saveToken(token: String) = runBlocking {
        context.dataStore.edit { it[TOKEN_KEY] = token }
    }

    fun saveAgentName(name: String) = runBlocking {
        context.dataStore.edit { it[AGENT_KEY] = name }
    }

    fun saveAgentEmail(email: String) = runBlocking {
        context.dataStore.edit { it[EMAIL_KEY] = email }
    }

    fun saveAgentId(id: String) = runBlocking {
        context.dataStore.edit { it[AGENT_ID_KEY] = id }
    }

    fun getToken(): String? = runBlocking {
        context.dataStore.data.first()[TOKEN_KEY]
    }

    fun getAgentName(): String = runBlocking {
        context.dataStore.data.first()[AGENT_KEY] ?: "Agent"
    }

    fun getAgentEmail(): String = runBlocking {
        context.dataStore.data.first()[EMAIL_KEY] ?: ""
    }

    fun getAgentId(): String? = runBlocking {
        context.dataStore.data.first()[AGENT_ID_KEY]
    }

    fun clear() = runBlocking {
        context.dataStore.edit { it.clear() }
    }
}

/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android.data.remote

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore by preferencesDataStore("qui_session")

/**
 * Server address plus credentials. qui accepts either a session cookie (issued by
 * /api/auth/login) or an `X-API-Key` header; this app supports both, preferring the
 * API key when one is configured because it survives session expiry.
 */
@Singleton
class SessionStore @Inject constructor(
    private val context: Context,
) {
    private object Keys {
        val serverUrl = stringPreferencesKey("server_url")
        val apiKey = stringPreferencesKey("api_key")
        val cookie = stringPreferencesKey("session_cookie")
        val username = stringPreferencesKey("username")
        val trustAllCerts = booleanPreferencesKey("trust_all_certs")
    }

    val serverUrl: Flow<String?> = context.sessionDataStore.data.map { it[Keys.serverUrl] }
    val username: Flow<String?> = context.sessionDataStore.data.map { it[Keys.username] }

    val isConfigured: Flow<Boolean> = context.sessionDataStore.data.map {
        !it[Keys.serverUrl].isNullOrBlank() &&
            (!it[Keys.apiKey].isNullOrBlank() || !it[Keys.cookie].isNullOrBlank())
    }

    suspend fun currentServerUrl(): String? = context.sessionDataStore.data.first()[Keys.serverUrl]

    suspend fun currentApiKey(): String? = context.sessionDataStore.data.first()[Keys.apiKey]

    suspend fun currentCookie(): String? = context.sessionDataStore.data.first()[Keys.cookie]

    suspend fun trustAllCerts(): Boolean =
        context.sessionDataStore.data.first()[Keys.trustAllCerts] ?: false

    suspend fun setServerUrl(url: String, trustAllCerts: Boolean) {
        context.sessionDataStore.edit {
            it[Keys.serverUrl] = normalizeBaseUrl(url)
            it[Keys.trustAllCerts] = trustAllCerts
        }
    }

    suspend fun setApiKey(key: String?) {
        context.sessionDataStore.edit {
            if (key.isNullOrBlank()) it.remove(Keys.apiKey) else it[Keys.apiKey] = key
        }
    }

    suspend fun setCookie(cookie: String?) {
        context.sessionDataStore.edit {
            if (cookie.isNullOrBlank()) it.remove(Keys.cookie) else it[Keys.cookie] = cookie
        }
    }

    suspend fun setUsername(name: String?) {
        context.sessionDataStore.edit {
            if (name.isNullOrBlank()) it.remove(Keys.username) else it[Keys.username] = name
        }
    }

    /** Drops credentials but keeps the server address so re-login is one field. */
    suspend fun clearCredentials() {
        context.sessionDataStore.edit {
            it.remove(Keys.apiKey)
            it.remove(Keys.cookie)
            it.remove(Keys.username)
        }
    }

    suspend fun clearAll() {
        context.sessionDataStore.edit { it.clear() }
    }

    companion object {
        /**
         * qui can live under a base URL (its `baseUrl` config), so the whole path is
         * kept and only normalized to a single trailing slash for Retrofit.
         */
        fun normalizeBaseUrl(raw: String): String {
            var url = raw.trim()
            if (url.isEmpty()) return url
            if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
                url = "http://$url"
            }
            return url.trimEnd('/') + "/"
        }
    }
}

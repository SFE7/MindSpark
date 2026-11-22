package com.mindspark.compose.core.auth

import android.content.Context

/**
 * Persists [AuthTokens] in [SharedPreferences].
 * Not unit-tested (requires Android Context); swappable for an encrypted store later.
 */
class SharedPrefsTokenStore(context: Context) : TokenStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(): AuthTokens? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        val id = prefs.getString(KEY_ID, "") ?: ""
        val expiry = prefs.getLong(KEY_EXPIRY, 0L)
        return AuthTokens(access, refresh, id, expiry)
    }

    override fun save(t: AuthTokens) {
        prefs.edit()
            .putString(KEY_ACCESS, t.accessToken)
            .putString(KEY_REFRESH, t.refreshToken)
            .putString(KEY_ID, t.idToken)
            .putLong(KEY_EXPIRY, t.expiresAtMillis)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "mindspark_auth"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_ID = "id_token"
        private const val KEY_EXPIRY = "expires_at_millis"
    }
}

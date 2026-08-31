@file:Suppress("DEPRECATION") // androidx.security.crypto has no non-deprecated replacement yet.

package com.chad.sensieink.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Local encrypted storage for the user-supplied Sensi refresh_token (see
 * sensi-client-spec.md, section 3: harvested manually via browser DevTools, then
 * treated as a config value). The refresh_token rotates on every use, so
 * [refreshToken] must be re-persisted after each successful token refresh.
 */
class TokenStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "sensi_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    /** Epoch millis when [accessToken] expires. */
    var accessTokenExpiresAt: Long
        get() = prefs.getLong(KEY_EXPIRES_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_EXPIRES_AT, value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun hasRefreshToken(): Boolean = !refreshToken.isNullOrBlank()

    private companion object {
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_EXPIRES_AT = "access_token_expires_at"
    }
}

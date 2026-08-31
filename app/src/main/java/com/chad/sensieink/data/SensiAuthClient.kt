package com.chad.sensieink.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

class SensiAuthException(message: String) : Exception(message)

/**
 * Exchanges the user-harvested refresh_token for a short-lived access_token, per
 * sensi-client-spec.md section 3. Only the refresh_token grant is implemented;
 * the password grant is deliberately not supported (reCAPTCHA-gated since Sensi
 * app v8.6.3+, confirmed 2026-08-31 against the live endpoint).
 *
 * client_id/client_secret below are Sensi's own public "fleet" web-client
 * credentials, hardcoded in their own app and documented by every open-source
 * Sensi integration (iprak/sensi, pysensi) - not a user secret.
 */
class SensiAuthClient(
    private val tokenStore: TokenStore,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    /**
     * Returns a valid access_token, refreshing it first if it is missing or
     * expired. Persists the rotated refresh_token on every refresh.
     */
    suspend fun ensureAccessToken(): String {
        val cachedAccessToken = tokenStore.accessToken
        val notExpired = System.currentTimeMillis() < tokenStore.accessTokenExpiresAt - EXPIRY_SAFETY_MARGIN_MS
        if (cachedAccessToken != null && notExpired) {
            return cachedAccessToken
        }
        return refresh()
    }

    /** Forces a token refresh regardless of the cached access_token's expiry. */
    suspend fun refresh(): String = withContext(Dispatchers.IO) {
        val refreshToken = tokenStore.refreshToken
            ?: throw SensiAuthException("No refresh_token configured")

        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .build()

        val request = Request.Builder()
            .url(TOKEN_URL)
            .header("Accept", "*/*")
            .post(body)
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw SensiAuthException("Network error refreshing token: ${e.message}")
        }

        response.use {
            val responseBody = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                throw SensiAuthException("Refresh token rejected (HTTP ${it.code}): $responseBody")
            }

            val json = JSONObject(responseBody)
            val accessToken = json.getString("access_token")
            val newRefreshToken = json.getString("refresh_token")
            val expiresInSeconds = json.optLong("expires_in", 0L)

            // The server rotates refresh_token on every use - persist the new one
            // immediately, or the next refresh will fail with the stale token.
            tokenStore.refreshToken = newRefreshToken
            tokenStore.accessToken = accessToken
            tokenStore.accessTokenExpiresAt =
                System.currentTimeMillis() + expiresInSeconds * 1000

            accessToken
        }
    }

    companion object {
        private const val TOKEN_URL = "https://oauth.sensiapi.io/token"
        private const val CLIENT_ID = "fleet"
        private const val CLIENT_SECRET = "JLFjJmketRhj>M9uoDhusYKyi?zUyNqhGB)H2XiwLEF#KcGKrRD2JZsDQ7ufNven"
        private const val EXPIRY_SAFETY_MARGIN_MS = 60_000L
    }
}

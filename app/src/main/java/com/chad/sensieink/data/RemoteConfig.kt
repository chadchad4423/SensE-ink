package com.chad.sensieink.data

import android.util.Log
import com.chad.sensieink.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "RemoteConfig"

data class AppNotice(val message: String?, val latestVersion: String?)

/**
 * Fetches a small JSON file for an optional broadcast [AppNotice.message] and
 * a [AppNotice.latestVersion] marker - the same pattern TripTime uses (see
 * that repo's `data/RemoteConfig.kt`), trimmed down since this app has no
 * remote-overridable endpoint or key to protect, just an advisory message.
 *
 * Every failure here - no network, a 404, malformed JSON - is swallowed and
 * treated as "nothing to say." This must never be able to make the app worse
 * than not having it at all, which is what lets it run once at launch on a
 * background thread and be ignored if it never returns.
 *
 * Two URLs on different GitHub surfaces (raw content + Pages) so a change to
 * one host's URL structure can't silently take out both.
 */
class RemoteConfigFetcher(
    private val urls: List<String> = listOf(
        BuildConfig.CONFIG_URL,
        "https://chadchad4423.github.io/Sensi-eink/config.json",
    ),
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(): AppNotice? = withContext(Dispatchers.IO) {
        for (url in urls) {
            val notice = runCatching { fetchOne(url) }
                .onFailure { Log.w(TAG, "Config fetch failed for $url", it) }
                .getOrNull()
            if (notice != null) return@withContext notice
        }
        null
    }

    private fun fetchOne(url: String): AppNotice? {
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            val json = JSONObject(body)
            return AppNotice(
                message = json.optString("message").takeIf { it.isNotBlank() },
                latestVersion = json.optString("latestVersion").takeIf { it.isNotBlank() },
            )
        }
    }
}

/**
 * [AppNotice.message] wins outright when present; otherwise a newer
 * [AppNotice.latestVersion] produces an update nudge. Version comparison is
 * numeric-segment-wise (1.10 > 1.9); anything unparseable is treated as
 * "nothing to say" rather than guessed at.
 */
fun noticeFor(notice: AppNotice, currentVersion: String = BuildConfig.VERSION_NAME): String? {
    notice.message?.let { return it }
    val latest = notice.latestVersion ?: return null
    if (!isNewer(latest, currentVersion)) return null
    return "SensE-ink $latest is available. This copy is $currentVersion."
}

private fun isNewer(candidate: String, current: String): Boolean {
    val a = candidate.split(".").mapNotNull { it.toIntOrNull() }
    val b = current.split(".").mapNotNull { it.toIntOrNull() }
    if (a.isEmpty() || b.isEmpty()) return false
    for (i in 0 until maxOf(a.size, b.size)) {
        val ai = a.getOrElse(i) { 0 }
        val bi = b.getOrElse(i) { 0 }
        if (ai != bi) return ai > bi
    }
    return false
}

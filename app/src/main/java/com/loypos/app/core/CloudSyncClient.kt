package com.loypos.app.core

import android.content.Context
import com.loypos.app.BuildConfig
import com.loypos.app.data.LoyPosRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Plesk MySQL/MariaDB HTTPS REST sync client targeting novelsapps.com.et.
 * Credentials (Tier 2): the terminal signs in ONCE with an owner/manager
 * account (POST /devices/register) and receives its own long-lived device
 * token, stored via SecurityManager (Android Keystore / EncryptedSharedPreferences)
 * and falling back to BuildConfig.PLESK_SYNC_TOKEN. The server also accepts the
 * legacy shared token until device auth is required.
 * Local Room SQLite remains the offline-first runtime store so POS checkout never blocks.
 */
object CloudSyncClient {
    val ENDPOINT: String get() = BuildConfig.PLESK_API_BASE_URL.trimEnd('/')

    fun resolveSyncToken(context: Context): String {
        val secureToken = SecurityManager.getSecureToken(context)
        if (secureToken.isNotBlank()) return secureToken

        return BuildConfig.PLESK_SYNC_TOKEN
            .trim()
            .takeIf { it.isNotEmpty() && it != "YOUR_PLESK_SYNC_TOKEN" }
            .orEmpty()
    }

    sealed class RegisterOutcome {
        data class Success(val deviceId: Long, val deviceName: String, val pairedAs: String) : RegisterOutcome()
        data class Failure(val message: String) : RegisterOutcome()
    }

    /**
     * Tier 2 self-registration: sign in with an owner/manager account and this
     * terminal registers itself. On success the device token is stored in the
     * same secure slot used for sync (SecurityManager.saveSecureToken) — from
     * then on resolveSyncToken() returns the device token and every sync call
     * authenticates as this specific, individually revocable device.
     * No pairing codes, nothing to copy from the back office.
     */
    suspend fun registerDevice(
        context: Context,
        serverUrl: String,
        email: String,
        password: String,
        deviceName: String,
    ): RegisterOutcome = withContext(Dispatchers.IO) {
        val base = serverUrl.trim().trimEnd('/')
        val mail = email.trim().lowercase()
        if (!base.startsWith("http")) {
            return@withContext RegisterOutcome.Failure("Server URL must start with https://")
        }
        if (!mail.contains('@') || mail.length < 5) {
            return@withContext RegisterOutcome.Failure("Enter the email of your owner or manager account")
        }
        if (password.isEmpty()) {
            return@withContext RegisterOutcome.Failure("Enter the account password")
        }
        try {
            val body = JSONObject().apply {
                put("email", mail)
                put("password", password)
                put("name", deviceName.trim().ifEmpty { android.os.Build.MODEL })
            }
            val res = httpPostJson("$base/devices/register", "", body)
            val err = res.json?.optString("error")?.takeIf { it.isNotEmpty() }
            when {
                res.code in 200..299 && res.json != null -> {
                    val deviceToken = res.json.optString("device_token").orEmpty()
                    if (deviceToken.isEmpty()) {
                        RegisterOutcome.Failure("Server accepted the sign-in but returned no token (HTTP ${res.code})")
                    } else {
                        SecurityManager.saveSecureToken(context, deviceToken)
                        SecurityManager.saveCredential(context, "device_id", res.json.optLong("device_id", -1L).toString())
                        SecurityManager.saveCredential(context, "device_name", res.json.optString("device_name"))
                        SecurityManager.saveCredential(context, "paired_as", res.json.optString("paired_as"))
                        RegisterOutcome.Success(
                            res.json.optLong("device_id", -1L),
                            res.json.optString("device_name").ifEmpty { "Terminal" },
                            res.json.optString("paired_as"),
                        )
                    }
                }
                res.code == 401 -> RegisterOutcome.Failure(err ?: "Invalid email or password")
                res.code == 403 -> RegisterOutcome.Failure(err ?: "This account can't add terminals")
                res.code == 429 -> RegisterOutcome.Failure(err ?: "Too many attempts — wait a minute and retry")
                else -> RegisterOutcome.Failure(err ?: "Sign-in failed (HTTP ${res.code})")
            }
        } catch (e: Exception) {
            RegisterOutcome.Failure("Cannot reach $base (${e.javaClass.simpleName})")
        }
    }

    sealed class SyncOutcome {
        data class Success(val message: String) : SyncOutcome()
        data class Offline(val message: String) : SyncOutcome()
    }

    suspend fun syncWithCloud(context: Context, repo: LoyPosRepository): SyncOutcome = withContext(Dispatchers.IO) {
        val raw = repo.loadRawSettings()
        val lastSyncMs = raw["last_sync_ms"]?.toLongOrNull() ?: 0L
        val token = resolveSyncToken(context)

        if (token.isEmpty()) {
            return@withContext SyncOutcome.Offline(
                "Enter Plesk Sync Token in Settings (secured by Android Keystore) to enable cloud sync · saving offline"
            )
        }

        try {
            // 1. Verify Plesk API & MySQL connectivity
            val health = httpGetJson("$ENDPOINT/health", token)
            if (health.code !in 200..299) {
                val serverErr = health.json?.optString("error")?.takeIf { it.isNotEmpty() }
                val hint = when (health.code) {
                    401, 403 -> "Plesk API rejected sync token (HTTP ${health.code})"
                    404 -> "Plesk API not deployed at $ENDPOINT yet (HTTP 404)"
                    503 -> serverErr ?: "Plesk MySQL database not created or unavailable (HTTP 503)"
                    else -> serverErr ?: "Plesk server returned HTTP ${health.code}"
                }
                return@withContext SyncOutcome.Offline("$hint · local Room store active")
            }

            // 2. Push incremental/full local Room changes to Plesk MySQL
            val (payload, localCount) = repo.exportPleskSyncPayload(lastSyncMs)
            val pushRes = httpPostJson("$ENDPOINT/sync/push", token, payload)
            if (pushRes.code !in 200..299) {
                val err = pushRes.json?.optString("error")?.takeIf { it.isNotEmpty() }
                    ?: "Push failed (HTTP ${pushRes.code})"
                return@withContext SyncOutcome.Offline("Plesk sync error: $err · saved locally")
            }
            val pushedServerCount = pushRes.json?.optInt("upserted", localCount) ?: localCount

            // 3. Pull remote changes updated since lastSyncMs from Plesk MySQL
            val pullRes = httpGetJson("$ENDPOINT/sync/pull?since=$lastSyncMs", token)
            var pulledCount = 0
            if (pullRes.code in 200..299 && pullRes.json != null) {
                pulledCount = repo.applyPleskPullPayload(pullRes.json)
            }

            val serverTimeMs = pullRes.json?.optLong("server_time_ms")
                ?: pushRes.json?.optLong("server_time_ms")
                ?: nowMs()
            repo.saveSetting("last_sync_ms", serverTimeMs.toString())

            SyncOutcome.Success(
                "Synced with Plesk MySQL · ↑$pushedServerCount pushed · ↓$pulledCount pulled"
            )
        } catch (e: Exception) {
            SyncOutcome.Offline(
                "Plesk unreachable (${e.javaClass.simpleName}) · all sales & stock saved locally"
            )
        }
    }

    private data class HttpJsonResult(val code: Int, val raw: String, val json: JSONObject?)

    private fun httpGetJson(urlString: String, token: String): HttpJsonResult {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7000
            readTimeout = 10000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-Sync-Token", token)
        }
        return readResponse(conn)
    }

    private fun httpPostJson(urlString: String, token: String, body: JSONObject): HttpJsonResult {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 7000
            readTimeout = 15000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-Sync-Token", token)
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(body.toString())
            writer.flush()
        }
        return readResponse(conn)
    }

    private fun readResponse(conn: HttpURLConnection): HttpJsonResult {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val raw = runCatching {
            stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        }.getOrDefault("")
        conn.disconnect()
        val parsed = runCatching { if (raw.isNotBlank()) JSONObject(raw) else null }.getOrNull()
        return HttpJsonResult(code, raw, parsed)
    }
}

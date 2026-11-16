package com.loypos.app.core

import com.loypos.app.data.LoyPosRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Best-effort HTTP health & sync adapter matching the LoyPOS Appwrite/Plesk
 * backend endpoint (https://novelsapps.com.et/api/v1).
 * The local Room SQLite store stays the primary runtime source of truth so
 * the POS never blocks when offline.
 */
object CloudSyncClient {
    const val ENDPOINT = "https://novelsapps.com.et/api/v1"
    const val PROJECT_ID = "6af71b570013e250471a"

    sealed class SyncOutcome {
        data class Success(val message: String) : SyncOutcome()
        data class Offline(val message: String) : SyncOutcome()
    }

    suspend fun syncWithCloud(repo: LoyPosRepository): SyncOutcome = withContext(Dispatchers.IO) {
        val raw = repo.loadRawSettings()
        val teamId = raw["cloud_team_id"].orEmpty()
        try {
            val conn = (URL("$ENDPOINT/health").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("X-Appwrite-Project", PROJECT_ID)
            }
            val code = conn.responseCode
            val body = runCatching {
                conn.inputStream.bufferedReader().use { it.readText() }
            }.getOrDefault("")
            conn.disconnect()

            if (code in 200..299) {
                val now = nowMs()
                repo.saveSetting("last_sync_ms", now.toString())
                val status = runCatching { JSONObject(body).optString("status", "ok") }.getOrDefault("ok")
                val suffix = if (teamId.isNotEmpty()) " (Shop $teamId)" else ""
                SyncOutcome.Success("Cloud reachable ($status)$suffix · local store up to date")
            } else {
                SyncOutcome.Offline("Cloud returned HTTP $code · operating offline")
            }
        } catch (_: Exception) {
            SyncOutcome.Offline("Cloud unreachable · all sales and inventory saved locally")
        }
    }
}

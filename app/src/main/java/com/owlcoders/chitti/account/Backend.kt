package com.owlcoders.chitti.account

import android.util.Log
import com.owlcoders.chitti.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Backup metadata as the server reports it. Only public values: no key, no password. */
data class BackupMeta(
    val size: Long,
    val sha256: String,
    val createdAt: String,
    val formatVersion: Int,
    val kdf: String,
    val kdfIterations: Int,
    val kdfSalt: String,
    val device: String
) {
    companion object {
        fun fromJson(o: JSONObject) = BackupMeta(
            size = o.optLong("size"), sha256 = o.optString("sha256"), createdAt = o.optString("createdAt"),
            formatVersion = o.optInt("formatVersion"), kdf = o.optString("kdf"),
            kdfIterations = o.optInt("kdfIterations"), kdfSalt = o.optString("kdfSalt"), device = o.optString("device")
        )
    }
}

class BackendException(message: String, val code: Int = 0) : Exception(message)

/**
 * The Chitti backend (Render + MongoDB). Every call carries the user's Firebase ID token; the server
 * verifies it and scopes everything to that user. Backups go up and come down as ciphertext only.
 *
 * The free Render instance sleeps when idle and can take up to a minute to wake, so timeouts are
 * generous and [wake] exists to start it early (e.g. while the user types the backup password).
 */
object Backend {

    private const val TAG = "ChittiBackend"
    private const val CONNECT_MS = 20_000
    private const val READ_MS = 75_000

    val isConfigured: Boolean get() = BuildConfig.BACKEND_URL.isNotBlank()

    private fun url(path: String) = URL(BuildConfig.BACKEND_URL.trimEnd('/') + path)

    private suspend fun open(path: String, method: String, authed: Boolean = true): HttpURLConnection {
        if (!isConfigured) throw BackendException("Backup server isn't set up in this build.")
        val token = if (authed) Auth.idToken() ?: throw BackendException("You're signed out. Sign in again.", 401) else null
        return (url(path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_MS
            readTimeout = READ_MS
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
        }
    }

    private fun HttpURLConnection.errorText(): String =
        runCatching { errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()

    private fun fail(conn: HttpURLConnection): Nothing {
        val code = conn.responseCode
        val body = conn.errorText()
        Log.w(TAG, "HTTP $code ${conn.url.path}: ${body.take(200)}")
        val message = when (code) {
            401 -> "Your session expired. Sign in again."
            404 -> "Nothing found."
            413 -> "The backup is too large to upload."
            429 -> "Too many attempts. Wait a few minutes and try again."
            in 500..599 -> "The backup server had a problem. Try again shortly."
            else -> "Something went wrong ($code)."
        }
        throw BackendException(message, code)
    }

    private suspend fun <T> call(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: BackendException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Request failed: ${e.javaClass.simpleName}: ${e.message}")
            throw BackendException("Couldn't reach the backup server. Check your connection.")
        }
    }

    /** Starts a sleeping free-tier instance. Errors are ignored. */
    suspend fun wake() {
        if (!isConfigured) return
        runCatching { call { open("/health", "GET", authed = false).run { responseCode; disconnect() } } }
    }

    /** Registers or refreshes the signed-in user on the server. */
    suspend fun upsertMe(displayName: String?) = call {
        val conn = open("/v1/me", "POST")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(JSONObject().apply { if (!displayName.isNullOrBlank()) put("displayName", displayName) }.toString().toByteArray()) }
        if (conn.responseCode !in 200..299) fail(conn)
        conn.disconnect()
    }

    suspend fun deleteMe() = call {
        val conn = open("/v1/me", "DELETE")
        if (conn.responseCode !in 200..299 && conn.responseCode != 404) fail(conn)
        conn.disconnect()
    }

    /** The latest backup's metadata, or null when there is none. */
    suspend fun backupMeta(): BackupMeta? = call {
        val conn = open("/v1/backup/meta", "GET")
        when (conn.responseCode) {
            200 -> BackupMeta.fromJson(JSONObject(conn.inputStream.bufferedReader().use { it.readText() }))
            404 -> null
            else -> fail(conn)
        }.also { conn.disconnect() }
    }

    suspend fun uploadBackup(
        file: File,
        sha256: String,
        iterations: Int,
        saltBase64: String,
        device: String,
        onProgress: (Float) -> Unit
    ): BackupMeta = call {
        val conn = open("/v1/backup", "PUT")
        conn.doOutput = true
        conn.setFixedLengthStreamingMode(file.length())
        conn.setRequestProperty("Content-Type", "application/octet-stream")
        conn.setRequestProperty("X-Backup-Format-Version", "1")
        conn.setRequestProperty("X-Backup-Kdf", "PBKDF2-HMAC-SHA256")
        conn.setRequestProperty("X-Backup-Kdf-Iterations", iterations.toString())
        conn.setRequestProperty("X-Backup-Kdf-Salt", saltBase64)
        conn.setRequestProperty("X-Backup-Sha256", sha256)
        conn.setRequestProperty("X-Backup-Device", device.take(100))
        val total = file.length().coerceAtLeast(1)
        conn.outputStream.use { out ->
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                var sent = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    sent += n
                    onProgress(sent.toFloat() / total)
                }
            }
        }
        if (conn.responseCode !in 200..299) fail(conn)
        BackupMeta.fromJson(JSONObject(conn.inputStream.bufferedReader().use { it.readText() })).also { conn.disconnect() }
    }

    suspend fun downloadBackup(target: File, onProgress: (Float) -> Unit) = call {
        val conn = open("/v1/backup", "GET")
        if (conn.responseCode != 200) fail(conn)
        val total = conn.contentLengthLong.coerceAtLeast(1)
        conn.inputStream.use { input ->
            target.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var got = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    got += n
                    onProgress(got.toFloat() / total)
                }
            }
        }
        conn.disconnect()
    }

    suspend fun deleteBackup() = call {
        val conn = open("/v1/backup", "DELETE")
        if (conn.responseCode !in 200..299 && conn.responseCode != 404) fail(conn)
        conn.disconnect()
    }
}

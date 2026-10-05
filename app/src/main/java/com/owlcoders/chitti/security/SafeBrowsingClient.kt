package com.owlcoders.chitti.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.owlcoders.chitti.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Google Safe Browsing (Lookup API v4): asks Google whether a URL is on its lists of phishing,
 * malware and unwanted-software pages.
 *
 * This is the one place Chitti sends anything off the phone, and only the link being checked.
 * It is a second opinion: the on-device [LinkScanner] always runs first and works offline; when
 * the network is slow or absent, this returns [Result.Unavailable] within a few seconds and the
 * local verdict stands.
 *
 * The API key is restricted in Cloud Console to this package and its signing certificate, so
 * every request carries the package name and the SHA-1 of the certificate the running app was
 * actually signed with.
 */
object SafeBrowsingClient {

    sealed interface Result {
        /** Google lists this URL. [threatTypes] are Safe Browsing threat type names. */
        data class Listed(val threatTypes: List<String>) : Result
        /** Google does not list this URL. */
        data object NotListed : Result
        /** No answer (offline, timeout, key missing or refused); rely on the local check. */
        data class Unavailable(val reason: String) : Result
    }

    private const val TAG = "SafeBrowsing"
    private const val ENDPOINT = "https://safebrowsing.googleapis.com/v4/threatMatches:find"
    private const val TIMEOUT_MS = 3000
    private const val CACHE_MS = 5 * 60 * 1000L

    private val THREAT_TYPES = listOf("MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION")

    private data class Cached(val result: Result, val at: Long)
    private val cache = ConcurrentHashMap<String, Cached>()

    @Volatile
    private var certSha1: String? = null

    val isConfigured: Boolean get() = BuildConfig.SAFE_BROWSING_API_KEY.isNotBlank()

    suspend fun check(context: Context, url: String): Result = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext Result.Unavailable("not configured")
        val now = System.currentTimeMillis()
        cache[url]?.takeIf { now - it.at < CACHE_MS }?.let { return@withContext it.result }

        val result = try {
            request(context, url)
        } catch (e: Exception) {
            Log.w(TAG, "Lookup failed: ${e.javaClass.simpleName}: ${e.message}")
            Result.Unavailable(e.javaClass.simpleName)
        }
        // Only definite answers are cached; a failure should be retried next time.
        if (result !is Result.Unavailable) cache[url] = Cached(result, now)
        result
    }

    private fun request(context: Context, url: String): Result {
        val body = JSONObject()
            .put("client", JSONObject().put("clientId", "chitti").put("clientVersion", "1.0"))
            .put(
                "threatInfo",
                JSONObject()
                    .put("threatTypes", JSONArray(THREAT_TYPES))
                    .put("platformTypes", JSONArray(listOf("ANY_PLATFORM")))
                    .put("threatEntryTypes", JSONArray(listOf("URL")))
                    .put("threatEntries", JSONArray().put(JSONObject().put("url", url)))
            )
            .toString()

        val conn = (URL("$ENDPOINT?key=${BuildConfig.SAFE_BROWSING_API_KEY}").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Android-Package", context.packageName)
            signingCertSha1(context)?.let { setRequestProperty("X-Android-Cert", it) }
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            if (code != 200) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                Log.w(TAG, "HTTP $code: ${err.take(300)}")
                return Result.Unavailable("HTTP $code")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val matches = json.optJSONArray("matches") ?: return Result.NotListed
            val types = (0 until matches.length())
                .mapNotNull { matches.optJSONObject(it)?.optString("threatType")?.takeIf(String::isNotBlank) }
                .distinct()
            return if (types.isEmpty()) Result.NotListed else Result.Listed(types)
        } finally {
            conn.disconnect()
        }
    }

    /** SHA-1 of the certificate this app is signed with, as upper-case hex without colons. */
    private fun signingCertSha1(context: Context): String? {
        certSha1?.let { return it }
        return try {
            val pm = context.packageManager
            val cert = if (Build.VERSION.SDK_INT >= 28) {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo?.apkContentsSigners?.firstOrNull()
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
            } ?: return null
            MessageDigest.getInstance("SHA-1").digest(cert.toByteArray())
                .joinToString("") { "%02X".format(it) }
                .also { certSha1 = it }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read signing certificate: ${e.message}")
            null
        }
    }

    /** Plain-language reason for a Safe Browsing threat type: a short claim, then what it means. */
    fun explain(threatType: String): String = when (threatType) {
        "SOCIAL_ENGINEERING" -> "Google lists this page as phishing: it pretends to be someone you trust to steal passwords, OTPs or payment details."
        "MALWARE" -> "Google lists this page as malware: it can install software that harms your phone."
        "UNWANTED_SOFTWARE" -> "Google lists this page as deceptive: it pushes unwanted software."
        "POTENTIALLY_HARMFUL_APPLICATION" -> "Google lists this page as offering harmful apps."
        else -> "Google lists this page as unsafe."
    }
}

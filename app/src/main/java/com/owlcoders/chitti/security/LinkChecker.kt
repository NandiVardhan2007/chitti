package com.owlcoders.chitti.security

import android.content.Context

/**
 * One verdict for a link, from two independent checks:
 *  1. [LinkScanner] on the phone: always runs, instant, works offline, explains itself;
 *  2. [SafeBrowsingClient]: Google's list of known phishing / malware pages, when online.
 *
 * Google listing a page always makes it DANGER, whatever the local score. Otherwise the local
 * verdict stands, and [google] records what Google said so the screen can tell the user exactly
 * how the link was checked.
 */
object LinkChecker {

    enum class GoogleStatus { Listed, NotListed, Offline, NotConfigured, NotApplicable }

    data class Verdict(
        val url: String,
        val host: String,
        val level: LinkScanner.RiskLevel,
        val localScore: Int,
        val reasons: List<String>,
        val google: GoogleStatus
    )

    suspend fun check(context: Context, url: String): Verdict {
        val local = LinkScanner.scan(url)
        val isWeb = url.trim().startsWith("http://", ignoreCase = true) || url.trim().startsWith("https://", ignoreCase = true)
        val google = if (isWeb) SafeBrowsingClient.check(context, url.trim()) else SafeBrowsingClient.Result.Unavailable("not a web link")

        return when (google) {
            is SafeBrowsingClient.Result.Listed -> Verdict(
                url = local.url,
                host = local.host,
                level = LinkScanner.RiskLevel.DANGER,
                localScore = local.score,
                reasons = google.threatTypes.map(SafeBrowsingClient::explain) +
                    local.reasons.filter { local.level != LinkScanner.RiskLevel.SAFE },
                google = GoogleStatus.Listed
            )
            is SafeBrowsingClient.Result.NotListed -> Verdict(
                local.url, local.host, local.level, local.score, local.reasons, GoogleStatus.NotListed
            )
            is SafeBrowsingClient.Result.Unavailable -> Verdict(
                local.url, local.host, local.level, local.score, local.reasons,
                when {
                    !isWeb -> GoogleStatus.NotApplicable
                    SafeBrowsingClient.isConfigured -> GoogleStatus.Offline
                    else -> GoogleStatus.NotConfigured
                }
            )
        }
    }
}

package com.owlcoders.chitti.ui.screens

import android.content.Context
import com.owlcoders.chitti.db.entities.AutomationHistory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/*
 * Text shared by several screens, so a time, an app name or an action reads the same everywhere.
 */

/** "just now", "5m ago", "3h ago", "2d ago", then a date. */
fun relativeTime(time: Long, now: Long = System.currentTimeMillis()): String {
    val delta = now - time
    val minutes = delta / 60_000L
    val hours = delta / 3_600_000L
    val days = delta / 86_400_000L
    return when {
        delta < 0L -> SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(time))
        minutes < 1L -> "Just now"
        minutes < 60L -> "${minutes}m ago"
        hours < 24L -> "${hours}h ago"
        days < 7L -> "${days}d ago"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(time))
    }
}

/** Section a timestamp belongs to in a day-grouped list. */
fun dayBucket(time: Long, now: Long = System.currentTimeMillis()): String {
    val today = Calendar.getInstance().apply { timeInMillis = now; startOfDay() }.timeInMillis
    val yesterday = today - 86_400_000L
    return when {
        time >= today -> "Today"
        time >= yesterday -> "Yesterday"
        time >= today - 6 * 86_400_000L -> "This week"
        else -> "Earlier"
    }
}

private fun Calendar.startOfDay() {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

/** The app's real name from the package manager; falls back to the last package segment. */
@Suppress("DEPRECATION") // the flags overload is API 33+; minSdk is 26
fun appLabel(context: Context, packageName: String): String = try {
    val info = context.packageManager.getApplicationInfo(packageName, 0)
    context.packageManager.getApplicationLabel(info).toString()
} catch (e: Exception) {
    packageName.substringAfterLast('.').ifBlank { packageName }.replaceFirstChar { it.uppercase() }
}

private val ACRONYMS = setOf("sms", "url", "otp", "api", "id", "ui", "gps", "qr", "pdf")

/** OPEN_APP -> "Open app", SEND_SMS -> "Send SMS". */
fun humaniseAction(raw: String): String {
    val words = raw.trim().split('_', '-', ' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return "Action"
    return words.joinToString(" ") { word ->
        val lower = word.lowercase(Locale.getDefault())
        if (lower in ACRONYMS) lower.uppercase(Locale.getDefault()) else lower
    }.replaceFirstChar { it.uppercase() }
}

enum class Outcome { Success, Failed, Cancelled, Pending, Other }

fun outcomeOf(result: String): Outcome {
    val r = result.trim().lowercase(Locale.getDefault())
    return when {
        r.startsWith("success") -> Outcome.Success
        r.startsWith("failed") || r.startsWith("error") -> Outcome.Failed
        r.startsWith("cancel") -> Outcome.Cancelled
        r.isEmpty() -> Outcome.Pending
        else -> Outcome.Other
    }
}

/** The quiet second line of an action: what the result said beyond its status word, then the parameters. */
fun actionDetail(item: AutomationHistory): String? {
    val result = item.result.indexOf(':').takeIf { it >= 0 }?.let { item.result.substring(it + 1).trim() }?.takeIf { it.isNotEmpty() }
    var params = item.parameters.trim()
    if (params.isEmpty() || params == "{}" || params == "[]" || params.equals("null", ignoreCase = true)) params = ""
    if (params.startsWith("{") && params.endsWith("}")) params = params.substring(1, params.length - 1)
    params = params.replace("\"", "").replace(",", " · ").replace(":", ": ").replace(Regex("\\s+"), " ").trim()
    return listOfNotNull(result, params.takeIf { it.isNotEmpty() }).joinToString(" · ").takeIf { it.isNotEmpty() }
}

fun String.titleCase(): String = replaceFirstChar { it.uppercase() }

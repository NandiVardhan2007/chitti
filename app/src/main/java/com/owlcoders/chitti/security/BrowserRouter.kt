package com.owlcoders.chitti.security

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Chitti as the phone's link opener.
 *
 * Android only routes a tapped link to an app that is the default browser, so "check every link"
 * means Chitti holds the browser role. It then needs a real browser to hand safe links to: that is
 * the user's choice ([preferredBrowser]), defaulting to Chrome, then any other installed browser.
 * Chitti never forwards to itself, so there is no loop.
 */
object BrowserRouter {

    data class Browser(val packageName: String, val label: String)

    private const val PREFS = "chitti_prefs"
    private const val KEY_BROWSER = "safe_link_browser"
    private const val CHROME = "com.android.chrome"

    /** Browsers installed on the phone, excluding Chitti. */
    fun installedBrowsers(context: Context): List<Browser> {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
        val pm = context.packageManager
        return pm.queryIntentActivities(probe, PackageManager.MATCH_ALL)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { Browser(it.packageName, it.loadLabel(pm).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    /** The browser safe links open in. */
    fun preferredBrowser(context: Context): Browser? {
        val all = installedBrowsers(context)
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_BROWSER, null)
        return all.firstOrNull { it.packageName == saved }
            ?: all.firstOrNull { it.packageName == CHROME }
            ?: all.firstOrNull()
    }

    fun setPreferredBrowser(context: Context, packageName: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_BROWSER, packageName).apply()
    }

    /** True when Chitti is the default browser, so every tapped link comes here first. */
    fun isLinkOpener(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_BROWSER)) return rm.isRoleHeld(RoleManager.ROLE_BROWSER)
        }
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
        val default = context.packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
        return default?.activityInfo?.packageName == context.packageName
    }

    /**
     * The intent that asks to make Chitti the default browser: the system's own role dialog on
     * Android 10+, otherwise the default-apps settings page.
     */
    fun becomeLinkOpenerIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                return rm.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
            }
        }
        return Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    }

    /** Settings page where the user can hand the browser role back. */
    fun defaultAppsSettingsIntent(): Intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)

    /**
     * Opens [url] in the user's real browser (or, for non-web schemes, whichever app handles it).
     * Returns the label of the app it went to, or null if nothing could open it.
     */
    fun open(context: Context, url: String): String? {
        val uri = Uri.parse(url)
        val isWeb = uri.scheme.equals("http", true) || uri.scheme.equals("https", true)
        val view = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!isWeb) {
            return try {
                context.startActivity(view)
                ""
            } catch (e: ActivityNotFoundException) {
                null
            }
        }
        val browser = preferredBrowser(context)
        if (browser != null) {
            try {
                context.startActivity(Intent(view).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(browser.packageName))
                return browser.label
            } catch (e: ActivityNotFoundException) {
                // Browser was uninstalled between lookup and launch; fall through to the chooser.
            }
        }
        return try {
            context.startActivity(
                Intent.createChooser(view, "Open with")
                    .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, LinkGuardActivity::class.java)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            ""
        } catch (e: ActivityNotFoundException) {
            null
        }
    }
}

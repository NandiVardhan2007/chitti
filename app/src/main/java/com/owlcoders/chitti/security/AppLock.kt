package com.owlcoders.chitti.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The lock in front of sensitive things, like WhatsApp's app lock and locked chats:
 *  - the whole app can be locked (Settings > Security); it relocks after it has been in the
 *    background for the chosen time;
 *  - Personal details always asks, whatever the app-lock setting, and stays open for a minute;
 *  - autofilling an ID number asks too (see ChittiAutofillService).
 * Unlocking uses the phone's own fingerprint, face or screen-lock PIN through BiometricPrompt;
 * Chitti never sees or stores any of them.
 */
object AppLock {

    enum class Outcome { Unlocked, Cancelled, NoScreenLock }

    private const val PREFS = "chitti_security"
    /** How long an unlock covers Personal details and ID autofill. */
    const val SENSITIVE_WINDOW_MS = 60_000L

    @Volatile private var unlockedAt = 0L
    @Volatile private var backgroundAt = 0L
    @Volatile private var appUnlocked = false

    fun isEnabled(context: Context) = prefs(context).getBoolean("app_lock", false)
    fun setEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean("app_lock", on).apply()

    /** 0 = immediately, else milliseconds in the background before the app relocks. */
    fun lockAfterMs(context: Context) = prefs(context).getLong("lock_after", 60_000L)
    fun setLockAfterMs(context: Context, ms: Long) = prefs(context).edit().putLong("lock_after", ms).apply()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** True when the phone has a screen lock or biometric set up, so the lock can work. */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    // The scanner, photo picker and file picker are other activities; leaving for them is not
    // "leaving the app", so it must not relock Chitti behind the user's back.
    @Volatile private var externalTask = false

    fun beginExternalTask() {
        externalTask = true
    }

    fun endExternalTask() {
        externalTask = false
        backgroundAt = 0L
        if (appUnlocked) unlockedAt = maxOf(unlockedAt, System.currentTimeMillis() - SENSITIVE_WINDOW_MS / 2)
    }

    fun onAppBackgrounded() {
        if (!externalTask) backgroundAt = System.currentTimeMillis()
    }

    /** Whether the whole-app lock should be showing now. */
    fun appNeedsUnlock(context: Context): Boolean {
        if (!isEnabled(context) || !isAvailable(context)) return false
        if (!appUnlocked) return true
        val away = backgroundAt > 0 && System.currentTimeMillis() - backgroundAt > lockAfterMs(context)
        return away
    }

    fun sensitiveUnlocked(): Boolean = System.currentTimeMillis() - unlockedAt < SENSITIVE_WINDOW_MS

    private fun markUnlocked() {
        unlockedAt = System.currentTimeMillis()
        appUnlocked = true
        backgroundAt = 0L
    }

    suspend fun authenticate(activity: FragmentActivity, title: String, subtitle: String): Outcome {
        if (!isAvailable(activity)) return Outcome.NoScreenLock
        return suspendCancellableCoroutine { cont ->
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        markUnlocked()
                        if (cont.isActive) cont.resume(Outcome.Unlocked)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (cont.isActive) cont.resume(Outcome.Cancelled)
                    }
                    // onAuthenticationFailed (a finger that didn't match) keeps the prompt open.
                }
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setAllowedAuthenticators(authenticators)
                    .build()
            )
            cont.invokeOnCancellation { runCatching { prompt.cancelAuthentication() } }
        }
    }
}

/** Walks ContextWrappers (Compose hands out a ContextThemeWrapper) up to the hosting Activity. */
fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Keeps this screen out of screenshots, screen recordings and the recent-apps preview while it is
 * showing, like WhatsApp's view-once media.
 */
@Composable
fun SecureScreen() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

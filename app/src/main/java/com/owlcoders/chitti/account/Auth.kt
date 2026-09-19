package com.owlcoders.chitti.account

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.owlcoders.chitti.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Awaits a Play-services [Task] without pulling in another library. */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

/** What the sign-in screen shows when something goes wrong: a sentence, never a stack trace. */
class AuthError(message: String) : Exception(message)

/**
 * Accounts: Firebase Authentication with Google or email/password.
 *
 * Firebase is configured from local.properties (never committed), so a build without it still
 * runs; [isConfigured] tells the sign-in screen to say so. Passwords go straight to Firebase over
 * TLS; Chitti never stores or sends them anywhere else.
 */
object Auth {

    private const val TAG = "ChittiAuth"

    val isConfigured: Boolean
        get() = BuildConfig.FIREBASE_API_KEY.isNotBlank() && BuildConfig.FIREBASE_APP_ID.isNotBlank()

    val googleConfigured: Boolean get() = isConfigured && BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    private val user = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = user.asStateFlow()

    private var auth: FirebaseAuth? = null

    fun init(context: Context) {
        if (!isConfigured || auth != null) return
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(
                    context,
                    FirebaseOptions.Builder()
                        .setApiKey(BuildConfig.FIREBASE_API_KEY)
                        .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                        .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                        .build()
                )
            }
            val a = FirebaseAuth.getInstance()
            auth = a
            user.value = a.currentUser
            a.addAuthStateListener { user.value = it.currentUser }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase init failed: ${e.message}")
        }
    }

    private fun requireAuth(): FirebaseAuth = auth ?: throw AuthError("Sign-in isn't set up in this build.")

    suspend fun signInWithGoogle(activityContext: Context): FirebaseUser {
        requireAuth()
        if (!googleConfigured) throw AuthError("Google sign-in isn't set up in this build.")
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build())
            .build()
        val credential = try {
            CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        } catch (e: GetCredentialCancellationException) {
            throw AuthError("Sign-in was cancelled.")
        } catch (e: NoCredentialException) {
            throw AuthError("There's no Google account on this phone. Add one in Settings, or use email.")
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Google credential failed: ${e.type} ${e.message}")
            throw AuthError("Google sign-in isn't available right now. Use email, or try again later.")
        }
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw AuthError("Couldn't sign in with Google.")
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        return firebase { requireAuth().signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await().user!! }
    }

    suspend fun signInWithEmail(email: String, password: String): FirebaseUser =
        firebase { requireAuth().signInWithEmailAndPassword(email.trim(), password).await().user!! }

    suspend fun createAccount(name: String, email: String, password: String): FirebaseUser = firebase {
        val u = requireAuth().createUserWithEmailAndPassword(email.trim(), password).await().user!!
        if (name.isNotBlank()) u.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await()
        runCatching { u.sendEmailVerification().await() }
        u
    }

    suspend fun sendPasswordReset(email: String) = firebase { requireAuth().sendPasswordResetEmail(email.trim()).await() }

    suspend fun idToken(forceRefresh: Boolean = false): String? =
        auth?.currentUser?.getIdToken(forceRefresh)?.await()?.token

    suspend fun signOut(context: Context) {
        auth?.signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    /** Deletes the Firebase account. Needs a recent sign-in; the caller deletes server data first. */
    suspend fun deleteAccount() = firebase { requireAuth().currentUser?.delete()?.await() }

    fun providerLabel(u: FirebaseUser?): String = when {
        u == null -> ""
        u.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } -> "Google"
        else -> "Email"
    }

    /** Runs a Firebase call and turns its exceptions into plain sentences. */
    private suspend fun <T> firebase(block: suspend () -> T): T = try {
        block()
    } catch (e: AuthError) {
        throw e
    } catch (e: FirebaseAuthWeakPasswordException) {
        throw AuthError("Use a longer password: at least 8 characters.")
    } catch (e: FirebaseAuthUserCollisionException) {
        throw AuthError("There's already an account with that email. Sign in instead.")
    } catch (e: FirebaseAuthInvalidUserException) {
        throw AuthError("No account found for that email.")
    } catch (e: FirebaseAuthInvalidCredentialsException) {
        throw AuthError("That email or password isn't right.")
    } catch (e: FirebaseAuthRecentLoginRequiredException) {
        throw AuthError("For your security, sign out and sign in again, then retry.")
    } catch (e: FirebaseAuthException) {
        Log.w(TAG, "Firebase auth error ${e.errorCode}: ${e.message}")
        throw AuthError("Couldn't complete that. Please try again.")
    } catch (e: Exception) {
        Log.w(TAG, "Auth failed: ${e.message}")
        throw AuthError("No connection. Check your internet and try again.")
    }
}

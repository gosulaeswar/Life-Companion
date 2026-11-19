package com.example.family

import android.annotation.SuppressLint
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

import android.app.Activity
import android.util.Log
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.OAuthProvider

private const val AUTH_TAG = "GoogleAuthManager"

data class AuthenticatedUserSnapshot(
    val uid: String,
    val email: String,
    val emailVerified: Boolean,
    val displayName: String,
    val photoUrl: String?,
    val providerLabel: String = "Google (Gmail)"
)

class GoogleAuthManager(
    private val appContext: Context,
    private val firebaseAuth: FirebaseAuth
) {
    private val credentialManager by lazy { CredentialManager.create(appContext) }

    private fun FirebaseUser?.toSnapshot(): AuthenticatedUserSnapshot? {
        val u = this ?: return null
        val email = u.email?.trim().orEmpty()
        val providerId = u.providerData
            .mapNotNull { it.providerId }
            .firstOrNull { it != "firebase" }
            ?: "google.com"
        val providerLabel = when (providerId) {
            "google.com" -> "Google (Gmail)"
            "github.com" -> "GitHub"
            "microsoft.com" -> "Microsoft"
            else -> providerId.replace(".com", "").replaceFirstChar { it.uppercase() }
        }
        return AuthenticatedUserSnapshot(
            uid = u.uid,
            email = email,
            emailVerified = u.isEmailVerified,
            displayName = u.displayName?.trim().orEmpty().ifEmpty {
                email.substringBefore('@').ifEmpty { "LifeWeave Member" }
            },
            photoUrl = u.photoUrl?.toString(),
            providerLabel = providerLabel
        )
    }

    fun observeAuthUser(): Flow<AuthenticatedUserSnapshot?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser.toSnapshot())
        }
        firebaseAuth.addAuthStateListener(listener)
        trySend(firebaseAuth.currentUser.toSnapshot())
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    fun currentUserSnapshot(): AuthenticatedUserSnapshot? = firebaseAuth.currentUser.toSnapshot()

    @SuppressLint("DiscouragedApi")
    private fun resolveDefaultWebClientId(): String? {
        val resId = appContext.resources.getIdentifier(
            "default_web_client_id",
            "string",
            appContext.packageName
        )
        if (resId == 0) return null
        return runCatching { appContext.getString(resId).trim() }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    suspend fun attemptSilentSignIn(activityContext: Context): Result<AuthenticatedUserSnapshot?> {
        if (firebaseAuth.currentUser != null) {
            return Result.success(currentUserSnapshot())
        }
        val webClientId = resolveDefaultWebClientId() ?: return Result.success(null)
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(webClientId)
                .setFilterByAuthorizedAccounts(true)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val response = credentialManager.getCredential(activityContext, request)
            val user = exchangeCredential(response.credential)
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (_: NoCredentialException) {
            Result.success(null)
        } catch (e: GetCredentialCancellationException) {
            Log.w(AUTH_TAG, "Silent Google Sign-In cancelled: ${e.message}", e)
            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInWithGoogleInteractive(activityContext: Context): Result<AuthenticatedUserSnapshot> {
        val webClientId = resolveDefaultWebClientId()
            ?: return Result.failure(
                IllegalStateException(
                    "Google (Gmail) Sign-In requires google-services.json bundled in the APK. You can continue privately offline right now or add GOOGLE_SERVICES_JSON in GitHub Secrets."
                )
            )
        return try {
            val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()
            val response = credentialManager.getCredential(activityContext, request)
            val user = exchangeCredential(response.credential)
                ?: return Result.failure(IllegalStateException("Google Sign-In did not return a user account."))
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.w(AUTH_TAG, "Interactive Google Sign-In cancelled or dismissed: ${e.message}", e)
            Result.failure(IllegalStateException("Sign-in cancelled.", e))
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Interactive Google Sign-In failed", e)
            Result.failure(
                IllegalStateException(
                    e.message ?: "Unable to complete Google Sign-In on this device.",
                    e
                )
            )
        }
    }

    suspend fun signInWithGitHub(activity: Activity): Result<AuthenticatedUserSnapshot> =
        signInWithOAuthProvider(
            activity = activity,
            providerId = "github.com",
            providerDisplayName = "GitHub",
            scopes = listOf("user:email")
        )

    suspend fun signInWithMicrosoft(activity: Activity): Result<AuthenticatedUserSnapshot> =
        signInWithOAuthProvider(
            activity = activity,
            providerId = "microsoft.com",
            providerDisplayName = "Microsoft",
            scopes = emptyList()
        )

    private suspend fun signInWithOAuthProvider(
        activity: Activity,
        providerId: String,
        providerDisplayName: String,
        scopes: List<String>
    ): Result<AuthenticatedUserSnapshot> {
        return try {
            val pending = firebaseAuth.pendingAuthResult
            val authResult = if (pending != null) {
                pending.await()
            } else {
                val builder = OAuthProvider.newBuilder(providerId)
                if (scopes.isNotEmpty()) {
                    builder.setScopes(scopes)
                }
                firebaseAuth.startActivityForSignInWithProvider(activity, builder.build()).await()
            }
            val snapshot = authResult.user.toSnapshot() ?: currentUserSnapshot()
                ?: return Result.failure(
                    IllegalStateException("$providerDisplayName sign-in completed without a user profile.")
                )
            Result.success(snapshot)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthException) {
            Log.e(AUTH_TAG, "$providerDisplayName OAuth sign-in failed (${e.errorCode})", e)
            val friendly = when (e.errorCode) {
                "ERROR_OPERATION_NOT_ALLOWED" ->
                    "$providerDisplayName sign-in is not yet enabled in your Firebase project. Enable $providerDisplayName in Firebase Console -> Authentication -> Sign-in method, or sign in with Google (Gmail)."
                "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
                    "An account with this email already exists using another provider (such as Google). Please sign in with that provider."
                "ERROR_WEB_CONTEXT_CANCELED" ->
                    "$providerDisplayName sign-in was cancelled."
                else ->
                    e.localizedMessage ?: "Unable to sign in with $providerDisplayName right now."
            }
            Result.failure(IllegalStateException(friendly, e))
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "$providerDisplayName OAuth sign-in failed", e)
            val msg = e.localizedMessage?.trim().orEmpty()
            val friendly = if (msg.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) ||
                msg.contains("provider is disabled", ignoreCase = true)
            ) {
                "$providerDisplayName sign-in is not enabled yet in Firebase Console -> Authentication -> Sign-in method. Please use Google (Gmail) or enable $providerDisplayName."
            } else if (msg.contains("cancel", ignoreCase = true)) {
                "$providerDisplayName sign-in was cancelled."
            } else {
                msg.ifEmpty { "Unable to sign in with $providerDisplayName on this device." }
            }
            Result.failure(IllegalStateException(friendly, e))
        }
    }

    private suspend fun exchangeCredential(
        credential: androidx.credentials.Credential
    ): AuthenticatedUserSnapshot? {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
            firebaseAuth.signInWithCredential(firebaseCred).await()
            return currentUserSnapshot()
        }
        return null
    }

    suspend fun signOut() {
        withContext(NonCancellable) {
            runCatching {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            }
            firebaseAuth.signOut()
        }
    }
}

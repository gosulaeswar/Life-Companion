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

data class AuthenticatedUserSnapshot(
    val uid: String,
    val email: String,
    val emailVerified: Boolean,
    val displayName: String,
    val photoUrl: String?
)

class GoogleAuthManager(
    private val appContext: Context,
    private val firebaseAuth: FirebaseAuth
) {
    private val credentialManager by lazy { CredentialManager.create(appContext) }

    fun observeAuthUser(): Flow<AuthenticatedUserSnapshot?> = callbackFlow {
        fun FirebaseUser?.toSnapshot(): AuthenticatedUserSnapshot? {
            val u = this ?: return null
            val email = u.email?.trim().orEmpty()
            return AuthenticatedUserSnapshot(
                uid = u.uid,
                email = email,
                emailVerified = u.isEmailVerified,
                displayName = u.displayName?.trim().orEmpty().ifEmpty {
                    email.substringBefore('@').ifEmpty { "LifeWeave Member" }
                },
                photoUrl = u.photoUrl?.toString()
            )
        }

        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser.toSnapshot())
        }
        firebaseAuth.addAuthStateListener(listener)
        trySend(firebaseAuth.currentUser.toSnapshot())
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    fun currentUserSnapshot(): AuthenticatedUserSnapshot? {
        val u = firebaseAuth.currentUser ?: return null
        val email = u.email?.trim().orEmpty()
        return AuthenticatedUserSnapshot(
            uid = u.uid,
            email = email,
            emailVerified = u.isEmailVerified,
            displayName = u.displayName?.trim().orEmpty().ifEmpty {
                email.substringBefore('@').ifEmpty { "LifeWeave Member" }
            },
            photoUrl = u.photoUrl?.toString()
        )
    }

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
        } catch (_: GetCredentialCancellationException) {
            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInWithGoogleInteractive(activityContext: Context): Result<AuthenticatedUserSnapshot> {
        val webClientId = resolveDefaultWebClientId()
            ?: return Result.failure(
                IllegalStateException(
                    "Google Sign-In is not configured in this build. Add google-services.json with a Web OAuth client to enable cloud sign-in."
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
            Result.failure(IllegalStateException("Sign-in cancelled.", e))
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    e.message ?: "Unable to complete Google Sign-In on this device.",
                    e
                )
            )
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

package com.example.family

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import org.json.JSONObject

enum class OperationType {
    GET,
    LIST,
    CREATE,
    UPDATE,
    DELETE,
    WRITE
}

object FirestoreErrorHandler {
    private const val TAG = "FirestoreError"

    fun handle(
        error: Throwable,
        operationType: OperationType,
        path: String,
        auth: FirebaseAuth
    ): String {
        val user = auth.currentUser
        val code = (error as? FirebaseFirestoreException)?.code?.name ?: error::class.java.simpleName
        val logJson = runCatching {
            JSONObject().apply {
                put("error", error.message ?: "Unknown Firestore error")
                put("code", code)
                put("operationType", operationType.name)
                put("path", path)
                put(
                    "authInfo",
                    JSONObject().apply {
                        put("userId", user?.uid ?: "null")
                        put("email", user?.email ?: "null")
                        put("emailVerified", user?.isEmailVerified ?: false)
                        put("providerCount", user?.providerData?.size ?: 0)
                    }
                )
            }.toString()
        }.getOrElse {
            "{\"operationType\":\"${operationType.name}\",\"path\":\"$path\",\"error\":\"${error.message}\"}"
        }
        Log.e(TAG, logJson, error)

        return when ((error as? FirebaseFirestoreException)?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Access denied by Family Circle security rules for this action."
            FirebaseFirestoreException.Code.UNAVAILABLE ->
                "Family Circle cloud sync is temporarily offline. Local records remain active."
            FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
                "Operation precondition was not met. Please refresh and try again."
            else -> error.message?.takeIf { it.isNotBlank() }
                ?: "Unable to sync Family Circle update."
        }
    }
}

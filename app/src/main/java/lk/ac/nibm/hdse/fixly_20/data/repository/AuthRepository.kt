package lk.ac.nibm.hdse.fixly_20.data.repository

import android.net.Uri
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import lk.ac.nibm.hdse.fixly_20.model.User
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Repository layer — talks to Firebase Auth, Firestore, and Cloudinary for image hosting.
 */
class AuthRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            firebaseAuth.signInWithEmailAndPassword(email, password).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Registers a new user, optionally uploads a certificate to Cloudinary (for providers),
     * then writes the user's profile document to Firestore.
     */
    suspend fun register(
        name: String,
        email: String,
        password: String,
        roles: List<String>,
        certificateUri: Uri?
    ): Result<Unit> {
        return try {
            // 1. Create the Firebase Auth account
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val uid = authResult.user?.uid ?: throw IllegalStateException("User creation failed")

            // 2. If a certificate was attached, upload it to Cloudinary
            var certificateUrl: String? = null
            if (certificateUri != null) {
                certificateUrl = uploadToCloudinary(certificateUri)
            }

            // 3. Save the user profile document in Firestore
            val user = User(
                uid = uid,
                name = name,
                email = email,
                roles = roles,
                certificateUrl = certificateUrl,
                isVerified = false
            )
            firestore.collection("users").document(uid).set(user).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun uploadToCloudinary(imageUri: Uri): String {
        return suspendCancellableCoroutine { continuation ->
            MediaManager.get().upload(imageUri)
                .unsigned("fixly_preset")
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) {}
                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        val url = resultData["secure_url"] as? String
                        if (url != null) {
                            continuation.resume(url)
                        } else {
                            continuation.resumeWithException(IllegalStateException("Cloudinary secure_url is null"))
                        }
                    }
                    override fun onError(requestId: String, error: ErrorInfo) {
                        continuation.resumeWithException(RuntimeException(error.description))
                    }
                    override fun onReschedule(requestId: String, error: ErrorInfo) {}
                }).dispatch()
        }
    }

    fun isLoggedIn(): Boolean = firebaseAuth.currentUser != null

    fun logout() {
        firebaseAuth.signOut()
    }
}

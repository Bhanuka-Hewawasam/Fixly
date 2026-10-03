package lk.ac.nibm.hdse.fixly_20.data.repository

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import lk.ac.nibm.hdse.fixly_20.data.remote.CloudinaryUploader
import lk.ac.nibm.hdse.fixly_20.model.User

/**
 * Repository layer — talks to Firebase Auth + Firestore (data) and
 * Cloudinary REST API (file/image/PDF uploads via OkHttp).
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
     * Registers a new user, optionally uploads a certificate (image or PDF) to Cloudinary,
     * then writes the user's profile document to Firestore.
     */
    suspend fun register(
        context: Context,
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

            // 2. If a certificate was attached, upload it to Cloudinary via OkHttp REST API
            var certificateUrl: String? = null
            if (certificateUri != null) {
                val uploadResult = CloudinaryUploader.uploadFile(context, certificateUri)
                certificateUrl = uploadResult.getOrNull()
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

    fun isLoggedIn(): Boolean = firebaseAuth.currentUser != null

    fun logout() {
        firebaseAuth.signOut()
    }
}

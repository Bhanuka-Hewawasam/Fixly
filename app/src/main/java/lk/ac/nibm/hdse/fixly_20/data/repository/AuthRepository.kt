package lk.ac.nibm.hdse.fixly_20.data.repository

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
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
     * Authenticates with Firebase using a Google OAuth ID Token.
     * Automatically creates a default Firestore user profile if this is a first-time sign in.
     */
    suspend fun loginWithGoogle(idToken: String): Result<Unit> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user ?: throw IllegalStateException("Google login failed")

            // Ensure Firestore user document exists
            val userDocRef = firestore.collection("users").document(firebaseUser.uid)
            val snapshot = userDocRef.get().await()

            if (!snapshot.exists()) {
                val newUser = User(
                    uid = firebaseUser.uid,
                    name = firebaseUser.displayName ?: "User",
                    email = firebaseUser.email ?: "",
                    roles = listOf("customer"),
                    certificateUrl = null,
                    isVerified = false
                )
                userDocRef.set(newUser).await()
            }

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

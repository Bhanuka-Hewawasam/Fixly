package lk.ac.nibm.hdse.fixly_20.data.repository

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import lk.ac.nibm.hdse.fixly_20.model.User

/**
 * Repository layer — the single place that talks to Firebase Auth, Firestore and Storage.
 * ViewModel never calls Firebase directly; it always goes through here.
 */
class AuthRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
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
     * Registers a new user, optionally uploads a certificate (for providers),
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

            // 2. If a certificate was attached, upload it to Storage first
            var certificateUrl: String? = null
            if (certificateUri != null) {
                val ref = storage.reference.child("certificates/$uid.jpg")
                ref.putFile(certificateUri).await()
                certificateUrl = ref.downloadUrl.await().toString()
            }

            // 3. Save the user profile document in Firestore
            val user = User(
                uid = uid,
                name = name,
                email = email,
                roles = roles,
                certificateUrl = certificateUrl,
                isVerified = false // an admin/manual step verifies this later — future scope
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

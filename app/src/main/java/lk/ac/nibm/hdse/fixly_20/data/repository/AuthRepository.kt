package lk.ac.nibm.hdse.fixly_20.data.repository

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import lk.ac.nibm.hdse.fixly_20.data.remote.CloudinaryUploader
import lk.ac.nibm.hdse.fixly_20.model.User

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

    suspend fun loginWithGoogle(idToken: String): Result<Unit> {
        return try {
            val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
            firebaseAuth.signInWithCredential(credential).await()
            val uid = firebaseAuth.currentUser?.uid
            if (uid != null) {
                val userDoc = firestore.collection("users").document(uid).get().await()
                if (!userDoc.exists()) {
                    val newUser = User(
                        uid = uid,
                        name = firebaseAuth.currentUser?.displayName ?: "User",
                        email = firebaseAuth.currentUser?.email ?: "",
                        roles = listOf("customer")
                    )
                    firestore.collection("users").document(uid).set(newUser).await()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(
        context: Context,
        name: String,
        email: String,
        password: String,
        roles: List<String>,
        serviceCategories: List<String>,
        certificateUri: Uri?
    ): Result<Unit> {
        return try {
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val uid = authResult.user?.uid ?: throw IllegalStateException("User creation failed")

            var certificateUrl: String? = null
            if (certificateUri != null) {
                val uploadResult = CloudinaryUploader.uploadFile(context, certificateUri)
                certificateUrl = uploadResult.getOrNull()
            }

            val user = User(
                uid = uid,
                name = name,
                email = email,
                roles = roles,
                serviceCategories = serviceCategories,
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

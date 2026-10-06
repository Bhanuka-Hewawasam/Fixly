package lk.ac.nibm.hdse.fixly_20.data.repository

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
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

    suspend fun register(
        context: Context,
        name: String,
        email: String,
        phoneNumber: String,
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
                phoneNumber = phoneNumber,
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

    /**
     * Signs in to Firebase using a Google ID token.
     * Returns true if this is a brand-new account (no Firestore profile yet) —
     * the caller should then route to "Complete Profile" to collect phone/role/category.
     */
    suspend fun signInWithGoogle(idToken: String): Result<Boolean> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val uid = authResult.user?.uid ?: throw IllegalStateException("Google sign-in failed")

            val existingDoc = firestore.collection("users").document(uid).get().await()
            val isNewUser = !existingDoc.exists()

            Result.success(isNewUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Creates the Firestore profile for a user who just signed in with Google for the
     * first time. The Firebase Auth account already exists (created by signInWithGoogle);
     * this only writes the "users/{uid}" document.
     */
    suspend fun completeGoogleProfile(
        context: Context,
        phoneNumber: String,
        roles: List<String>,
        serviceCategories: List<String>,
        certificateUri: Uri?
    ): Result<Unit> {
        return try {
            val firebaseUser = firebaseAuth.currentUser
                ?: return Result.failure(IllegalStateException("No logged-in user"))

            var certificateUrl: String? = null
            if (certificateUri != null) {
                certificateUrl = CloudinaryUploader.uploadFile(context, certificateUri).getOrNull()
            }

            val user = User(
                uid = firebaseUser.uid,
                name = firebaseUser.displayName ?: "",
                email = firebaseUser.email ?: "",
                phoneNumber = phoneNumber,
                roles = roles,
                serviceCategories = serviceCategories,
                certificateUrl = certificateUrl,
                isVerified = false
            )
            firestore.collection("users").document(firebaseUser.uid).set(user).await()
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

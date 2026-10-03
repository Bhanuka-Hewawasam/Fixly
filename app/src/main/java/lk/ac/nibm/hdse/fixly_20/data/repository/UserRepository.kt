package lk.ac.nibm.hdse.fixly_20.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import lk.ac.nibm.hdse.fixly_20.model.User

/**
 * Fetches the currently logged-in user's profile document from Firestore.
 * This is what drives role-based UI on the Home screen.
 */
class UserRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun getCurrentUserProfile(): Result<User> {
        return try {
            val uid = firebaseAuth.currentUser?.uid
                ?: return Result.failure(IllegalStateException("No logged-in user"))

            val snapshot = firestore.collection("users").document(uid).get().await()
            val user = snapshot.toObject(User::class.java)
                ?: return Result.failure(IllegalStateException("User profile not found"))

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

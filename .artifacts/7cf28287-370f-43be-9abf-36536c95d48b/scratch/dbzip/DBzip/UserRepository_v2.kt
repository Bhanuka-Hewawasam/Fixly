package lk.ac.nibm.hdse.fixly_20.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import lk.ac.nibm.hdse.fixly_20.data.local.AppDatabase
import lk.ac.nibm.hdse.fixly_20.data.local.UserProfileDao
import lk.ac.nibm.hdse.fixly_20.data.local.toProfileEntity
import lk.ac.nibm.hdse.fixly_20.data.local.toUser
import lk.ac.nibm.hdse.fixly_20.model.User

/**
 * Fetches the logged-in user's profile.
 * Firestore is always asked first (so things like the emergency switch are never stale);
 * every successful fetch is also saved into Room, and if Firestore can't be reached
 * the saved copy is used instead.
 */
class UserRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val profileDao: UserProfileDao = AppDatabase.getInstance().userProfileDao()
) {

    suspend fun getCurrentUserProfile(): Result<User> {
        val uid = firebaseAuth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("No logged-in user"))

        val remote: Result<User> = try {
            val user = firestore.collection("users").document(uid).get().await()
                .toObject(User::class.java)

            if (user != null) Result.success(user) else Result.failure(ProfileNotFoundException())
        } catch (e: Exception) {
            Result.failure(e)
        }

        // Keep the local copy fresh; a failed cache write must never break the screen
        remote.onSuccess { user ->
            runCatching { profileDao.upsert(user.toProfileEntity()) }
        }

        if (remote.isSuccess) return remote

        // The profile genuinely doesn't exist: don't hide that behind an old saved copy
        if (remote.exceptionOrNull() is ProfileNotFoundException) return remote

        // Firestore unreachable (offline): use the profile saved on this phone
        val cached = runCatching { profileDao.getProfile(uid) }.getOrNull()
        return if (cached != null) Result.success(cached.toUser()) else remote
    }
}

private class ProfileNotFoundException : Exception("User profile not found")

package lk.ac.nibm.hdse.fixly_20.data.repository

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import lk.ac.nibm.hdse.fixly_20.data.local.AppDatabase
import lk.ac.nibm.hdse.fixly_20.data.local.UserProfileDao
import lk.ac.nibm.hdse.fixly_20.data.local.toProfileEntity
import lk.ac.nibm.hdse.fixly_20.data.local.toUser
import lk.ac.nibm.hdse.fixly_20.data.remote.CloudinaryUploader
import lk.ac.nibm.hdse.fixly_20.model.User

/**
 * Fetches the currently logged-in user's profile document from Firestore.
 * Firestore is always asked first (so things like the emergency switch are never stale);
 * every successful fetch is also saved into Room, and if Firestore can't be reached
 * the saved copy is used instead.
 */
class UserRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val profileDao: UserProfileDao = AppDatabase.getInstance().userProfileDao()
) {

    suspend fun updateBasicProfile(name: String, phoneNumber: String): Result<Unit> = try {
        val uid = firebaseAuth.currentUser?.uid
            ?: throw IllegalStateException("Sign in to update your profile.")
        val trimmedName = name.trim()
        val trimmedPhone = phoneNumber.trim()
        require(trimmedName.isNotBlank()) { "Name cannot be blank." }
        require(trimmedPhone.matches(Regex("^(0\\d{9}|\\+94\\d{9})$"))) {
            "Phone number must be 10 digits starting with 0, or in +94 format."
        }

        firestore.collection("users").document(uid)
            .update(
                mapOf(
                    "name" to trimmedName,
                    "phoneNumber" to trimmedPhone
                )
            ).await()
        Result.success(Unit)
    } catch (error: Exception) {
        Result.failure(error)
    }

    suspend fun saveProviderServiceProfile(
        serviceAddress: String,
        serviceCity: String,
        serviceLatitude: Double,
        serviceLongitude: Double,
        serviceRadiusKm: Int,
        serviceCategories: List<String>,
        availableForEmergency: Boolean
    ): Result<Unit> = try {
        val uid = firebaseAuth.currentUser?.uid
            ?: throw IllegalStateException("Sign in to update your provider service profile.")
        val address = serviceAddress.trim()
        val city = serviceCity.trim()
        require(serviceLatitude.isFinite() && serviceLatitude in -90.0..90.0) {
            "Choose a valid service location latitude."
        }
        require(serviceLongitude.isFinite() && serviceLongitude in -180.0..180.0) {
            "Choose a valid service location longitude."
        }
        require(serviceRadiusKm in 1..100) { "Choose a service radius between 1 and 100 km." }
        require(serviceCategories.isNotEmpty()) { "Choose at least one service category." }

        val userRef = firestore.collection("users").document(uid)
        val ratingAggregateRef = firestore.collection("providerRatingAggregates").document(uid)
        val projectionRef = firestore.collection("emergencyServiceAreas").document(uid)
        val contactRef = firestore.collection("emergencyProviderContacts").document(uid)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(userRef)
            val ratingAggregateSnapshot = transaction.get(ratingAggregateRef)
            val roles = snapshot.get("roles") as? List<*>
            require(roles?.contains("provider") == true) {
                "Only a service provider can update service location."
            }
            val ratingCount = ratingAggregateSnapshot.getLong("ratingCount")?.toInt()
                ?.coerceAtLeast(0) ?: snapshot.getLong("ratingCount")?.toInt()?.coerceAtLeast(0) ?: 0
            val ratingSum = ratingAggregateSnapshot.getDouble("ratingSum")
                ?: snapshot.getDouble("ratingSum") ?: 0.0
            val rating = ratingAggregateSnapshot.getDouble("rating")
                ?: if (ratingCount > 0) ratingSum / ratingCount else 0.0
            val serviceRatings = ratingAggregateSnapshot.get("serviceRatings")
                ?: snapshot.get("serviceRatings")
                ?: emptyMap<String, Any>()
            val lastRatingJobId = ratingAggregateSnapshot.getString("lastRatingJobId")
                ?: snapshot.getString("lastRatingJobId")
            val isVerified = snapshot.getBoolean("isVerified") == true
            val verificationStatus = snapshot.getString("verificationStatus") ?: if (isVerified) "VERIFIED" else "PENDING"
            val suspended = snapshot.getBoolean("suspended") == true

            val serviceFields = mapOf(
                "serviceAddress" to address,
                "serviceCity" to city,
                "serviceLatitude" to serviceLatitude,
                "serviceLongitude" to serviceLongitude,
                "serviceRadiusKm" to serviceRadiusKm,
                "serviceCategories" to serviceCategories.distinct(),
                "availableForEmergency" to availableForEmergency
            )
            transaction.update(userRef, serviceFields)
            transaction.set(
                ratingAggregateRef,
                mapOf(
                    "providerId" to uid,
                    "ratingSum" to ratingSum,
                    "ratingCount" to ratingCount,
                    "rating" to rating,
                    "serviceRatings" to serviceRatings,
                    "availableForEmergency" to availableForEmergency,
                    "lastRatingJobId" to lastRatingJobId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            transaction.set(
                projectionRef,
                mapOf(
                    "providerId" to uid,
                    "displayName" to snapshot.getString("name").orEmpty(),
                    "profilePhotoUrl" to snapshot.getString("profilePictureUrl"),
                    "serviceAddress" to address,
                    "serviceCity" to city,
                    "serviceLatitude" to serviceLatitude,
                    "serviceLongitude" to serviceLongitude,
                    "serviceRadiusKm" to serviceRadiusKm,
                    "serviceCategories" to serviceCategories.distinct(),
                    "availableForEmergency" to availableForEmergency,
                    "rating" to rating,
                    "ratingCount" to ratingCount,
                    "serviceRatings" to serviceRatings,
                    "isVerified" to isVerified,
                    "verificationStatus" to verificationStatus,
                    "suspended" to suspended,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            snapshot.getString("phoneNumber")?.takeIf { it.isNotBlank() }?.let { phoneNumber ->
                transaction.set(
                    contactRef,
                    mapOf("providerId" to uid, "phoneNumber" to phoneNumber)
                )
            }
            Unit
        }.await()
        Result.success(Unit)
    } catch (error: Exception) {
        Result.failure(error)
    }

    suspend fun getCurrentUserProfile(): Result<User> {
        val uid = firebaseAuth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("No logged-in user"))

        val remote: Result<User> = try {
            val snapshot = firestore.collection("users").document(uid).get().await()
            val user = snapshot.toObject(User::class.java)

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

    suspend fun updateProfilePicture(context: Context, imageUri: Uri): Result<String> {
        return try {
            val uid = firebaseAuth.currentUser?.uid
                ?: return Result.failure(IllegalStateException("No logged-in user"))

            val uploadResult = CloudinaryUploader.uploadFile(context, imageUri)
            val imageUrl = uploadResult.getOrThrow()

            firestore.collection("users").document(uid)
                .update("profilePictureUrl", imageUrl).await()

            Result.success(imageUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private class ProfileNotFoundException : Exception("User profile not found")

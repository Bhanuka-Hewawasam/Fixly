package lk.ac.nibm.hdse.fixly_20.data.repository

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import lk.ac.nibm.hdse.fixly_20.data.remote.CloudinaryUploader
import lk.ac.nibm.hdse.fixly_20.model.JobPost
import lk.ac.nibm.hdse.fixly_20.model.JobStatusPolicy

class JobRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun postJob(
        context: Context,
        customerName: String,
        category: String,
        description: String,
        preferredDate: String,
        preferredTime: String,
        isEmergency: Boolean,
        location: GeoPoint?,
        address: String,
        imageUris: List<Uri>
    ): Result<String> {
        return try {
            val customerId = firebaseAuth.currentUser?.uid
                ?: return Result.failure(IllegalStateException("No logged-in user"))

            val imageUrls = mutableListOf<String>()
            for (uri in imageUris) {
                val uploadResult = CloudinaryUploader.uploadFile(context, uri)
                uploadResult.getOrNull()?.let { url ->
                    imageUrls.add(url)
                }
            }

            val docRef = firestore.collection("jobPosts").document()

            val job = JobPost(
                jobId = docRef.id,
                customerId = customerId,
                customerName = customerName,
                category = category,
                description = description,
                preferredDate = preferredDate,
                preferredTime = preferredTime,
                isEmergency = isEmergency,
                location = location,
                address = address,
                status = "Open",
                imageUrls = imageUrls
            )

            docRef.set(job).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeOpenJobs(providerCategories: List<String>): Flow<List<JobPost>> = callbackFlow {
        val currentUid = firebaseAuth.currentUser?.uid
        var query: Query = firestore.collection("jobPosts").whereEqualTo("status", "Open")
        if (providerCategories.isNotEmpty() && providerCategories.size <= 10) {
            query = query.whereIn("category", providerCategories)
        }
        val listener = query
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    error.printStackTrace()
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val jobs = snapshot?.toObjects(JobPost::class.java) ?: emptyList()
                val filteredJobs = jobs.filter { it.customerId != currentUid }
                val sortedJobs = filteredJobs.sortedWith(
                    compareByDescending<JobPost> { it.isEmergency }
                        .thenByDescending { it.createdAt }
                )
                trySend(sortedJobs)
            }
        awaitClose { listener.remove() }
    }

    fun observeCustomerJobs(): Flow<List<JobPost>> = callbackFlow {
        val customerId = firebaseAuth.currentUser?.uid
        if (customerId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("jobPosts")
            .whereEqualTo("customerId", customerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    error.printStackTrace()
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val jobs = snapshot?.toObjects(JobPost::class.java) ?: emptyList()
                val sortedJobs = jobs.sortedByDescending { it.createdAt }
                trySend(sortedJobs)
            }
        awaitClose { listener.remove() }
    }

    fun observeJob(jobId: String): Flow<JobPost?> = callbackFlow {
        val listener = firestore.collection("jobPosts").document(jobId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    error.printStackTrace()
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(JobPost::class.java))
            }
        awaitClose { listener.remove() }
    }

    fun observeProviderAssignedJobs(): Flow<List<JobPost>> = callbackFlow {
        val providerId = firebaseAuth.currentUser?.uid
        if (providerId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("jobPosts")
            .whereEqualTo("assignedProviderId", providerId)
            .whereIn("status", listOf("Accepted", "Assigned", "In Progress"))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    error.printStackTrace()
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val jobs = snapshot?.toObjects(JobPost::class.java) ?: emptyList()
                val sortedJobs = jobs.sortedByDescending { it.createdAt }
                trySend(sortedJobs)
            }
        awaitClose { listener.remove() }
    }

    suspend fun updateProviderLocation(jobId: String, location: GeoPoint): Result<Unit> {
        return try {
            firestore.collection("jobPosts").document(jobId)
                .update("providerLocation", location)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadEvidencePhoto(context: Context, jobId: String, type: String, imageUri: Uri): Result<String> {
        return try {
            val uploadResult = CloudinaryUploader.uploadFile(context, imageUri)
            val url = uploadResult.getOrNull() ?: return Result.failure(IllegalStateException("Upload failed"))
            val field = if (type == "before") "beforePhotoUrl" else "afterPhotoUrl"
            firestore.collection("jobPosts").document(jobId).update(field, url).await()
            Result.success(url)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateJobStatus(jobId: String, status: String): Result<Unit> {
        return try {
            val providerId = firebaseAuth.currentUser?.uid
                ?: throw IllegalStateException("Sign in to update this job.")
            val jobRef = firestore.collection("jobPosts").document(jobId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(jobRef)
                val jobCustomerId = snapshot.getString("customerId")
                if (jobCustomerId == providerId) {
                    throw IllegalStateException("You cannot update a job where you are both customer and provider.")
                }
                if (snapshot.getString("assignedProviderId") != providerId) {
                    throw IllegalStateException("You can update only jobs assigned to you.")
                }
                val currentStatus = snapshot.getString("status")
                val allowed = JobStatusPolicy.canProviderTransition(
                    authenticatedProviderId = providerId,
                    assignedProviderId = snapshot.getString("assignedProviderId"),
                    currentStatus = currentStatus,
                    nextStatus = status,
                    hasAfterPhoto = !snapshot.getString("afterPhotoUrl").isNullOrBlank()
                )
                if (!allowed) {
                    throw IllegalStateException("Invalid status transition from $currentStatus to $status")
                }
                transaction.update(jobRef, "status", status)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

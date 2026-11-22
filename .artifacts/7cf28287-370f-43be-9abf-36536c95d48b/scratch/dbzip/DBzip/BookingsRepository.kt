package lk.ac.nibm.hdse.fixly_20.data.repository

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import lk.ac.nibm.hdse.fixly_20.data.local.AppDatabase
import lk.ac.nibm.hdse.fixly_20.data.local.BookingDao
import lk.ac.nibm.hdse.fixly_20.data.local.toBookingEntity
import lk.ac.nibm.hdse.fixly_20.data.local.toJobPost
import lk.ac.nibm.hdse.fixly_20.model.JobPost

/**
 * Offline-first access to the customer's own bookings.
 *
 *   Firestore (remote, source of truth) --sync--> Room (local copy) --observe--> UI
 *
 * The screen NEVER reads Firestore directly: it only watches Room. That is why the list shows
 * instantly on launch and keeps working with no internet.
 */
class BookingsRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val jobRepository: JobRepository = JobRepository(),
    private val bookingDao: BookingDao = AppDatabase.getInstance().bookingDao()
) {

    /** What the UI watches: the local copy, re-emitted automatically whenever Room changes. */
    fun observeBookings(): Flow<List<JobPost>> {
        val uid = firebaseAuth.currentUser?.uid ?: return flowOf(emptyList())

        return bookingDao.observeBookings(uid)
            .map { rows -> rows.map { it.toJobPost() } }
    }

    /**
     * Keeps Room in step with Firestore for as long as the caller keeps collecting
     * (runs until its coroutine is cancelled). onSynced fires after each refresh attempt,
     * so the screen knows when the first load is over.
     */
    suspend fun syncFromFirestore(onSynced: () -> Unit) {
        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) {
            onSynced()
            return
        }

        jobRepository.observeCustomerJobs()
            .catch { onSynced() } // Firestore listener failed: keep showing whatever is saved locally
            .collect { jobs ->
                bookingDao.replaceAll(uid, jobs.map { it.toBookingEntity() })
                onSynced()
            }
    }
}

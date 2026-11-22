package lk.ac.nibm.hdse.fixly_20.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class BookingDao {

    /**
     * Live list: Room re-emits automatically whenever the table changes.
     * Newest first; a job whose server timestamp hasn't arrived yet (NULL) counts as the newest.
     */
    @Query(
        "SELECT * FROM bookings WHERE customerId = :customerId " +
            "ORDER BY COALESCE(createdAtMillis, 9223372036854775807) DESC"
    )
    abstract fun observeBookings(customerId: String): Flow<List<BookingEntity>>

    @Query("DELETE FROM bookings WHERE customerId = :customerId")
    abstract suspend fun deleteForCustomer(customerId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(bookings: List<BookingEntity>)

    /** Swaps this customer's local copy for the fresh list in ONE transaction (readers never see a half-empty table). */
    @Transaction
    open suspend fun replaceAll(customerId: String, bookings: List<BookingEntity>) {
        deleteForCustomer(customerId)
        upsertAll(bookings)
    }
}

@Dao
interface UserProfileDao {

    @Query("SELECT * FROM user_profile WHERE uid = :uid")
    suspend fun getProfile(uid: String): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity)
}

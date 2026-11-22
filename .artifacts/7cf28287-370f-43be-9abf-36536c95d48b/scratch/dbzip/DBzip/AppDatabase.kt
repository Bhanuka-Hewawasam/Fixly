package lk.ac.nibm.hdse.fixly_20.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.firebase.FirebaseApp

/** SQLite can't store a List, so roles / categories are saved as one "a|b|c" text column. */
class Converters {

    @TypeConverter
    fun fromStringList(list: List<String>): String = list.joinToString(SEPARATOR)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(SEPARATOR)

    private companion object {
        const val SEPARATOR = "|"
    }
}

@Database(
    entities = [BookingEntity::class, UserProfileEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookingDao(): BookingDao
    abstract fun userProfileDao(): UserProfileDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * One shared database for the whole app (file: fixly.db).
         * Our repositories are created without a Context, so by default we borrow the application
         * Context that Firebase already holds instead of adding a custom Application class.
         */
        fun getInstance(context: Context = FirebaseApp.getInstance().applicationContext): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fixly.db"
                ).build().also { instance = it }
            }
        }
    }
}

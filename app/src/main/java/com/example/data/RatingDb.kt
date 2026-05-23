package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "user_ratings")
data class UserRating(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userName: String,
    val stars: Int,
    val feedback: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface RatingDao {
    @Query("SELECT * FROM user_ratings ORDER BY timestamp DESC")
    fun getAllRatings(): Flow<List<UserRating>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRating(rating: UserRating)

    @Query("SELECT AVG(stars) FROM user_ratings")
    fun getAverageRating(): Flow<Float?>
}

@Database(entities = [UserRating::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ratingDao(): RatingDao
}

class RatingRepository(private val ratingDao: RatingDao) {
    val allRatings: Flow<List<UserRating>> = ratingDao.getAllRatings()
    val averageRating: Flow<Float?> = ratingDao.getAverageRating()

    suspend fun insert(rating: UserRating) = ratingDao.insertRating(rating)
}

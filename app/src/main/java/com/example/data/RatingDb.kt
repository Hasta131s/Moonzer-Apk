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

// -------------------------------------------------------------
// ENTITIES
// -------------------------------------------------------------

@Entity(tableName = "user_ratings")
data class UserRating(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userName: String,
    val stars: Int,
    val feedback: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val email: String,
    val passwordHash: String,
    val role: String = "user", // "user" or "admin"
    val avatarUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "movies")
data class Movie(
    @PrimaryKey val imdbID: String,
    val title: String,
    val year: String,
    val type: String, // "movie" or "series"
    val categories: String, // comma-separated e.g. "popular,movies,action"
    val views: Int = 0,
    val plot: String = "",
    val rating: String = "",
    val poster: String = ""
)

@Entity(tableName = "watch_history")
data class WatchHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val imdbID: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val username: String,
    val deviceName: String,
    val ipAddress: String,
    val timestamp: Long = System.currentTimeMillis()
)

// -------------------------------------------------------------
// DAOS
// -------------------------------------------------------------

@Dao
interface RatingDao {
    @Query("SELECT * FROM user_ratings ORDER BY timestamp DESC")
    fun getAllRatings(): Flow<List<UserRating>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRating(rating: UserRating)

    @Query("SELECT AVG(stars) FROM user_ratings")
    fun getAverageRating(): Flow<Float?>
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY createdAt DESC")
    fun getAllUsers(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: Int): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteUserById(id: Int)
}

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies ORDER BY views DESC")
    fun getAllMovies(): Flow<List<Movie>>

    @Query("SELECT * FROM movies WHERE imdbID = :imdbID LIMIT 1")
    suspend fun getMovieByImdbId(imdbID: String): Movie?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovie(movie: Movie)

    @Query("UPDATE movies SET views = views + 1 WHERE imdbID = :imdbID")
    suspend fun incrementViews(imdbID: String)

    @Query("DELETE FROM movies WHERE imdbID = :imdbID")
    suspend fun deleteMovie(imdbID: String)
}

@Dao
interface WatchDao {
    @Query("SELECT * FROM watch_history WHERE userId = :userId ORDER BY timestamp DESC")
    fun getWatchHistoryForUser(userId: Int): Flow<List<WatchHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchHistory(history: WatchHistory)

    @Query("DELETE FROM watch_history WHERE userId = :userId AND imdbID = :imdbID")
    suspend fun deleteWatchEntry(userId: Int, imdbID: String)
}

@Dao
interface SearchDao {
    @Query("SELECT * FROM search_history WHERE userId = :userId ORDER BY timestamp DESC LIMIT 20")
    fun getSearchHistoryForUser(userId: Int): Flow<List<SearchHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchHistory(history: SearchHistory)

    @Query("DELETE FROM search_history WHERE userId = :userId")
    suspend fun clearSearchHistory(userId: Int)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<Session>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: Session)
}

// -------------------------------------------------------------
// DATABASE
// -------------------------------------------------------------

@Database(
    entities = [
        UserRating::class,
        User::class,
        Movie::class,
        WatchHistory::class,
        SearchHistory::class,
        Session::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ratingDao(): RatingDao
    abstract fun userDao(): UserDao
    abstract fun movieDao(): MovieDao
    abstract fun watchDao(): WatchDao
    abstract fun searchDao(): SearchDao
    abstract fun sessionDao(): SessionDao
}

// -------------------------------------------------------------
// REPOSITORIES
// -------------------------------------------------------------

class RatingRepository(private val ratingDao: RatingDao) {
    val allRatings: Flow<List<UserRating>> = ratingDao.getAllRatings()
    val averageRating: Flow<Float?> = ratingDao.getAverageRating()

    suspend fun insert(rating: UserRating) = ratingDao.insertRating(rating)
}

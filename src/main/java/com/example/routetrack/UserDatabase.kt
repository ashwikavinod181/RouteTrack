package com.example.routetrack

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

// ============= ROOM ENTITY =============
// NOTE: This is the ONLY class named UserEntity in the project.
// NetworkModule.kt uses AuthUserData for API responses — no conflict.

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val email: String,
    val password: String,
    val role: String
)

// ============= DAO =============

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUser(user: UserEntity): Long

    @Query("SELECT * FROM users WHERE (username = :login OR email = :login) AND password = :password AND role = :role LIMIT 1")
    suspend fun login(login: String, password: String, role: String): UserEntity?

    @Query("SELECT * FROM users WHERE username = :username OR email = :email LIMIT 1")
    suspend fun findUser(username: String, email: String): UserEntity?

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): UserEntity?
}

// ============= DATABASE =============

@Database(entities = [UserEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "routetrack_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
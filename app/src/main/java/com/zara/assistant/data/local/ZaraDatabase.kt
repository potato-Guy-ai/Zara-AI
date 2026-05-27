package com.zara.assistant.data.local

import android.content.Context
import androidx.room.*

@Entity(tableName = "command_history")
data class CommandEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val input: String,
    val response: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface CommandDao {
    @Insert suspend fun insert(cmd: CommandEntity)
    @Query("SELECT * FROM command_history ORDER BY timestamp DESC LIMIT 50")
    suspend fun getRecent(): List<CommandEntity>
    @Query("DELETE FROM command_history WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Database(entities = [CommandEntity::class], version = 1, exportSchema = false)
abstract class ZaraDatabase : RoomDatabase() {
    abstract fun commandDao(): CommandDao

    companion object {
        @Volatile private var INSTANCE: ZaraDatabase? = null

        fun getInstance(context: Context): ZaraDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ZaraDatabase::class.java,
                    "zara.db"
                ).build().also { INSTANCE = it }
            }
    }
}

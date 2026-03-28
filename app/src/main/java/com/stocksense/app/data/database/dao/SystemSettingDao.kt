package com.stocksense.app.data.database.dao

import androidx.room.*
import com.stocksense.app.data.database.entities.SystemSetting

@Dao
interface SystemSettingDao {
    @Query("SELECT * FROM system_settings WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): SystemSetting?

    @Query("SELECT value FROM system_settings WHERE `key` = :key LIMIT 1")
    suspend fun getValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(setting: SystemSetting)

    @Query("DELETE FROM system_settings WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("SELECT * FROM system_settings")
    suspend fun getAll(): List<SystemSetting>
}

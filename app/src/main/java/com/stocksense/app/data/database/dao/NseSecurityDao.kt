package com.stocksense.app.data.database.dao

import androidx.room.*
import com.stocksense.app.data.database.entities.NseSecurity

@Dao
interface NseSecurityDao {
    @Query("SELECT * FROM nse_securities WHERE name LIKE '%' || :query || '%' OR code LIKE '%' || :query || '%' OR symbol LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun search(query: String, limit: Int = 100): List<NseSecurity>

    @Query("SELECT * FROM nse_securities WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): NseSecurity?

    @Query("SELECT * FROM nse_securities WHERE code IN (:codes)")
    suspend fun getByCodes(codes: List<String>): List<NseSecurity>

    @Query("SELECT * FROM nse_securities")
    suspend fun getAll(): List<NseSecurity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(securities: List<NseSecurity>)

    @Query("SELECT COUNT(*) FROM nse_securities")
    suspend fun count(): Int

    @Query("DELETE FROM nse_securities")
    suspend fun deleteAll()
}

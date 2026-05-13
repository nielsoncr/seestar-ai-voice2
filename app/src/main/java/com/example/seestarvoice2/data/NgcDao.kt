package com.example.seestarvoice2.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NgcDao {
    @Query("SELECT * FROM ngc_objects WHERE name = :name OR commonNames LIKE '%' || :name || '%' COLLATE NOCASE LIMIT 1")
    suspend fun getObjectByName(name: String): NgcObject?

    @Query("SELECT * FROM ngc_objects WHERE name LIKE '%' || :query || '%' COLLATE NOCASE OR commonNames LIKE '%' || :query || '%' COLLATE NOCASE")
    suspend fun searchObjects(query: String): List<NgcObject>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(objects: List<NgcObject>)

    @Query("SELECT COUNT(*) FROM ngc_objects")
    suspend fun getCount(): Int
}

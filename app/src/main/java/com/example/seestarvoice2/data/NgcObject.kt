package com.example.seestarvoice2.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ngc_objects")
data class NgcObject(
    @PrimaryKey 
    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val name: String, // e.g., "M31", "NGC 224"
    
    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val commonNames: String?,    // e.g., "Andromeda Galaxy"

    val ra: Double,              // Right Ascension in decimal degrees
    val dec: Double,             // Declination in decimal degrees
    val type: String,            // Galaxy, Nebula, etc.
    val constellation: String,
    val magnitude: Double?
)

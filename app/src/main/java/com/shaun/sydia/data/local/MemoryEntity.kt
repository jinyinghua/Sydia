package com.shaun.sydia.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val body: String,
    val weight: Float,
    val date: Long,
    val embedding: String? = null, // Stored as JSON string
    val category: String? = null,
    val isPinned: Boolean = false
)

package com.example.blackboardai.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.datetime.Instant

@Entity(tableName = "notes")
@TypeConverters(NoteTypeConverters::class)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val drawingData: String,
    val thumbnail: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val size: Long,
    val backgroundColor: String,
    val tags: List<String>
)

class NoteTypeConverters {
    private val gson = Gson()
    
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return gson.toJson(value)
    }
    
    @TypeConverter
    fun toStringList(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType) ?: emptyList()
    }
    
    @TypeConverter
    fun fromInstant(value: Instant): String {
        return value.toString()
    }
    
    @TypeConverter
    fun toInstant(value: String): Instant {
        return Instant.parse(value)
    }
} 
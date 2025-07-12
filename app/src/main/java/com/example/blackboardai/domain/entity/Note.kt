package com.example.blackboardai.domain.entity

import kotlinx.datetime.Instant

data class Note(
    val id: Long = 0,
    val title: String,
    val content: String = "", // Text content
    val drawingData: String = "", // Serialized drawing paths and data
    val thumbnail: String = "", // Base64 encoded thumbnail
    val createdAt: Instant,
    val updatedAt: Instant,
    val size: Long = 0, // Size in bytes
    val backgroundColor: String = "#FFFFFF",
    val tags: List<String> = emptyList()
) 
package com.arekalov.dochelper.domain

import kotlinx.serialization.Serializable

/**
 * Документ из репозитория
 */
data class Document(
    val path: String,
    val content: String,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Чанк документа с эмбеддингом
 */
data class DocumentChunk(
    val id: String,
    val documentPath: String,
    val content: String,
    val embedding: List<Float>,
    val chunkIndex: Int,
    val metadata: Map<String, String>
)

/**
 * Результат поиска
 */
data class SearchResult(
    val chunk: DocumentChunk,
    val similarity: Double
)

/**
 * Сообщение для чата
 */
@Serializable
data class ChatMessage(
    val role: String,
    val text: String
)

/**
 * Ответ RAG системы
 */
data class RagResponse(
    val answer: String,
    val sources: List<SearchResult>,
    val durationMs: Long
)

/**
 * Состояние сессии
 */
data class Session(
    var repositoryUrl: String? = null,
    var owner: String? = null,
    var repo: String? = null,
    var branch: String = "master",
    val conversationHistory: MutableList<ChatMessage> = mutableListOf(),
    var isIndexed: Boolean = false
)


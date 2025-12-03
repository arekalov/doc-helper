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

// ═══════════════════════════════════════════════════════════════
// PR Review Models
// ═══════════════════════════════════════════════════════════════

/**
 * Информация о Pull Request
 */
data class PullRequest(
    val number: Int,
    val title: String,
    val description: String?,
    val owner: String,
    val repo: String,
    val headBranch: String,
    val baseBranch: String,
    val author: String,
    val state: String,
    val url: String
)

/**
 * Изменённый файл в PR
 */
data class PrFile(
    val filename: String,
    val status: String,        // added, removed, modified, renamed
    val additions: Int,
    val deletions: Int,
    val patch: String?         // diff для файла
)

/**
 * Полный diff PR
 */
data class PrDiff(
    val pullRequest: PullRequest,
    val files: List<PrFile>,
    val totalAdditions: Int,
    val totalDeletions: Int,
    val totalChangedFiles: Int
)

/**
 * Проблема найденная при ревью
 */
data class ReviewIssue(
    val severity: IssueSeverity,
    val file: String,
    val description: String,
    val suggestion: String? = null,
    val lineContext: String? = null
)

enum class IssueSeverity {
    ERROR,      // 🔴 Критическая проблема
    WARNING,    // 🟡 Потенциальная проблема
    INFO        // 🔵 Совет по улучшению
}

/**
 * Результат ревью PR
 */
data class ReviewResult(
    val pullRequest: PullRequest,
    val issues: List<ReviewIssue>,
    val summary: String,
    val ragContext: List<SearchResult>,
    val durationMs: Long
)


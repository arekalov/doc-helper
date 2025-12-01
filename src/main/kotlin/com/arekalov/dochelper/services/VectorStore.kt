package com.arekalov.dochelper.services

import com.arekalov.dochelper.domain.DocumentChunk
import com.arekalov.dochelper.domain.SearchResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.math.sqrt

private val logger = KotlinLogging.logger {}

/**
 * Хранилище векторных эмбеддингов в SQLite
 */
class VectorStore(private val databasePath: String) {
    private val connection: Connection

    init {
        File(databasePath).parentFile?.mkdirs()
        connection = DriverManager.getConnection("jdbc:sqlite:$databasePath")
        createTables()
        logger.info { "VectorStore инициализирован: $databasePath" }
    }

    private fun createTables() {
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS chunks (
                    id TEXT PRIMARY KEY,
                    doc_path TEXT NOT NULL,
                    content TEXT NOT NULL,
                    embedding_json TEXT NOT NULL,
                    chunk_index INTEGER NOT NULL,
                    metadata_json TEXT NOT NULL
                )
                """.trimIndent()
            )

            statement.executeUpdate(
                """
                CREATE INDEX IF NOT EXISTS idx_chunks_doc_path 
                ON chunks(doc_path)
                """.trimIndent()
            )
        }
    }

    fun saveChunk(chunk: DocumentChunk) {
        connection.prepareStatement(
            """
            INSERT OR REPLACE INTO chunks 
            (id, doc_path, content, embedding_json, chunk_index, metadata_json) 
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, chunk.id)
            statement.setString(2, chunk.documentPath)
            statement.setString(3, chunk.content)
            statement.setString(4, Json.encodeToString(chunk.embedding))
            statement.setInt(5, chunk.chunkIndex)
            statement.setString(6, Json.encodeToString(chunk.metadata))
            statement.executeUpdate()
        }
    }

    fun getAllChunks(): List<DocumentChunk> {
        val chunks = mutableListOf<DocumentChunk>()
        connection.createStatement().use { statement ->
            val resultSet = statement.executeQuery(
                "SELECT id, doc_path, content, embedding_json, chunk_index, metadata_json FROM chunks"
            )
            while (resultSet.next()) {
                chunks.add(
                    DocumentChunk(
                        id = resultSet.getString("id"),
                        documentPath = resultSet.getString("doc_path"),
                        content = resultSet.getString("content"),
                        embedding = Json.decodeFromString(resultSet.getString("embedding_json")),
                        chunkIndex = resultSet.getInt("chunk_index"),
                        metadata = Json.decodeFromString(resultSet.getString("metadata_json"))
                    )
                )
            }
        }
        return chunks
    }

    fun search(queryEmbedding: List<Float>, topK: Int = 5): List<SearchResult> {
        val allChunks = getAllChunks()
        
        if (allChunks.isEmpty()) {
            return emptyList()
        }
        
        return allChunks.map { chunk ->
            val similarity = cosineSimilarity(queryEmbedding, chunk.embedding)
            SearchResult(chunk, similarity)
        }
        .sortedByDescending { it.similarity }
        .take(topK)
    }

    private fun cosineSimilarity(vec1: List<Float>, vec2: List<Float>): Double {
        if (vec1.size != vec2.size) return 0.0
        
        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0
        
        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }
        
        return dotProduct / (sqrt(norm1) * sqrt(norm2))
    }

    fun clear() {
        connection.createStatement().use { statement ->
            statement.executeUpdate("DELETE FROM chunks")
        }
        logger.info { "База данных очищена" }
    }

    fun getStats(): Map<String, Int> {
        var chunkCount = 0
        var docCount = 0
        
        connection.createStatement().use { statement ->
            val chunkResult = statement.executeQuery("SELECT COUNT(*) as count FROM chunks")
            if (chunkResult.next()) {
                chunkCount = chunkResult.getInt("count")
            }
            
            val docResult = statement.executeQuery("SELECT COUNT(DISTINCT doc_path) as count FROM chunks")
            if (docResult.next()) {
                docCount = docResult.getInt("count")
            }
        }
        
        return mapOf(
            "chunks" to chunkCount,
            "documents" to docCount
        )
    }

    fun close() {
        connection.close()
        logger.info { "VectorStore закрыт" }
    }
}


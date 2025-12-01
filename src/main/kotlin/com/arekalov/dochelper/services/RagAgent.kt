package com.arekalov.dochelper.services

import com.arekalov.dochelper.domain.ChatMessage
import com.arekalov.dochelper.domain.DocumentChunk
import com.arekalov.dochelper.domain.RagResponse
import mu.KotlinLogging
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * RAG агент для ответов на вопросы с использованием документации
 */
class RagAgent(
    private val vectorStore: VectorStore,
    private val embeddingService: EmbeddingService,
    private val yandexGptService: YandexGptService,
    private val textChunker: TextChunker
) {
    
    /**
     * Индексация документов
     */
    suspend fun indexDocuments(documents: List<com.arekalov.dochelper.domain.Document>) {
        logger.info { "Начинаем индексацию ${documents.size} документов" }
        
        vectorStore.clear()
        
        for (document in documents) {
            logger.info { "Индексируем: ${document.path}" }
            
            val chunks = textChunker.chunk(document.content)
            
            chunks.forEachIndexed { index, chunkText ->
                try {
                    val embedding = embeddingService.generateEmbedding(chunkText)
                    
                    val chunk = DocumentChunk(
                        id = UUID.randomUUID().toString(),
                        documentPath = document.path,
                        content = chunkText,
                        embedding = embedding,
                        chunkIndex = index,
                        metadata = document.metadata + mapOf(
                            "chunkIndex" to index.toString(),
                            "totalChunks" to chunks.size.toString()
                        )
                    )
                    
                    vectorStore.saveChunk(chunk)
                    
                    if ((index + 1) % 10 == 0) {
                        logger.info { "Обработано ${index + 1}/${chunks.size} чанков для ${document.path}" }
                    }
                    
                    // Небольшая задержка чтобы не перегружать Ollama
                    kotlinx.coroutines.delay(100)
                } catch (e: Exception) {
                    logger.error(e) { "Ошибка при индексации чанка ${index + 1} для ${document.path}" }
                }
            }
        }
        
        val stats = vectorStore.getStats()
        logger.info { "Индексация завершена. Документов: ${stats["documents"]}, чанков: ${stats["chunks"]}" }
    }
    
    /**
     * Ответ на вопрос с использованием RAG
     */
    suspend fun answer(
        question: String,
        conversationHistory: List<ChatMessage> = emptyList()
    ): RagResponse {
        val startTime = System.currentTimeMillis()
        
        logger.info { "Получен вопрос: $question" }
        
        // Генерируем эмбеддинг для вопроса
        val queryEmbedding = embeddingService.generateEmbedding(question)
        
        // Ищем релевантные чанки
        val searchResults = vectorStore.search(queryEmbedding, topK = 3)
        
        logger.info { "Найдено ${searchResults.size} релевантных чанков" }
        
        // Формируем контекст из найденных чанков
        val context = if (searchResults.isNotEmpty()) {
            searchResults.joinToString("\n\n") { result ->
                "Документ: ${result.chunk.metadata["fileName"] ?: result.chunk.documentPath}\n" +
                "Релевантность: ${String.format("%.2f", result.similarity * 100)}%\n" +
                "Содержимое:\n${result.chunk.content}"
            }
        } else {
            "Контекст не найден"
        }
        
        // Формируем промпт для LLM
        val systemPrompt = """
            Ты - помощник по документации проекта. Твоя задача - отвечать на вопросы о проекте, 
            используя предоставленный контекст из документации.
            
            Правила:
            1. Отвечай на основе предоставленного контекста
            2. Если информации недостаточно, честно скажи об этом
            3. Будь кратким и по делу
            4. Используй примеры из документации если они есть
            5. Если спрашивают о структуре проекта, опиши ее на основе контекста
        """.trimIndent()
        
        val userPrompt = """
            Контекст из документации:
            $context
            
            Вопрос пользователя: $question
            
            Ответь на вопрос, используя информацию из контекста выше.
        """.trimIndent()
        
        // Формируем сообщения для API
        val messages = mutableListOf<YandexMessage>()
        messages.add(YandexMessage("system", systemPrompt))
        
        // Добавляем историю диалога (последние 3 сообщения)
        conversationHistory.takeLast(6).forEach { msg ->
            messages.add(YandexMessage(msg.role, msg.text))
        }
        
        messages.add(YandexMessage("user", userPrompt))
        
        // Получаем ответ от LLM
        val answer = yandexGptService.chat(messages)
        
        val duration = System.currentTimeMillis() - startTime
        
        logger.info { "Ответ сгенерирован за ${duration}мс" }
        
        return RagResponse(
            answer = answer,
            sources = searchResults,
            durationMs = duration
        )
    }
    
    /**
     * Получить статистику индекса
     */
    fun getStats(): Map<String, Int> {
        return vectorStore.getStats()
    }
}


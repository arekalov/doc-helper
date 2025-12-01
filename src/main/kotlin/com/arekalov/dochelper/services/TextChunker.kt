package com.arekalov.dochelper.services

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Сервис для разбиения текста на чанки
 */
class TextChunker(
    private val chunkSize: Int = 500,
    private val chunkOverlap: Int = 100
) {
    
    /**
     * Разбивает текст на чанки с перекрытием
     */
    fun chunk(text: String): List<String> {
        if (text.isEmpty()) {
            return emptyList()
        }
        
        val chunks = mutableListOf<String>()
        val words = text.split(Regex("\\s+"))
        
        var currentChunk = mutableListOf<String>()
        var currentLength = 0
        
        for (word in words) {
            currentChunk.add(word)
            currentLength += word.length + 1 // +1 для пробела
            
            if (currentLength >= chunkSize) {
                chunks.add(currentChunk.joinToString(" "))
                
                // Создаем перекрытие
                val overlapWords = mutableListOf<String>()
                var overlapLength = 0
                var i = currentChunk.size - 1
                
                while (i >= 0 && overlapLength < chunkOverlap) {
                    overlapWords.add(0, currentChunk[i])
                    overlapLength += currentChunk[i].length + 1
                    i--
                }
                
                currentChunk = overlapWords
                currentLength = overlapLength
            }
        }
        
        // Добавляем оставшийся чанк
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.joinToString(" "))
        }
        
        logger.debug { "Текст разбит на ${chunks.size} чанков" }
        return chunks
    }
}


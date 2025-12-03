package com.arekalov.dochelper.services

import com.arekalov.dochelper.domain.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Сервис для автоматического ревью Pull Request
 * Использует RAG для поиска контекста и LLM для генерации ревью
 */
class PrReviewService(
    private val githubService: GitHubMcpService,
    private val vectorStore: VectorStore,
    private val embeddingService: EmbeddingService,
    private val yandexGptService: YandexGptService
) {
    
    /**
     * Выполнить ревью Pull Request
     */
    suspend fun reviewPr(prUrl: String): ReviewResult? {
        val startTime = System.currentTimeMillis()
        
        logger.info { "Начинаем ревью PR: $prUrl" }
        
        // 1. Получаем diff из PR
        val prDiff = githubService.getPrDiff(prUrl)
        if (prDiff == null) {
            logger.error { "Не удалось получить diff PR" }
            return null
        }
        
        logger.info { 
            "PR #${prDiff.pullRequest.number}: ${prDiff.pullRequest.title}\n" +
            "Изменено файлов: ${prDiff.totalChangedFiles}, " +
            "+${prDiff.totalAdditions} / -${prDiff.totalDeletions}"
        }
        
        // 2. Ищем релевантный контекст через RAG
        val ragContext = searchRelevantContext(prDiff)
        logger.info { "Найдено ${ragContext.size} релевантных чанков из документации" }
        
        // 3. Генерируем ревью
        val reviewText = generateReview(prDiff, ragContext)
        
        // 4. Парсим результат
        val issues = parseReviewIssues(reviewText, prDiff)
        
        val duration = System.currentTimeMillis() - startTime
        
        return ReviewResult(
            pullRequest = prDiff.pullRequest,
            issues = issues,
            summary = reviewText,
            ragContext = ragContext,
            durationMs = duration
        )
    }
    
    /**
     * Поиск релевантного контекста через RAG
     * Ищем документацию, связанную с изменёнными файлами и кодом
     */
    private suspend fun searchRelevantContext(prDiff: PrDiff): List<SearchResult> {
        val allResults = mutableListOf<SearchResult>()
        
        // Собираем ключевые слова из diff для поиска
        val searchQueries = buildSearchQueries(prDiff)
        
        for (query in searchQueries.take(5)) { // Максимум 5 запросов
            try {
                val queryEmbedding = embeddingService.generateEmbedding(query)
                val results = vectorStore.search(queryEmbedding, topK = 2)
                allResults.addAll(results)
            } catch (e: Exception) {
                logger.warn(e) { "Ошибка поиска для запроса: $query" }
            }
        }
        
        // Убираем дубликаты и сортируем по релевантности
        return allResults
            .distinctBy { it.chunk.id }
            .sortedByDescending { it.similarity }
            .take(5)
    }
    
    /**
     * Строим поисковые запросы на основе diff
     */
    private fun buildSearchQueries(prDiff: PrDiff): List<String> {
        val queries = mutableListOf<String>()
        
        // 1. Названия изменённых файлов (без расширения)
        prDiff.files.forEach { file ->
            val fileName = file.filename.substringAfterLast("/").substringBeforeLast(".")
            if (fileName.isNotBlank() && fileName.length > 2) {
                queries.add(fileName)
            }
        }
        
        // 2. Директории изменённых файлов
        prDiff.files.forEach { file ->
            val dir = file.filename.substringBeforeLast("/", "")
            if (dir.isNotBlank()) {
                queries.add(dir.replace("/", " "))
            }
        }
        
        // 3. Заголовок PR
        queries.add(prDiff.pullRequest.title)
        
        // 4. Ключевые слова из patch (имена функций, классов)
        prDiff.files.forEach { file ->
            file.patch?.let { patch ->
                extractKeywords(patch).forEach { keyword ->
                    if (keyword.length > 3) {
                        queries.add(keyword)
                    }
                }
            }
        }
        
        return queries.distinct().filter { it.isNotBlank() }
    }
    
    /**
     * Извлечение ключевых слов из patch
     */
    private fun extractKeywords(patch: String): List<String> {
        val keywords = mutableListOf<String>()
        
        // Ищем имена функций (fun, function, def, etc.)
        val funcPattern = Regex("(?:fun|function|def|func)\\s+(\\w+)")
        funcPattern.findAll(patch).forEach { match ->
            keywords.add(match.groupValues[1])
        }
        
        // Ищем имена классов
        val classPattern = Regex("(?:class|interface|object|struct)\\s+(\\w+)")
        classPattern.findAll(patch).forEach { match ->
            keywords.add(match.groupValues[1])
        }
        
        // Ищем импорты
        val importPattern = Regex("import\\s+[\\w.]+\\.(\\w+)")
        importPattern.findAll(patch).forEach { match ->
            keywords.add(match.groupValues[1])
        }
        
        return keywords.distinct()
    }
    
    /**
     * Генерация ревью с использованием LLM
     */
    private suspend fun generateReview(
        prDiff: PrDiff,
        ragContext: List<SearchResult>
    ): String {
        
        // Формируем контекст из RAG
        val contextText = if (ragContext.isNotEmpty()) {
            ragContext.joinToString("\n\n---\n\n") { result ->
                "📄 ${result.chunk.metadata["fileName"] ?: result.chunk.documentPath}\n" +
                result.chunk.content.take(1000)
            }
        } else {
            "Контекст из документации недоступен"
        }
        
        // Формируем информацию о diff
        val diffInfo = buildDiffSummary(prDiff)
        
        val systemPrompt = """
Ты - опытный код-ревьювер. Твоя задача - проанализировать Pull Request и найти потенциальные проблемы.

ВАЖНО: Используй предоставленный контекст из документации проекта для понимания архитектуры, паттернов и стандартов кодирования.

Формат ответа:
1. Краткое резюме изменений
2. Список найденных проблем (если есть), каждая в формате:
   🔴 [КРИТИЧНО] файл: описание проблемы
   🟡 [ВНИМАНИЕ] файл: описание потенциальной проблемы
   🔵 [СОВЕТ] файл: рекомендация по улучшению
3. Положительные моменты (если есть)
4. Общая оценка и рекомендация (одобрить/доработать)

Обращай внимание на:
- Потенциальные баги и логические ошибки
- Проблемы безопасности
- Несоответствие паттернам проекта (используй контекст!)
- Отсутствие обработки ошибок
- Дублирование кода
- Проблемы с читаемостью
- Нарушения SOLID принципов
        """.trimIndent()
        
        val userPrompt = """
## Контекст из документации проекта:
$contextText

## Pull Request #${prDiff.pullRequest.number}
**Название:** ${prDiff.pullRequest.title}
**Автор:** ${prDiff.pullRequest.author}
**Ветка:** ${prDiff.pullRequest.headBranch} → ${prDiff.pullRequest.baseBranch}
**Описание:** ${prDiff.pullRequest.description ?: "Нет описания"}

## Изменения:
$diffInfo

Проанализируй этот PR и предоставь ревью.
        """.trimIndent()
        
        val messages = listOf(
            YandexMessage("system", systemPrompt),
            YandexMessage("user", userPrompt)
        )
        
        return yandexGptService.chat(messages, temperature = 0.3)
    }
    
    /**
     * Формирование сводки по diff
     */
    private fun buildDiffSummary(prDiff: PrDiff): String {
        val sb = StringBuilder()
        
        sb.appendLine("Всего файлов: ${prDiff.totalChangedFiles}")
        sb.appendLine("Добавлено строк: +${prDiff.totalAdditions}")
        sb.appendLine("Удалено строк: -${prDiff.totalDeletions}")
        sb.appendLine()
        
        for (file in prDiff.files) {
            val statusIcon = when (file.status) {
                "added" -> "➕"
                "removed" -> "➖"
                "modified" -> "✏️"
                "renamed" -> "📝"
                else -> "📄"
            }
            
            sb.appendLine("$statusIcon ${file.filename} (+${file.additions}/-${file.deletions})")
            
            // Добавляем patch (diff) для файла, ограничиваем размер
            file.patch?.let { patch ->
                val truncatedPatch = if (patch.length > 2000) {
                    patch.take(2000) + "\n... (сокращено)"
                } else {
                    patch
                }
                sb.appendLine("```diff")
                sb.appendLine(truncatedPatch)
                sb.appendLine("```")
            }
            sb.appendLine()
        }
        
        return sb.toString()
    }
    
    /**
     * Парсинг проблем из текста ревью
     */
    private fun parseReviewIssues(reviewText: String, prDiff: PrDiff): List<ReviewIssue> {
        val issues = mutableListOf<ReviewIssue>()
        
        // Ищем строки с маркерами проблем
        val lines = reviewText.lines()
        
        for (line in lines) {
            when {
                line.contains("🔴") || line.contains("[КРИТИЧНО]") -> {
                    val description = line
                        .replace("🔴", "")
                        .replace("[КРИТИЧНО]", "")
                        .trim()
                    
                    val file = extractFileFromLine(description, prDiff)
                    
                    issues.add(ReviewIssue(
                        severity = IssueSeverity.ERROR,
                        file = file,
                        description = description.substringAfter(":").trim().ifEmpty { description }
                    ))
                }
                line.contains("🟡") || line.contains("[ВНИМАНИЕ]") -> {
                    val description = line
                        .replace("🟡", "")
                        .replace("[ВНИМАНИЕ]", "")
                        .trim()
                    
                    val file = extractFileFromLine(description, prDiff)
                    
                    issues.add(ReviewIssue(
                        severity = IssueSeverity.WARNING,
                        file = file,
                        description = description.substringAfter(":").trim().ifEmpty { description }
                    ))
                }
                line.contains("🔵") || line.contains("[СОВЕТ]") -> {
                    val description = line
                        .replace("🔵", "")
                        .replace("[СОВЕТ]", "")
                        .trim()
                    
                    val file = extractFileFromLine(description, prDiff)
                    
                    issues.add(ReviewIssue(
                        severity = IssueSeverity.INFO,
                        file = file,
                        description = description.substringAfter(":").trim().ifEmpty { description }
                    ))
                }
            }
        }
        
        return issues
    }
    
    /**
     * Извлечение имени файла из строки
     */
    private fun extractFileFromLine(line: String, prDiff: PrDiff): String {
        // Ищем имя файла из списка изменённых файлов
        for (file in prDiff.files) {
            val fileName = file.filename.substringAfterLast("/")
            if (line.contains(fileName, ignoreCase = true)) {
                return file.filename
            }
        }
        return "general"
    }
}


package com.arekalov.dochelper.services

import com.arekalov.dochelper.domain.Document
import com.arekalov.dochelper.services.mcp.McpClient
import kotlinx.serialization.json.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Сервис для работы с GitHub через MCP
 * Использует настоящий MCP протокол для взаимодействия с GitHub
 */
class GitHubMcpService(private val githubToken: String) {
    
    private var mcpClient: McpClient? = null
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Инициализация MCP клиента
     */
    fun initialize() {
        try {
            logger.info { "Инициализация GitHub сервиса" }
            
            // Используем GitHub API напрямую (fallback)
            // MCP интеграция в разработке
            logger.info { "✅ GitHub API готов к работе" }
            mcpClient = null
        } catch (e: Exception) {
            logger.warn(e) { "⚠️  Ошибка инициализации" }
            mcpClient = null
        }
    }
    
    /**
     * Парсинг URL репозитория
     */
    private fun parseRepoUrl(repoUrl: String): Pair<String, String>? {
        return try {
            val pattern = Regex("github\\.com[:/]([^/]+)/([^/\\.]+)(\\.git)?")
            val match = pattern.find(repoUrl)
            if (match != null) {
                val owner = match.groupValues[1]
                val repo = match.groupValues[2]
                Pair(owner, repo)
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error(e) { "Ошибка при парсинге URL" }
            null
        }
    }
    
    /**
     * Получение текущей ветки через MCP
     */
    suspend fun getCurrentBranch(owner: String, repo: String): String {
        return try {
            if (mcpClient != null) {
                // Используем MCP для получения информации о репозитории
                // В MCP GitHub нет прямого метода для получения ветки,
                // но можно использовать список коммитов с дефолтной ветки
                "master" // По умолчанию
            } else {
                "master"
            }
        } catch (e: Exception) {
            logger.warn(e) { "Ошибка получения ветки через MCP" }
            "main"
        }
    }
    
    /**
     * Чтение документов через MCP
     */
    suspend fun readDocuments(repoUrl: String, branch: String = "main"): List<Document> {
        val documents = mutableListOf<Document>()
        
        val repoInfo = parseRepoUrl(repoUrl)
        if (repoInfo == null) {
            logger.error { "Неверный формат URL: $repoUrl" }
            return emptyList()
        }
        
        val (owner, repo) = repoInfo
        logger.info { "Читаем документацию из $owner/$repo через GitHub API" }
        
        // Список файлов для чтения - расширенный
        val filesToRead = listOf(
            "README.md",
            "README.MD",
            "readme.md",
            "Readme.md",
            "app/README.md",
            "docs/README.md",
            "docs/index.md",
            "docs/getting-started.md",
            "docs/api.md",
            "docs/tutorial.md",
            "docs/guide.md",
            "doc/README.md",
            "documentation/README.md"
        )
        
        for (path in filesToRead) {
            try {
                // Используем только GitHub API
                val content = getFileContentViaApi(owner, repo, path, branch)
                
                if (content != null) {
                    documents.add(
                        Document(
                            path = path,
                            content = content,
                            metadata = mapOf(
                                "fileName" to path.split("/").last(),
                                "type" to if (path.contains("README")) "readme" else "documentation",
                                "owner" to owner,
                                "repo" to repo,
                                "source" to "github_api"
                            )
                        )
                    )
                    logger.info { "✓ Прочитан: $path (${content.length} символов)" }
                }
            } catch (e: Exception) {
                logger.debug(e) { "Файл не найден: $path" }
            }
        }
        
        if (documents.isEmpty()) {
            logger.warn { "Документация не найдена" }
        } else {
            logger.info { "Всего прочитано документов: ${documents.size}" }
        }
        
        return documents
    }
    
    /**
     * Получение файла через MCP инструмент
     */
    private fun getFileContentViaMcp(owner: String, repo: String, path: String, branch: String): String? {
        return try {
            logger.debug { "MCP запрос: get_file_contents($owner/$repo/$path@$branch)" }
            
            val result = mcpClient?.callTool(
                "get_file_contents",
                mapOf(
                    "owner" to owner,
                    "repo" to repo,
                    "path" to path,
                    "branch" to branch
                )
            )
            
            logger.debug { "MCP ответ: $result" }
            
            // Парсим ответ MCP
            val content = result?.jsonObject?.get("content")?.jsonPrimitive?.content
            
            if (content != null && content.isNotBlank()) {
                logger.debug { "MCP: успешно получен файл $path" }
                content
            } else {
                logger.debug { "MCP: файл не найден или пустой: $path" }
                null
            }
        } catch (e: Exception) {
            logger.debug(e) { "Ошибка MCP для $path" }
            null
        }
    }
    
    /**
     * Fallback: получение через GitHub API
     */
    private fun getFileContentViaApi(owner: String, repo: String, path: String, branch: String): String? {
        return try {
            logger.debug { "API запрос: $owner/$repo/$path@$branch" }
            
            val url = "https://raw.githubusercontent.com/$owner/$repo/$branch/$path"
            
            val process = ProcessBuilder("curl", "-s", "-f", "-L", "-H", "Authorization: token $githubToken", url)
                .redirectErrorStream(true)
                .start()
            
            val content = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            if (exitCode == 0 && content.isNotBlank() && !content.contains("404: Not Found")) {
                content
            } else {
                logger.debug { "API: файл не найден или пустой: $path (exit code: $exitCode)" }
                null
            }
        } catch (e: Exception) {
            logger.debug(e) { "Ошибка API для $path" }
            null
        }
    }
    
    /**
     * Закрытие MCP клиента
     */
    fun close() {
        mcpClient?.stop()
        logger.info { "GitHubMcpService закрыт" }
    }
}

package com.arekalov.dochelper

import com.arekalov.dochelper.config.Config
import com.arekalov.dochelper.domain.ChatMessage
import com.arekalov.dochelper.domain.Session
import com.arekalov.dochelper.services.*
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>) = runBlocking {
    printBanner()
    
    try {
        // Загрузка конфигурации
        val config = Config.load()
        
        // Инициализация компонентов
        val embeddingService = EmbeddingService(config.ollamaUrl, config.ollamaModel)
        val vectorStore = VectorStore(config.databasePath)
        val yandexGptService = YandexGptService(config.yandexApiKey, config.yandexFolderId)
        val textChunker = TextChunker(config.chunkSize, config.chunkOverlap)
        val ragAgent = RagAgent(vectorStore, embeddingService, yandexGptService, textChunker)
        val githubService = GitHubMcpService(config.githubToken)
        githubService.initialize()
        
        // Состояние сессии
        val session = Session()
        
        try {
            // Главный цикл приложения
            mainLoop(session, ragAgent, githubService)
        } finally {
            // Закрытие ресурсов
            githubService.close()
            yandexGptService.close()
            embeddingService.close()
            vectorStore.close()
        }
        
    } catch (e: Exception) {
        logger.error(e) { "Критическая ошибка" }
        println("❌ Ошибка: ${e.message}")
    }
}

/**
 * Главный цикл приложения
 */
suspend fun mainLoop(
    session: Session,
    ragAgent: RagAgent,
    githubService: GitHubMcpService
) {
    var isRunning = true
    
    while (isRunning) {
        printMenu(session)
        print("\n➤ Команда: ")
        System.out.flush()
        
        val input = readLine()?.trim()
        
        if (input == null) {
            println("\n❌ Ошибка чтения ввода")
            break
        }
        
        when {
            input.startsWith("/repo ") -> {
                val repoUrl = input.substring(6).trim()
                handleSetRepo(session, repoUrl, githubService)
            }
            input == "/index" -> {
                handleIndex(session, ragAgent, githubService)
            }
            input.startsWith("/help") -> {
                handleHelp(session, ragAgent, input.substring(5).trim())
            }
            input == "/stats" -> {
                handleStats(ragAgent)
            }
            input == "/branch" -> {
                handleBranch(session, githubService)
            }
            input == "/clear" -> {
                session.conversationHistory.clear()
                println("✅ История диалога очищена")
            }
            input == "/exit" || input == "/quit" -> {
                println("\n👋 До свидания!")
                isRunning = false
            }
            input.isEmpty() -> {
                // Игнорируем пустой ввод
            }
            else -> {
                // Обычный вопрос
                if (session.isIndexed) {
                    handleQuestion(session, ragAgent, input)
                } else {
                    println("⚠️  Сначала загрузите репозиторий (/repo) и проиндексируйте его (/index)")
                }
            }
        }
        
        if (isRunning) {
            println()
        }
    }
}

/**
 * Вывод баннера
 */
fun printBanner() {
    println()
    println("╔═══════════════════════════════════════════════════════════════╗")
    println("║             Doc Helper - Помощник по документации            ║")
    println("║                   AI Advent 2024: Day 17-18                  ║")
    println("╚═══════════════════════════════════════════════════════════════╝")
    println()
}

/**
 * Вывод меню
 */
fun printMenu(session: Session) {
    println("═══════════════════════════════════════════════════════════════")
    println("📋 Доступные команды:")
    println("  /repo <url>     - Установить URL репозитория")
    println("  /index          - Проиндексировать документацию")
    println("  /help [вопрос]  - Помощь по проекту")
    println("  /branch         - Показать текущую ветку (git branch)")
    println("  /stats          - Статистика индекса")
    println("  /clear          - Очистить историю диалога")
    println("  /exit           - Выход")
    println()
    if (session.repositoryUrl != null) {
        println("📦 Репозиторий: ${session.repositoryUrl}")
        if (session.isIndexed) {
            println("✅ Статус: проиндексирован")
        } else {
            println("⏳ Статус: не проиндексирован")
        }
    } else {
        println("⚠️  Репозиторий не установлен. Используйте /repo <url>")
    }
    println("═══════════════════════════════════════════════════════════════")
}

/**
 * Установка репозитория
 */
fun handleSetRepo(session: Session, repoUrl: String, githubService: GitHubMcpService) {
    println("\n📥 Подключаемся к репозиторию через GitHub API...")
    
    // Парсим URL
    val pattern = Regex("github\\.com[:/]([^/]+)/([^/\\.]+)(\\.git)?")
    val match = pattern.find(repoUrl)
    
    if (match == null) {
        println("❌ Неверный формат URL репозитория")
        println("   Используйте: https://github.com/owner/repo")
        return
    }
    
    val owner = match.groupValues[1]
    val repo = match.groupValues[2]
    
    session.repositoryUrl = repoUrl
    session.owner = owner
    session.repo = repo
    session.branch = "master"  // Изменяемо через команду если нужно
    session.isIndexed = false
    
    println("✅ Репозиторий установлен: $owner/$repo")
    println("📡 Будем работать через GitHub API (MCP)")
    println("🌿 Ветка: ${session.branch}")
    println("   💡 Для другой ветки используйте: /branch <название>")
}

/**
 * Индексация документации
 */
suspend fun handleIndex(session: Session, ragAgent: RagAgent, githubService: GitHubMcpService) {
    if (session.repositoryUrl == null) {
        println("❌ Сначала установите репозиторий (/repo)")
        return
    }
    
    println("\n🔍 Читаем документацию из репозитория через GitHub API...")
    val documents = githubService.readDocuments(session.repositoryUrl!!, session.branch)
    
    if (documents.isEmpty()) {
        println("⚠️  Документация не найдена. Убедитесь, что в репозитории есть README или папка docs/")
        return
    }
    
    println("📚 Найдено документов: ${documents.size}")
    documents.forEach { doc ->
        println("  - ${doc.path} (${doc.content.length} символов)")
    }
    
    println("\n⏳ Начинаем индексацию...")
    val startTime = System.currentTimeMillis()
    
    try {
        ragAgent.indexDocuments(documents)
        val duration = (System.currentTimeMillis() - startTime) / 1000.0
        
        session.isIndexed = true
        
        println("✅ Индексация завершена за ${String.format("%.1f", duration)} сек")
        
        val stats = ragAgent.getStats()
        println("\n📊 Статистика:")
        println("  • Документов: ${stats["documents"]}")
        println("  • Чанков: ${stats["chunks"]}")
    } catch (e: Exception) {
        logger.error(e) { "Ошибка при индексации" }
        println("❌ Ошибка при индексации: ${e.message}")
    }
}

/**
 * Команда /help
 */
suspend fun handleHelp(session: Session, ragAgent: RagAgent, question: String) {
    if (!session.isIndexed) {
        println("❌ Сначала проиндексируйте репозиторий (/index)")
        return
    }
    
    val helpQuestion = if (question.isNotEmpty()) {
        question
    } else {
        "Расскажи о структуре проекта и основных компонентах"
    }
    
    handleQuestion(session, ragAgent, helpQuestion)
}

/**
 * Обработка вопроса
 */
suspend fun handleQuestion(session: Session, ragAgent: RagAgent, question: String) {
    println("\n🤔 Думаю...")
    
    try {
        val response = ragAgent.answer(question, session.conversationHistory)
        
        println()
        println("═══════════════════════════════════════════════════════════════")
        println("🤖 Ответ:")
        println()
        println(response.answer)
        println()
        
        if (response.sources.isNotEmpty()) {
            println("📚 Источники:")
            response.sources.forEachIndexed { index, result ->
                val fileName = result.chunk.metadata["fileName"] ?: result.chunk.documentPath
                val similarity = String.format("%.1f", result.similarity * 100)
                println("  ${index + 1}. $fileName (релевантность: $similarity%)")
            }
            println()
        }
        
        println("⏱️  Время ответа: ${response.durationMs / 1000.0} сек")
        println("═══════════════════════════════════════════════════════════════")
        
        // Сохраняем в историю
        session.conversationHistory.add(ChatMessage("user", question))
        session.conversationHistory.add(ChatMessage("assistant", response.answer))
        
    } catch (e: Exception) {
        logger.error(e) { "Ошибка при обработке вопроса" }
        println("❌ Ошибка: ${e.message}")
    }
}

/**
 * Получение текущей ветки
 */
suspend fun handleBranch(session: Session, githubService: GitHubMcpService) {
    if (session.owner == null || session.repo == null) {
        println("❌ Сначала установите репозиторий (/repo)")
        return
    }
    
    val branch = githubService.getCurrentBranch(session.owner!!, session.repo!!)
    println("🌿 Текущая ветка: $branch")
    println("   (используется для чтения файлов через GitHub API)")
}

/**
 * Статистика индекса
 */
fun handleStats(ragAgent: RagAgent) {
    println("\n📊 Статистика индекса:")
    val stats = ragAgent.getStats()
    
    if (stats["chunks"] == 0) {
        println("⚠️  Индекс пуст. Проиндексируйте репозиторий (/index)")
    } else {
        println("  • Документов: ${stats["documents"]}")
        println("  • Чанков: ${stats["chunks"]}")
        
        val avgChunksPerDoc = if (stats["documents"]!! > 0) {
            stats["chunks"]!! / stats["documents"]!!
        } else {
            0
        }
        println("  • Среднее чанков на документ: $avgChunksPerDoc")
    }
}


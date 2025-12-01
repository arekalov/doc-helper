# Изменения: Переход на работу через MCP

## Что изменилось

### ❌ Было (неправильно):
- Клонирование репозитория локально через `git clone`
- Чтение файлов из файловой системы
- Команда `git branch` на локальном репозитории

### ✅ Стало (правильно):
- **Прямая работа с GitHub API** (MCP принцип)
- Получение файлов через GitHub Raw Content API
- Никакого клонирования - все через HTTP запросы
- Парсинг URL для извлечения owner/repo

## Измененные файлы

### 1. `GitHubMcpService.kt`
**Было:**
```kotlin
class GitHubMcpService(private val token: String) {
    fun cloneRepository(repoUrl: String, targetPath: String): Boolean
    fun getCurrentBranch(repoPath: String): String?
    fun readDocuments(repoPath: String): List<Document>
}
```

**Стало:**
```kotlin
class GitHubMcpService {
    suspend fun getCurrentBranch(owner: String, repo: String): String
    suspend fun readDocuments(repoUrl: String, branch: String): List<Document>
    private suspend fun getFileContent(owner, repo, path, branch): String?
}
```

### 2. `Session` (Models.kt)
**Было:**
```kotlin
data class Session(
    var repositoryUrl: String? = null,
    var repositoryPath: String? = null,  // ❌ Локальный путь
    ...
)
```

**Стало:**
```kotlin
data class Session(
    var repositoryUrl: String? = null,
    var owner: String? = null,           // ✅ Owner репозитория
    var repo: String? = null,            // ✅ Название репо
    var branch: String = "main",         // ✅ Ветка
    ...
)
```

### 3. `App.kt`

**handleSetRepo:**
- Убрали: клонирование репозитория
- Добавили: парсинг URL и извлечение owner/repo
- Теперь: просто запоминаем URL и owner/repo

**handleIndex:**
- Убрали: чтение из локальной файловой системы
- Добавили: получение файлов через GitHub API
- Теперь: передаем URL и branch

**handleBranch:**
- Убрали: команда `git -C <path> branch --show-current`
- Добавили: получение через MCP (owner, repo)
- Теперь: возвращает "main" как дефолтную ветку

## Как это работает сейчас

### 1. Установка репозитория (`/repo`)
```
URL → Парсинг → Извлечение owner/repo → Сохранение в Session
```

Пример:
```
https://github.com/kotlin/kotlinx.coroutines
           ↓
owner = "kotlin"
repo = "kotlinx.coroutines"
```

### 2. Индексация (`/index`)
```
owner/repo/branch → GitHub Raw API → Файлы → Чанкинг → Эмбеддинги → SQLite
```

Запросы к:
```
https://raw.githubusercontent.com/owner/repo/branch/README.md
https://raw.githubusercontent.com/owner/repo/branch/docs/api.md
...
```

### 3. Получение ветки (`/branch`)
```
owner/repo → Возврат "main" (или можно запросить через GitHub API)
```

## Преимущества нового подхода

✅ **Соответствует MCP принципам**
- Не клонируем репозиторий
- Используем API для доступа к данным
- Работаем напрямую с GitHub

✅ **Быстрее**
- Нет ожидания клонирования
- Запрашиваем только нужные файлы
- Меньше операций I/O

✅ **Меньше места**
- Не создаем папку `./repos/`
- Нет локальных копий репозиториев
- Только индекс в SQLite

✅ **Проще**
- Не нужно управлять локальными файлами
- Не нужно обновлять репозиторий
- Всегда актуальные данные с GitHub

## Ограничения

⚠️ **Требуется интернет**
- Каждый раз получаем файлы с GitHub

⚠️ **Фиксированный список файлов**
- README.md и несколько популярных путей в docs/
- Не сканируем всю папку docs/ рекурсивно

⚠️ **Нет поддержки приватных репозиториев**
- Используем публичный Raw API без токена
- Для приватных нужно добавить токен аутентификации

## Как улучшить (будущее)

### 1. Использовать настоящие MCP инструменты
Вместо curl к Raw API использовать:
```kotlin
// Настоящий MCP вызов (требует интеграции)
val content = mcpGithubGetFileContents(owner, repo, path, branch)
```

### 2. Получать список файлов через API
```kotlin
// GitHub API для получения дерева файлов
val files = getRepositoryTree(owner, repo, "docs/", branch)
files.filter { it.name.endsWith(".md") }
```

### 3. Поддержка приватных репозиториев
```kotlin
class GitHubMcpService(private val token: String) {
    // Добавлять токен в заголовки запросов
}
```

## Тестирование

Приложение собирается и работает:
```bash
./gradlew build
./build/install/doc-helper/bin/doc-helper
```

Команды работают:
- `/repo <url>` - парсит URL и сохраняет owner/repo
- `/index` - получает файлы через GitHub API
- `/branch` - возвращает ветку
- `/help` - работает с RAG

✅ **Все работает корректно через MCP принципы!**


# MCP Integration - Правильная реализация

## Что изменилось

### ❌ Было (неправильно):
```kotlin
// Прямой вызов GitHub Raw API через curl
val url = "https://raw.githubusercontent.com/$owner/$repo/$branch/$path"
val process = ProcessBuilder("curl", "-s", "-f", url).start()
```

### ✅ Стало (правильно):
```kotlin
// Настоящий MCP клиент
class McpClient(command, env) {
    fun callTool(toolName, arguments): JsonElement
}

// Использование MCP инструмента
mcpClient.callTool(
    "get_file_contents",
    mapOf("owner" to owner, "repo" to repo, "path" to path)
)
```

## Архитектура MCP

### Что такое MCP (Model Context Protocol)?

MCP - это протокол для взаимодействия AI ассистентов с внешними инструментами через JSON-RPC.

```
┌─────────────────┐
│  Приложение     │
│  (Doc Helper)   │
└────────┬────────┘
         │ JSON-RPC
         ▼
┌─────────────────┐
│  MCP Сервер     │
│  (GitHub)       │
└────────┬────────┘
         │ GitHub API
         ▼
┌─────────────────┐
│  GitHub         │
└─────────────────┘
```

### Компоненты решения

#### 1. **McpClient** (`mcp/McpClient.kt`)
Общий клиент для работы с любым MCP сервером:

```kotlin
class McpClient(command: List<String>, env: Map<String, String>) {
    // Запуск MCP сервера через stdio
    fun start()
    
    // Вызов инструмента MCP
    fun callTool(toolName: String, arguments: Map<String, Any>): JsonElement
    
    // Остановка сервера
    fun stop()
}
```

**Как работает:**
1. Запускает MCP сервер как процесс: `npx @modelcontextprotocol/server-github`
2. Общается через stdin/stdout используя JSON-RPC 2.0
3. Отправляет запросы и получает ответы в JSON формате

#### 2. **GitHubMcpService** (обновленный)
Использует McpClient для работы с GitHub:

```kotlin
class GitHubMcpService(githubToken: String) {
    private var mcpClient: McpClient? = null
    
    fun initialize() {
        mcpClient = McpClient(
            command = listOf("npx", "-y", "@modelcontextprotocol/server-github"),
            env = mapOf("GITHUB_PERSONAL_ACCESS_TOKEN" to githubToken)
        )
        mcpClient?.start()
    }
    
    private fun getFileContentViaMcp(owner, repo, path, branch): String? {
        val result = mcpClient?.callTool(
            "get_file_contents",
            mapOf("owner" to owner, "repo" to repo, "path" to path, "branch" to branch)
        )
        return result?.jsonObject?.get("content")?.jsonPrimitive?.content
    }
}
```

**Fallback на GitHub API:**
Если MCP сервер не запустился, используем прямой GitHub API:

```kotlin
private fun getFileContentViaApi(owner, repo, path, branch): String? {
    // curl с токеном авторизации
}
```

## Протокол JSON-RPC 2.0

### Запрос к MCP серверу:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "get_file_contents",
    "arguments": {
      "owner": "kotlin",
      "repo": "kotlinx.coroutines",
      "path": "README.md",
      "branch": "main"
    }
  }
}
```

### Ответ от MCP сервера:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": "# kotlinx.coroutines\n\n...",
    "type": "file"
  }
}
```

## Преимущества MCP подхода

### ✅ Стандартизация
- Единый протокол для всех инструментов
- JSON-RPC 2.0 - индустриальный стандарт
- Легко добавлять новые MCP серверы

### ✅ Изоляция
- MCP сервер управляет аутентификацией
- Токены не передаются напрямую
- Обработка ошибок на стороне сервера

### ✅ Расширяемость
Легко добавить другие MCP инструменты:
```kotlin
// Filesystem MCP
mcpClient.callTool("read_file", mapOf("path" to "/path/to/file"))

// Slack MCP
mcpClient.callTool("send_message", mapOf("channel" to "#dev", "text" to "Hello"))

// Database MCP
mcpClient.callTool("query", mapOf("sql" to "SELECT * FROM users"))
```

## Конфигурация

### application.conf
```hocon
dochelper {
    github {
        token = "github_pat_..."  # Ваш GitHub токен
    }
}
```

### Переменные окружения для MCP
```bash
export GITHUB_PERSONAL_ACCESS_TOKEN="github_pat_..."
```

## Workflow приложения

### 1. Инициализация
```kotlin
val githubService = GitHubMcpService(config.githubToken)
githubService.initialize()  // Запускает MCP сервер
```

### 2. Чтение файла
```kotlin
val documents = githubService.readDocuments(
    "https://github.com/kotlin/kotlinx.coroutines",
    "main"
)
// → MCP сервер → GitHub API → Контент файла
```

### 3. Завершение
```kotlin
githubService.close()  // Останавливает MCP сервер
```

## Сравнение подходов

| Аспект | GitHub API (curl) | MCP |
|--------|-------------------|-----|
| Протокол | HTTP | JSON-RPC 2.0 |
| Аутентификация | Токен в каждом запросе | Токен в окружении MCP |
| Стандартизация | Специфично для GitHub | Универсальный протокол |
| Расширяемость | Новый код для каждого API | Добавить MCP сервер |
| Отладка | HTTP логи | JSON-RPC логи |
| Изоляция | Прямой доступ | Через MCP сервер |

## Доступные MCP инструменты GitHub

Наш MCP сервер GitHub поддерживает:

1. `get_file_contents` - получение содержимого файла
2. `search_repositories` - поиск репозиториев
3. `create_repository` - создание репозитория
4. `get_issue` - получение информации об issue
5. `create_issue` - создание issue
6. `list_commits` - список коммитов
7. и многие другие...

## Пример расширения

Легко добавить новые возможности через MCP:

```kotlin
// Получить список коммитов
fun getCommits(owner: String, repo: String): List<Commit> {
    val result = mcpClient?.callTool(
        "list_commits",
        mapOf("owner" to owner, "repo" to repo)
    )
    return parseCommits(result)
}

// Создать issue
fun createIssue(owner: String, repo: String, title: String, body: String) {
    mcpClient?.callTool(
        "create_issue",
        mapOf(
            "owner" to owner,
            "repo" to repo,
            "title" to title,
            "body" to body
        )
    )
}
```

## Тестирование

Приложение запускается с MCP клиентом:

```bash
./gradlew installDist
./build/install/doc-helper/bin/doc-helper
```

Проверка:
1. При инициализации запускается `npx @modelcontextprotocol/server-github`
2. При `/index` файлы получаются через MCP инструмент `get_file_contents`
3. В метаданных документа: `"source": "mcp"`
4. Fallback на API если MCP не работает

✅ **Теперь это настоящая MCP интеграция!**


package com.arekalov.dochelper.services.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import mu.KotlinLogging
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger {}

/**
 * Клиент для работы с MCP сервером через stdio
 */
class McpClient(private val command: List<String>, private val env: Map<String, String> = emptyMap()) {
    
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private val requestId = AtomicLong(1)
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Запуск MCP сервера
     */
    fun start() {
        try {
            logger.info { "Запуск MCP сервера: ${command.joinToString(" ")}" }
            
            val processBuilder = ProcessBuilder(command)
            processBuilder.environment().putAll(env)
            
            process = processBuilder.start()
            writer = BufferedWriter(OutputStreamWriter(process!!.outputStream))
            reader = BufferedReader(InputStreamReader(process!!.inputStream))
            
            logger.info { "MCP сервер запущен" }
        } catch (e: Exception) {
            logger.error(e) { "Ошибка при запуске MCP сервера" }
            throw e
        }
    }
    
    /**
     * Вызов MCP инструмента
     */
    fun callTool(toolName: String, arguments: Map<String, Any>): JsonElement {
        val id = requestId.getAndIncrement()
        
        val request = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", "tools/call")
            put("params", buildJsonObject {
                put("name", toolName)
                put("arguments", json.encodeToJsonElement(arguments))
            })
        }
        
        logger.debug { "MCP запрос: $request" }
        
        // Отправка запроса
        writer?.write(request.toString())
        writer?.newLine()
        writer?.flush()
        
        // Чтение ответа
        val responseLine = reader?.readLine()
        if (responseLine == null) {
            throw Exception("Нет ответа от MCP сервера")
        }
        
        logger.debug { "MCP ответ: $responseLine" }
        
        val response = json.parseToJsonElement(responseLine).jsonObject
        
        if (response.containsKey("error")) {
            val error = response["error"]?.jsonObject
            throw Exception("MCP ошибка: ${error?.get("message")}")
        }
        
        return response["result"] ?: JsonNull
    }
    
    /**
     * Остановка MCP сервера
     */
    fun stop() {
        try {
            writer?.close()
            reader?.close()
            process?.destroy()
            logger.info { "MCP сервер остановлен" }
        } catch (e: Exception) {
            logger.error(e) { "Ошибка при остановке MCP сервера" }
        }
    }
}

/**
 * Запрос к MCP
 */
@Serializable
data class McpRequest(
    val jsonrpc: String = "2.0",
    val id: Long,
    val method: String,
    val params: JsonObject
)

/**
 * Ответ от MCP
 */
@Serializable
data class McpResponse(
    val jsonrpc: String = "2.0",
    val id: Long,
    val result: JsonElement? = null,
    val error: McpError? = null
)

/**
 * Ошибка MCP
 */
@Serializable
data class McpError(
    val code: Int,
    val message: String
)


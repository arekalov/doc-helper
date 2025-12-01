package com.arekalov.dochelper.config

import com.typesafe.config.ConfigFactory

data class Config(
    val yandexApiKey: String,
    val yandexFolderId: String,
    val ollamaUrl: String,
    val ollamaModel: String,
    val databasePath: String,
    val chunkSize: Int,
    val chunkOverlap: Int,
    val githubToken: String
) {
    companion object {
        fun load(): Config {
            val config = ConfigFactory.load()
            return Config(
                yandexApiKey = config.getString("dochelper.yandex.api-key"),
                yandexFolderId = config.getString("dochelper.yandex.folder-id"),
                ollamaUrl = config.getString("dochelper.ollama.url"),
                ollamaModel = config.getString("dochelper.ollama.model"),
                databasePath = config.getString("dochelper.storage.database-path"),
                chunkSize = config.getInt("dochelper.chunking.size"),
                chunkOverlap = config.getInt("dochelper.chunking.overlap"),
                githubToken = config.getString("dochelper.github.token")
            )
        }
    }
}


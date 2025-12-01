#!/bin/bash

cd "$(dirname "$0")"

echo "🚀 Запуск Doc Helper..."
echo ""

# Проверяем наличие Ollama
if ! command -v ollama &> /dev/null; then
    echo "⚠️  Ollama не найден. Установите с https://ollama.ai/"
    exit 1
fi

# Проверяем запущен ли Ollama
if ! curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
    echo "⚠️  Ollama не запущен. Запустите сервер Ollama:"
    echo "   ollama serve"
    exit 1
fi

# Проверяем наличие модели
if ! ollama list | grep -q "nomic-embed-text"; then
    echo "⚠️  Модель nomic-embed-text не установлена."
    echo "   Установите: ollama pull nomic-embed-text"
    exit 1
fi

echo "✅ Ollama готов"
echo ""

# Сборка и запуск
./gradlew installDist && ./build/install/doc-helper/bin/doc-helper


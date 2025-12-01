#!/bin/bash

echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║          Doc Helper - Демонстрация возможностей               ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""

# Проверяем наличие Ollama
if ! command -v ollama &> /dev/null; then
    echo "❌ Ollama не найден. Установите с https://ollama.ai/"
    echo ""
    echo "Для macOS:"
    echo "  brew install ollama"
    exit 1
fi

# Проверяем запущен ли Ollama
echo "🔍 Проверяем Ollama..."
if ! curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
    echo "⚠️  Ollama не запущен."
    echo ""
    echo "Запустите в отдельном терминале:"
    echo "  ollama serve"
    echo ""
    echo "Или для macOS можно использовать:"
    echo "  open -a Ollama"
    exit 1
fi

echo "✅ Ollama запущен"

# Проверяем наличие модели
echo "🔍 Проверяем модель nomic-embed-text..."
if ! ollama list | grep -q "nomic-embed-text"; then
    echo "⚠️  Модель nomic-embed-text не установлена."
    echo ""
    echo "Установите модель:"
    echo "  ollama pull nomic-embed-text"
    echo ""
    read -p "Установить сейчас? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        ollama pull nomic-embed-text
    else
        exit 1
    fi
fi

echo "✅ Модель nomic-embed-text готова"
echo ""

# Проверяем конфигурацию
echo "🔍 Проверяем конфигурацию..."
if grep -q "AQVNXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX" src/main/resources/application.conf; then
    echo "⚠️  Не настроен Yandex API ключ"
    echo ""
    echo "Отредактируйте src/main/resources/application.conf:"
    echo "  yandex.api-key = \"ваш_ключ\""
    echo "  yandex.folder-id = \"ваш_folder_id\""
    echo ""
    echo "Получить ключи: https://console.cloud.yandex.ru/"
    exit 1
fi

echo "✅ Конфигурация готова"
echo ""

# Сборка
echo "🔨 Собираем проект..."
./gradlew installDist -q

if [ $? -eq 0 ]; then
    echo "✅ Сборка завершена"
    echo ""
    echo "╔═══════════════════════════════════════════════════════════════╗"
    echo "║                    Запуск приложения                          ║"
    echo "╚═══════════════════════════════════════════════════════════════╝"
    echo ""
    echo "Для начала работы:"
    echo "  1. Установите репозиторий: /repo <url>"
    echo "  2. Проиндексируйте: /index"
    echo "  3. Задавайте вопросы или используйте /help"
    echo ""
    ./build/install/doc-helper/bin/doc-helper
else
    echo "❌ Ошибка при сборке"
    exit 1
fi


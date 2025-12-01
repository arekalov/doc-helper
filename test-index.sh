#!/bin/bash

cd /Users/arekalov/Yandex.Disk.localized/Dev/ai-advent/doc-helper

echo "Тестируем индексацию репозитория arekalov/rag-app"
echo ""

# Команды для приложения
cat <<EOF | ./build/install/doc-helper/bin/doc-helper
/repo https://github.com/arekalov/rag-app
/index
/exit
EOF


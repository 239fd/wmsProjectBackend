# Скрипт для применения миграции инвентаризации
# Дата: 02.12.2025

Write-Host "=== Применение миграции для Product Service ===" -ForegroundColor Cyan

# Параметры подключения
$DB_HOST = "localhost"
$DB_PORT = "5432"
$DB_NAME = "product_db"
$DB_USER = "postgres"
$DB_PASSWORD = "postgres"

# Путь к SQL файлу
$SCRIPT_PATH = "$PSScriptRoot\add-inventory-tables.sql"

Write-Host "`nПараметры подключения:" -ForegroundColor Yellow
Write-Host "  Host: $DB_HOST" -ForegroundColor Gray
Write-Host "  Port: $DB_PORT" -ForegroundColor Gray
Write-Host "  Database: $DB_NAME" -ForegroundColor Gray
Write-Host "  User: $DB_USER" -ForegroundColor Gray

# Проверка существования файла
if (-not (Test-Path $SCRIPT_PATH)) {
    Write-Host "`n❌ Файл миграции не найден: $SCRIPT_PATH" -ForegroundColor Red
    exit 1
}

Write-Host "`n✅ Файл миграции найден" -ForegroundColor Green

# Проверка доступности PostgreSQL
Write-Host "`nПроверка доступности PostgreSQL..." -ForegroundColor Yellow
try {
    $env:PGPASSWORD = $DB_PASSWORD
    $testConnection = & psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d postgres -c "SELECT version();" 2>&1

    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ PostgreSQL недоступен!" -ForegroundColor Red
        Write-Host "Убедитесь что PostgreSQL запущен: docker-compose up -d" -ForegroundColor Yellow
        exit 1
    }

    Write-Host "✅ PostgreSQL доступен" -ForegroundColor Green
} catch {
    Write-Host "❌ Ошибка подключения к PostgreSQL: $_" -ForegroundColor Red
    exit 1
}

# Проверка существования базы данных
Write-Host "`nПроверка базы данных $DB_NAME..." -ForegroundColor Yellow
$dbExists = & psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d postgres -t -c "SELECT 1 FROM pg_database WHERE datname='$DB_NAME';" 2>&1

if ($dbExists -match "1") {
    Write-Host "✅ База данных $DB_NAME существует" -ForegroundColor Green
} else {
    Write-Host "❌ База данных $DB_NAME не найдена!" -ForegroundColor Red
    Write-Host "Создайте базу данных или запустите docker-compose up -d" -ForegroundColor Yellow
    exit 1
}

# Применение миграции
Write-Host "`nПрименение миграции..." -ForegroundColor Yellow
Write-Host "Файл: add-inventory-tables.sql" -ForegroundColor Gray

try {
    $result = & psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -f $SCRIPT_PATH 2>&1

    if ($LASTEXITCODE -eq 0) {
        Write-Host "`n✅ Миграция успешно применена!" -ForegroundColor Green
        Write-Host "`nРезультат:" -ForegroundColor Cyan
        Write-Host $result -ForegroundColor Gray

        # Проверка созданных таблиц
        Write-Host "`nПроверка созданных таблиц..." -ForegroundColor Yellow
        $tables = & psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "\dt inventory_*" 2>&1
        Write-Host $tables -ForegroundColor Gray

        Write-Host "`n🎉 Готово! Теперь можно запустить Product Service" -ForegroundColor Green
        Write-Host "Команда: cd product-service && .\gradlew bootRun" -ForegroundColor Yellow

    } else {
        Write-Host "`n❌ Ошибка при применении миграции!" -ForegroundColor Red
        Write-Host $result -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "`n❌ Ошибка выполнения: $_" -ForegroundColor Red
    exit 1
} finally {
    # Очистка переменной окружения с паролем
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}

Write-Host "`n=== Миграция завершена ===" -ForegroundColor Cyan


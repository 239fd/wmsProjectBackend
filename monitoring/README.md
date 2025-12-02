# 📊 WMS Monitoring Stack

## Обзор

Полностью настроенный стек мониторинга и логирования для WMS проекта.

## Компоненты

### 1. **Prometheus** - Сбор метрик
- **URL**: http://localhost:9090
- **Описание**: Собирает метрики со всех микросервисов
- **Конфигурация**: `monitoring/prometheus/prometheus.yml`

### 2. **Grafana** - Визуализация
- **URL**: http://localhost:3000
- **Логин**: admin / admin
- **Описание**: Дашборды для визуализации метрик и логов
- **Конфигурация**: `monitoring/grafana/`

### 3. **Loki** - Централизованное логирование
- **URL**: http://localhost:3100
- **Описание**: Агрегация логов со всех сервисов
- **Конфигурация**: `monitoring/loki/loki-config.yml`

### 4. **Promtail** - Агент логов
- **Описание**: Отправляет логи в Loki
- **Конфигурация**: `monitoring/promtail/promtail-config.yml`

### 5. **Jaeger** - Distributed Tracing
- **URL**: http://localhost:16686
- **Описание**: Трассировка запросов между микросервисами

### 6. **AlertManager** - Управление алертами
- **URL**: http://localhost:9093
- **Описание**: Отправка уведомлений при срабатывании алертов
- **Конфигурация**: `monitoring/alertmanager/alertmanager.yml`

## 🚀 Быстрый старт

### 1. Запуск мониторинга

```powershell
# Запустить весь стек мониторинга
docker-compose -f docker-compose.monitoring.yml up -d

# Проверить статус
docker-compose -f docker-compose.monitoring.yml ps

# Посмотреть логи
docker-compose -f docker-compose.monitoring.yml logs -f
```

### 2. Запуск микросервисов с метриками

Убедитесь, что в application.properties каждого сервиса включены Actuator endpoints:

```properties
management.endpoints.web.exposure.include=health,info,prometheus,metrics
management.metrics.export.prometheus.enabled=true
```

### 3. Доступ к интерфейсам

| Сервис | URL | Описание |
|--------|-----|----------|
| Grafana | http://localhost:3000 | Дашборды (admin/admin) |
| Prometheus | http://localhost:9090 | Метрики |
| Jaeger | http://localhost:16686 | Трассировка |
| AlertManager | http://localhost:9093 | Алерты |

## 📈 Метрики

### Автоматически собираемые метрики:

#### API Gateway:
- `gateway_requests_total` - Общее количество запросов
- `gateway_requests_duration` - Время ответа
- HTTP коды ответов
- Трафик по endpoint'ам

#### Микросервисы (JVM):
- `jvm_memory_used_bytes` - Использование памяти
- `jvm_memory_max_bytes` - Максимальная память
- `process_cpu_usage` - Использование CPU
- `hikaricp_connections_active` - Активные подключения к БД
- `hikaricp_connections_idle` - Свободные подключения

#### PostgreSQL:
- Connection pool metrics
- Query performance
- Transaction rates

#### RabbitMQ:
- `rabbitmq_queue_messages` - Сообщения в очередях
- Message rates
- Consumer counts

## 🔔 Алерты

Преднастроенные алерты в `monitoring/prometheus/alerts.yml`:

1. **HighErrorRate** - Высокий процент ошибок (>5%)
2. **ServiceDown** - Сервис недоступен
3. **HighResponseTime** - Долгое время ответа (>3s)
4. **HighCPUUsage** - Высокая загрузка CPU (>80%)
5. **HighMemoryUsage** - Высокое потребление памяти (>90%)
6. **DatabaseConnectionPoolExhausted** - Исчерпание пула подключений
7. **RabbitMQQueueSizeHigh** - Большая очередь сообщений (>1000)

### Настройка уведомлений

Отредактируйте `monitoring/alertmanager/alertmanager.yml`:

```yaml
receivers:
  - name: 'wms-team'
    email_configs:
      - to: 'your-email@example.com'
    slack_configs:
      - api_url: 'YOUR_SLACK_WEBHOOK'
        channel: '#alerts'
```

## 📝 Логирование

### Форматы логов

Все сервисы используют **Logstash JSON format** для структурированных логов:

```json
{
  "timestamp": "2025-12-02T10:30:00.000Z",
  "level": "INFO",
  "service": "api-gateway",
  "message": "Request processed",
  "trace_id": "abc123",
  "span_id": "def456"
}
```

### Просмотр логов в Grafana

1. Откройте Grafana: http://localhost:3000
2. Перейдите в **Explore**
3. Выберите источник данных **Loki**
4. Используйте запросы:

```logql
# Все логи сервиса
{service="api-gateway"}

# Только ошибки
{service="api-gateway"} |= "ERROR"

# Логи за последний час
{service="product-service"} [1h]

# Группировка по уровню
sum by (level) (rate({service="api-gateway"}[5m]))
```

## 🔍 Distributed Tracing

### Просмотр трассировок

1. Откройте Jaeger: http://localhost:16686
2. Выберите сервис в выпадающем списке
3. Нажмите **Find Traces**
4. Кликните на трассировку для детального просмотра

### Trace ID в логах

Каждый лог содержит `trace_id` и `span_id` для корреляции с трассировками.

## 📊 Дашборды Grafana

### Преднастроенные дашборды:

1. **WMS Services Overview** - Общий обзор всех сервисов
   - Request rate
   - Error rate
   - Response time
   - JVM metrics
   - Database connections

### Создание собственных дашбордов

1. Откройте Grafana
2. **Create** → **Dashboard**
3. **Add Panel**
4. Используйте PromQL запросы:

```promql
# Request rate
rate(gateway_requests_total[5m])

# Error percentage
sum(rate(gateway_requests_total{status=~"5.."}[5m])) / sum(rate(gateway_requests_total[5m])) * 100

# 95th percentile response time
histogram_quantile(0.95, rate(gateway_requests_duration_bucket[5m]))
```

## 🛠️ Troubleshooting

### Prometheus не собирает метрики

```powershell
# Проверьте targets в Prometheus
# Откройте: http://localhost:9090/targets

# Убедитесь, что сервисы доступны
curl http://localhost:8765/actuator/prometheus
```

### Логи не появляются в Loki

```powershell
# Проверьте Promtail
docker logs wms-promtail

# Проверьте Loki
curl http://localhost:3100/ready
```

### Jaeger не показывает трассировки

Убедитесь, что в application.properties:

```properties
management.tracing.sampling.probability=1.0
management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans
```

## 🔧 Настройка retention (хранение данных)

### Prometheus
Редактируйте `monitoring/prometheus/prometheus.yml`:
```yaml
global:
  scrape_interval: 15s
  retention: 30d  # Хранить 30 дней
```

### Loki
Редактируйте `monitoring/loki/loki-config.yml`:
```yaml
limits_config:
  retention_period: 720h  # 30 дней
```

## 📦 Production Deployment

### Для production рекомендуется:

1. **Persistent Storage** для Prometheus и Loki
2. **HA (High Availability)** для критичных компонентов
3. **Secrets management** для паролей и токенов
4. **TLS/SSL** для всех соединений
5. **Authentication** для Grafana и Prometheus
6. **Backup** конфигураций и данных

### Пример для Kubernetes

```yaml
# Используйте Helm charts:
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts

helm install prometheus prometheus-community/kube-prometheus-stack
helm install loki grafana/loki-stack
```

## 🔗 Полезные ссылки

- [Prometheus Documentation](https://prometheus.io/docs/)
- [Grafana Documentation](https://grafana.com/docs/)
- [Loki Documentation](https://grafana.com/docs/loki/)
- [Jaeger Documentation](https://www.jaegertracing.io/docs/)
- [PromQL Cheat Sheet](https://promlabs.com/promql-cheat-sheet/)
- [LogQL Documentation](https://grafana.com/docs/loki/latest/logql/)

## 💡 Best Practices

1. **Метрики должны быть измеримыми** (latency, error rate, throughput)
2. **Используйте labels разумно** (не более 10-15 уникальных значений)
3. **Настройте алерты на бизнес-метрики**, а не только технические
4. **Регулярно проверяйте дашборды** и обновляйте их
5. **Документируйте свои метрики** и алерты
6. **Используйте трассировку для дебага** сложных проблем

## 📞 Поддержка

Для вопросов и проблем создавайте issue в репозитории проекта.


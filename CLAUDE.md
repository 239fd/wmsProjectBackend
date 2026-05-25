# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

This directory is a **git submodule** (`wmsProjectBackend`) of the WMS umbrella repo. It is also a self-contained Gradle multi-module build — you can work here without the parent checked out, but DB init scripts (`../sql-scripts/`), Docker Compose, and k8s manifests live in the parent.

## Module topology

`settings.gradle` includes all seven projects as plain subprojects:

```
include 'eureka-server'
include 'api-gateway'
include 'SSOService'
include 'document-service'
include 'warehouse-service'
include 'organization-service'
include 'product-service'
```

- All seven projects automatically pick up Java 21 toolchain, Checkstyle, JaCoCo, and `useJUnitPlatform()` from the root `build.gradle` (`subprojects { … }` block, lines 29–87).
- `api-gateway` is **Kotlin DSL** (`build.gradle.kts` + `settings.gradle.kts`). The other six use Groovy DSL.
- The inner `settings.gradle(.kts)` files inside `eureka-server/` and `api-gateway/` are ignored when invoked from the root build — they exist so each subproject can also be opened/built standalone (`cd eureka-server && ./gradlew bootRun`), but `gradlew` from `backend/` treats both as ordinary subprojects.
- The aggregator tasks (`allCodeQuality`, `allTestWithCoverage`, `allFixAndCheck`, `allSpotlessApply`, …) defined at the bottom of root `build.gradle` use a **hardcoded `codeQualityServices` list** of the five business services only: `['document-service', 'product-service', 'warehouse-service', 'organization-service', 'SSOService']`. `eureka-server` and `api-gateway` are deliberately excluded — their `build.gradle(.kts)` files don't apply PMD/SpotBugs/Modernizer/CPD plugins, only Spring Boot + Java 21.
- Group is `by.bsuir`. Java packages live under `by.bsuir.<service-name>` (no dashes — `warehouse-service` → `by.bsuir.warehouseservice`).
- `gradle.properties` pins `org.gradle.java.home=C:/Program Files/Java/jdk-21`. Override with `-Dorg.gradle.java.home=…` on Linux/macOS.

## Common Gradle commands

```bash
# Single-service build / run
./gradlew :SSOService:bootJar -x test
./gradlew :SSOService:bootRun                  # needs DB + Redis + Eureka up

# Tests (test is finalizedBy jacocoTestReport — coverage HTML at build/reports/jacoco/)
./gradlew :warehouse-service:test
./gradlew :SSOService:test --tests "by.bsuir.ssoservice.service.UserServiceTest"
./gradlew :SSOService:test --tests "*UserServiceTest.register_*"

# All five included services
./gradlew allTestWithCoverage   # tests + JaCoCo
./gradlew allCodeQuality        # spotlessCheck + PMD + SpotBugs + CPD + modernizer
./gradlew allFixAndCheck        # spotlessApply + pmdFix + re-run quality
./gradlew allSpotlessApply      # format only

# Single-service quality / fix
./gradlew :SSOService:codeQuality
./gradlew :SSOService:pmdFix    # defined in pmd-fixer.gradle, applied per service
```

JaCoCo minimum coverage = **0.50** (`jacocoTestCoverageVerification` in root `build.gradle`). PMD/SpotBugs/Checkstyle all set `ignoreFailures = true` — they generate reports under `*/build/reports/{pmd,spotbugs,checkstyle}/` but do not fail the build. Don't change that without explicit ask.

Where each tool is applied (this matters when you go looking for the config):

- **Spotless** — root `build.gradle` `allprojects { spotless { … } }` (lines 14–27). Applies to every project, including eureka and gateway.
- **Checkstyle + JaCoCo** — root `build.gradle` `subprojects { … }` (lines 29–87). Applies to every subproject.
- **PMD + SpotBugs + CPD + Modernizer + the per-service `codeQuality` aggregator** — declared in **each business service's own `build.gradle`** (e.g. `SSOService/build.gradle` lines 86–211). This is why `:eureka-server:codeQuality` and `:api-gateway:codeQuality` don't exist.
- **`pmd-fixer.gradle`** — sits at the backend root and is `apply from: 'pmd-fixer.gradle'` from each business service's `build.gradle`; contributes the `pmdFix` task.

Quality config files live at the backend root:
- `config/checkstyle/checkstyle.xml`
- `config/pmd/pmd-ruleset.xml`

## Service map

| Project | Port | Database (host port from compose) | Notable internals |
|---|---|---|---|
| `eureka-server` | 8761 | — | Spring Cloud Netflix Eureka discovery server |
| `api-gateway` | 8765 | — | WebFlux gateway, JWT validation, Loki/Brave tracing, Kotlin DSL build |
| `SSOService` | 8000 | `user_db` (PG :5432) + Redis :6379 | OAuth2 authorization server, JWT issuer (RS256, 4h access / 30d refresh) |
| `organization-service` | 8010 | `organization_db` (PG :5433) | Org CRUD (`OrganizationStatus.ACTIVE/ARCHIVED` сохранён для ручного архивирования через `deleteOrganization`), employees (`OrganizationEmployee` + `EmployeeManagementService`), invitation tokens (`InvitationService` → email-link, used by SSO `register/invitation`) **AND** legacy invitation codes (`OrganizationInvitationCode`, generated per warehouse by `OrganizationService.generateInvitationCodes`, validated via `PublicInvitationController`), SMTP outbound (`EmailService` — на 2026-05-21 кидает `EmailDeliveryException` с user-friendly сообщением вместо SMTP-трейсбэка; ошибка не блокирует создание `Invitation`, попадает в `emailError` поля response, чтобы UI показал «ссылку передайте вручную»), `InvitationCodeScheduler` cleans expired codes hourly. **`UserEventListener` (на 2026-05-21) переключён**: при `user.director.deleted` вызывает `deleteOrganizationOnDirectorDelete` — физический DELETE из всех org-таблиц (`organization_read_model` / `organization_event` / `organization_employee` / `organization_invitation_codes` / `invitation`), затем публикует **`organization.deleted`** (новый routing key) с payload `{orgId, name, unp, employeeUserIds, deletedBy}`. Прежний путь `archiveOrganizationOnDirectorDelete` удалён. Ручной `deleteOrganization(orgId, userId)` всё ещё архивирует (soft) — это другой кейс. |
| `warehouse-service` | 8020 | `warehouse_db` (PG :5434) | Warehouses + racks. **Three rack tables** (`Shelf`/`Cell`/`Pallet`) keyed on `rack_id`, with `RackKind` enum (SHELF/CELL/PALLET — `FRIDGE` removed 2026-05-21) on `RackReadModel`. Read-model carries a `storageConditions` column (enum `ROOM`/`COOL`/`FRIDGE`/`FREEZER` — refactored 2026-05-21 from AMBIENT/DRY) NOT NULL DEFAULT 'ROOM', плюс `max_weight_kg NUMERIC(10,2)` — **грузоподъёмность хранится на стеллаже**, не на слотах. Слоты (`shelf.shelf_capacity_kg`, `pallet.max_weight_kg`, `cell.max_weight_kg`) — nullable, legacy fallback. Pallet places (`PalletPlace` + `PalletType` enum EUR/FIN/US/ASIA с захардкоженными габаритами). `WarehouseMessageListener` consumes `user.director.deleted` / `organization.archived` / `organization.deleted` — последний (новый routing key 2026-05-21) триггерит **физический cascade DELETE** через `WarehouseService.deleteWarehousesByOrganization`. `WarehouseAnalyticsService` теперь реально подсчитывает структуру (через `ProductClient.getCellsLoad` к product-service internal endpoint) — отдаёт `racksByKind`/`racksByStorageConditions`/`totalSlots`/`occupiedSlots`/`utilizationPercent` в `/api/warehouses/analytics/{id}` и `/organization/{orgId}/summary`. Конфиг — `RestClientConfig` с `@LoadBalanced RestTemplate`, клиент product-service — `client/ProductClient`. |
| `product-service` | 8030 | `product_db` (PG :5435) | Products / batches / inventory / operations + the lion's share of business logic. Controllers: `Product`, `Batch`, `Inventory`, `InventoryCheck`, `Operation`, `Supply`, `SupplyImport` (new 2026-05-21: `import-1c`/`import-json`/`sample-json`), `Supplier`, `ShipmentRequest`, `ReceiptSession` (PAUSED workflow), `ProductCard`, `ProductAnalytics`, `DocumentRegistry` (MinIO-backed `generated_documents`), `InternalInventory` (`POST /api/internal/inventory/cells-load` для warehouse-service). **`ErpExtractor`/`ErpConnection` контроллеры снесены 2026-05-21** вместе с `PlannedDelivery` — импорт теперь только через `SupplyImport*`. Services: `Saga` family (`SagaOrchestrator` + `ReceiveSagaState`/`ShipSagaState` + `ShipmentSagaService`), `FEFOService`, `PlacementService` (фильтрация по `storageCondition` с fallback: `batch.storageConditions` → `product.requiredStorageCondition` → `ROOM`), `AbcAnalysisService` (cron 02:00), `ErpExtractorJob` (cron 03:00 — теперь делегирует в `SupplyImportService`), **`SupplyImportService` (new 2026-05-21: единая точка импорта `(orgId, List<SupplyDto>) → Supply+SupplyItem`, идемпотентно по `(orgId, externalId)`, авто-создание Supplier по `unp`/`inn` и Product по `sku`)**, `DocumentNumberService` (prefixes ПО/АП/ТТН/ТН/CMR/ИНВ/ПЕР/СПС/И/ЛП/ОТЧ), `DocumentRegistryService`, `BarcodeService`, `RevaluationService`, `WriteOffService`, `ProductJourneyService`, `AnalyticsReportService`, `OrganizationDeletionListener`. Util `MoneyToWordsRu` (`util/MoneyToWordsRu.java`, 2026-05-25) — Russian rubles/kopecks word form used as `totalAmountInWords` in акт списания, ТН/ТТН/инвойс payloads. `InventoryCheckService` (2026-05-25): `recordActualCountById(sessionId, countId, …)` для однозначной записи факта по строке + legacy `recordActualCount(productId, cellId)` теперь бросает 409 при неоднозначном `(product, cell)` (несколько партий одного товара в ячейке). `StorageConditions` enum (`ROOM`/`COOL`/`FRIDGE`/`FREEZER`). `PackagingType` enum (`PALLET`/`BOX`/`CRATE`/`EACH`) — на `SupplyItem` и `ProductBatch`. `Product.requiredStorageCondition` задаёт дефолт условий хранения. `Supply` многострочный: `externalId` (UNIQUE per org), `source` (MANUAL/JSON/1C-Python), `quantityOnly` boolean, `snapshot JSONB` для опциональных блоков (transport/commission/international/…), `SupplyItem.productId` nullable + snapshot товара (sku/name/barcode/category/manufacturer) + плановая партия (batchNumber/manufactureDate/expiryDate/purchasePrice) + финансы (unitPrice/vatRate/vatAmount/totalAmount) + packagingType + markedForWriteoff. `validation/BusinessValidator` enforces placement/business rules. |
| `document-service` | 8040 | — (stateless) | `rpa/PdfDocumentService` (PDFBox + DejaVuSans for Cyrillic) and Python `PythonRpaClient` (see RPA channels). `DataEnrichmentService` pulls org/warehouse names by calling org-service `/api/internal/organizations/{id}` and warehouse-service to enrich document headers. **12 generator endpoints** on `DocumentController` — 11 "business" types listed in PLAN §6.8 plus `/analytics-report` (PDF summary used by DIRECTOR analytics). `MockErpController` (`@RequestMapping("/mock-erp")`) feeds product-service ERP-extractor in dev. Default format = PDF; XLS/DOCX available via `?format=` but program channel falls back to PDF in all cases (D-3 workaround). |

## Per-service package layout

Internal packages are **uniform** across the five included services — mirror the existing layout when adding code:

```
by.bsuir.<service>/
├── controller/    # REST endpoints (@RestController)
├── service/       # business logic
├── repository/    # Spring Data JPA repositories (split: <Aggregate>EventRepository + <Aggregate>ReadModelRepository)
├── model/
│   ├── entity/    # JPA entities — typically <Aggregate>Event + <Aggregate>ReadModel pairs
│   ├── enums/
│   └── event/     # domain event payload classes (published to RabbitMQ)
├── dto/
│   ├── request/   # records — incoming payloads
│   └── response/  # records — outgoing payloads
├── config/        # SecurityConfig, RedisConfig, RabbitMQConfig, OpenApiConfig,
│                  #   CORSConfig, GlobalExceptionHandler, JwtAuthenticationFilter, …
└── exception/     # AppException + custom subclasses
```

Two services break the pattern intentionally:
- `document-service` has **no** `model/` or `repository/` (stateless) and adds `rpa/`.
- `product-service` adds `saga/` and `validation/` (`BusinessValidator`).

### DTO conventions

DTOs are Java `record`s (not classes). Validation is via Jakarta Bean Validation (`@NotBlank`, `@Email`, `@Size`, `@NotNull`) with **Russian** error messages on the constraints. Records can host helper methods — e.g. `RegisterRequest#getFullName()` composes "Lastname Firstname Middlename". When you add a request DTO, put it in `dto/request/`; response in `dto/response/`. Don't introduce DTO classes.

### Exception handling

Each service has its **own** `exception/AppException` (NOT shared) with static factories `badRequest`, `unauthorized`, `forbidden`, `notFound`, `conflict`, `internalError` — use them in service code instead of throwing raw `RuntimeException`s or `ResponseStatusException`. Each service also has its own `config/GlobalExceptionHandler` (`@RestControllerAdvice`) that maps `AppException` → `ErrorResponse` JSON with the right HTTP status. When adding new error semantics, prefer extending the factory list over creating bespoke exception classes.

**Validation error format (2026-05-25)**: `MethodArgumentNotValidException` in all four service handlers (`product-service`, `SSOService`, `organization-service`, `warehouse-service`) now joins only the Russian message **values** with `; ` — the English field-key prefix (`reason: ...`) and the `errors.toString()` (`{reason=...}`) leak are removed. Field map still returned as `errors` for client-side mapping. DTO `@NotNull/@NotBlank/@Size` messages and runtime `AppException.badRequest(...)` strings are all Russian (no `Product ID / Warehouse ID / User ID / Refresh token / Cell ID` English tokens left).

### Messaging (RabbitMQ)

Topology is declared per-service in `<service>/config/RabbitMQConfig.java` — that file is the registry of every exchange, queue, routing key, and binding the service touches. Conventions:

- One `TopicExchange` per service, named `<service>.exchange` (e.g. `warehouse.exchange`, `organization.exchange`).
- Routing keys mirror domain events: `warehouse.created`, `warehouse.updated`, `warehouse.deleted`, `organization.deleted`, etc.
- Queues are named `<aggregate>.<event>.queue` (e.g. `warehouse.created.queue`); cross-service consumer queues append the consumer service: `organization.deleted.warehouse.queue`.
- Producers use `RabbitTemplate` (with `Jackson2JsonMessageConverter`); consumers are `@RabbitListener`-annotated services like `WarehouseMessageListener` (warehouse-service).
- All queues are durable (`new Queue(NAME, true)`).

Adding a cross-service event = (1) add the routing key + queue + binding to the producer's `RabbitMQConfig`, (2) declare the same queue + binding in the consumer's `RabbitMQConfig`, (3) write a `@RabbitListener` method in the consumer.

### Observability

Every service has `src/main/resources/logback-spring.xml` with three appenders:
- **Console** — JSON via Logstash encoder, with `service` custom field.
- **RollingFile** — `./logs/<service>.log`, 10 MB rotate, 30-day retention.
- **LOKI** (`com.github.loki4j.logback.Loki4jAppender`) — pushes to `http://localhost:3100/loki/api/v1/push` with labels `service`, `host`, `level` and a JSON payload that includes `trace_id` / `span_id` from MDC.

Tracing is Brave + Zipkin (`management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans`, sampling probability 1.0). Actuator exposes `/actuator/{health,info,prometheus,metrics,loggers}` on the service's main port. Prometheus scrapes `/actuator/prometheus`.

Don't strip the LOKI/Zipkin config when copy-pasting into a new service — the monitoring stack expects every service to publish.

### Database

Every service uses `spring.jpa.hibernate.ddl-auto=validate` — JPA validates entities against the schema but does **not** create tables. DDL is owned by `../sql-scripts/<service>DB.sql` (and `SSOService/scripts/user-db.sql` for SSO's standalone compose). When you add an entity field, you must also add the column to the SQL file in the parent repo, otherwise startup fails validation.

### API gateway routing

`api-gateway/src/main/java/by/bsuir/apigateway/config/GatewayConfig.java` is the routing table — the only place that knows how `/api/...` paths map to services:

| Prefix | → service |
|---|---|
| `/api/auth/**`, `/api/profile/**`, `/api/oauth/**` | `lb://SSOSERVICE` |
| `/api/organizations/**`, `/api/invitations/**` | `lb://ORGANIZATION-SERVICE` |
| `/api/warehouses/**`, `/api/racks/**` | `lb://WAREHOUSE-SERVICE` |
| `/api/products/**`, `/api/batches/**`, `/api/operations/**`, `/api/inventory/**`, `/api/inventory-check/**`, `/api/analytics/**`, `/api/supplies/**`, `/api/suppliers/**`, `/api/erp-extractor/**` ⚠, `/api/erp-connections/**` ⚠, `/api/product-card/**`, `/api/document-registry/**`, `/api/receipt-sessions/**` | `lb://PRODUCT-SERVICE` |

⚠ `/api/erp-extractor/**` and `/api/erp-connections/**` are **dead routes** — the controllers (`ErpExtractorController`, `ErpConnectionController`) were removed 2026-05-21 in favour of `SupplyImportController` (`/api/supplies/import-1c`, `/api/supplies/import-json`). The gateway mapping is still here; hitting it returns 404 from product-service. Safe to delete from `GatewayConfig.java:26-27`.
| `/api/documents/**` | `lb://DOCUMENT-SERVICE` |

Service IDs (`SSOSERVICE`, `WAREHOUSE-SERVICE`, …) are **uppercased** Eureka registrations — use `lb://<UPPERCASE>` in any new route. There are also `/<service-name>/**` routes (`stripPrefix(1)`) for direct service access during debugging.

When you add a new endpoint prefix, edit `GatewayConfig.java` AND mirror it into `client/src/config/api.js` (`API_ENDPOINTS`) on the frontend side.

`/api/operations/ship-requests/**` is covered by the existing `/api/operations/**` rule. When adding a controller in the product or org services, check whether its base path needs a new gateway entry.

## CQRS / event-sourcing convention (important)

Several services split write and read sides — e.g. SSO has `UserEvent` + `UserReadModel`, with `UserEventRepository` and `UserReadModelRepository`; warehouse has `WarehouseEvent` / `WarehouseReadModel`, `RackEvent` / `RackReadModel`. The pattern in commands:

1. Save a domain event row (write side).
2. Update the read-model row in the same transaction.
3. Publish to RabbitMQ via `RabbitTemplate` for cross-service consumers.

Queries hit only the read model. SQL DDL for these pairs lives in `../sql-scripts/*.sql` and (for SSO) `SSOService/scripts/user-db.sql`. When adding a new aggregate, mirror the event/read-model split rather than introducing a single mutable JPA entity.

## Saga (product-service)

`saga/SagaOrchestrator` is now **persistent**: every state change is mirrored into `saga_state` (`saga_id`, `saga_type` = `RECEIVE|SHIP`, `status`, `current_step`, `payload` JSON, `failure_reason`, timestamps). On `ContextRefreshedEvent` it reloads sagas in `PENDING`/`COMPENSATING` state from the table back into `activeSagas` / `activeShipSagas` `ConcurrentHashMap`s, so a service restart resumes in-flight work. Two saga shapes exist:

- **Receive** (`ReceiveSagaState`): `BATCH_CREATION` → `INVENTORY_UPDATE` → `OPERATION_RECORD` → `COMPLETED`.
- **Ship** (`ShipSagaState`): `STOCK_RESERVATION` → `STAGING` → `DOCUMENT_GENERATION` → `INVENTORY_UPDATE` → `OPERATION_RECORD` → `COMPLETED`.

Failures call `markStepFailed` / `markShipStepFailed`, which set status `FAILED` and call `compensate(...)`. Compensation **выполняет реальный rollback** (исправлено в D-PR-5 ещё 2026-05-01):
- `compensate(sagaId)` для receive: удаляет `ProductOperation`, откатывает `Inventory.quantity` (или удаляет если стало ≤0), удаляет batch.
- `compensateShipSaga(sagaId)` для ship: удаляет operation + staging operation, восстанавливает `Inventory.quantity`, освобождает резерв через `reservedQuantity -= qty`. `documentId` не удаляется (document-service stateless).

Saga status переходит в `COMPENSATED`. Если сама компенсация упадёт — `COMPENSATION_FAILED` (требует ручного вмешательства).

Don't bypass the saga with synchronous cross-service calls when extending receive/ship logic.

## RPA channels

Two parallel channels generate documents and feed planned deliveries; the old WinAppDriver/Appium/JACOB Java stack was removed 2026-05-19 in favour of a Python micro-service.

### Channel 1 — Python `rpa-service` (host Windows, COM-based)

`backend/rpa-service/` is a **FastAPI** app on `pywinauto + win32com` running directly on a Windows host that has MS Office and 1С УТ 11.2 installed. It is NOT packaged into Docker or k8s (COM automation requires Office + Windows). Start it with `backend/rpa-service/run.ps1` (binds to `:8060`). See `backend/rpa-service/README.md` for the full template list.

Endpoints:
- `GET  /health` — status + supported document types.
- `POST /fill/{doc-type}` — generates a native `.xlsx` / `.docx` from a `Map<String,Object>` WMS payload using the templates in `rpa-service/templates/` (9 production-grade Office templates: receipt-order, inventory-report, revaluation-act, write-off-act, invoice, discrepancy-act, transport-note ×2 layouts, waybill ×2 layouts, cmr ×3 languages). No PDF conversion server-side — Java falls back to Apache POI / PDFBox when `format=pdf&mode=rpa`.
- `POST /parse/supplies` — drives 1С УТ "Заказы поставщикам" via pywinauto, returns hierarchical `supplies.json`.
- `POST /parse/sales` — same for "Заказы клиентов" (excluding closed).

Java integration:
- `document-service/rpa/PythonRpaClient` — `RestClient`-based component used when the `X-Generation-Mode: rpa` header is set on `POST /api/documents/<type>`. On any failure it raises `IllegalStateException` and `DocumentService` falls back to the Apache POI channel (response header `X-Generation-Channel: rpa-fallback-error`).
- `product-service/rpa/PythonRpaExtractor` — `@Component("oneCExtractor")` implementing `SupplyExtractor` (NEW interface 2026-05-21, replaces `PlannedDeliveryExtractor`). Возвращает `List<SupplyDto>` (полную многострочную структуру) — без flatten. Дополнительные блоки JSON (`transport`/`commission`/`international`/`receipt_session`/…) уходят в `Supply.snapshot` как JSONB. Триггерится из `ErpExtractorJob.runManually(orgId, warehouseId, userId)` (вручную через `POST /api/supplies/import-1c`) или по cron-расписанию (без orgId — для теста соединения, без записи в БД).
- No auth between Java and Python — internal trust, Python is not exposed outside the host.
- No Eureka registration — external URL via `rpa.python.base-url` (default `http://localhost:8060`). Both services beanify their own `RpaProperties.Python` (`enabled`, `baseUrl`, `timeoutSeconds`).

Config keys (in each service's `application.properties`):
```
rpa.python.enabled=true
rpa.python.base-url=http://localhost:8060
rpa.python.timeout-seconds=120   # document-service (per request)
rpa.python.timeout-seconds=300   # product-service (parsing 1С is slower)
```

### Channel 2 — Apache POI / PDFBox (Java, in-process, default — PDF only since D-3)

`document-service` is now a thin generator: `PdfDocumentService` renders the document straight to PDF via PDFBox + DejaVuSans (Cyrillic-capable). The XLS/DOCX template-fill via Apache POI / HWPF was disabled by D-3 workaround (2026-05-19) because POI's HWPF replace produced corrupt files Word/Excel could not open. `DocumentController.generate(type, data, ..., format, mode)` always returns PDF bytes when `mode != rpa`; `?format=xls`/`?format=docx` query params are still parsed but the body is PDF. POI templates in `documents template/` (Cyrillic filenames — keep them, the Python rpa-service references them) are only used by the Python channel today; the long-term fix is to migrate `.doc`/`.rtf` templates to `.docx` and rewrite via XWPFDocument (PLAN.md §5.2 D-3).

The **12 generator endpoints** on `DocumentController` (all `POST /api/documents/<type>`, returning raw bytes + `X-Generation-Channel` header):

`receipt-order` · `inventory-report` · `revaluation-act` · `write-off-act` · `waybill` (with `?layout=horizontal|vertical` synonymous via Python) · `picking-list` · `placement-list` (after-receipt placement recommendations) · `receipt-act` · `invoice` · `transport-note` (`?layout=horizontal|vertical`) · `cmr` · `analytics-report` (PDF summary by warehouse, used by DIRECTOR Analytics; PDF-only — no POI or Python template).

**Dropped from earlier scope (2026-05-13 cleanup)**: `release-order` / `shipment-order` (alias pair) / `invoice-fact` / `discrepancy-act`. Discrepancy data is now an embedded section inside `receipt-act` when `data.hasDiscrepancies()` (see PLAN.md §0.1 Q1).

**`isPdfOnly(type, data)` (2026-05-25, data-aware)** в `DocumentService` форсирует PDF для:
- `picking-list`, `placement-list`, `analytics-report` — нет Office-шаблона.
- `revaluation-act` — Office-шаблон `акт переоценки.xls` это форма переоценки **основных средств** (ОС: первоначальная/восстановительная/остаточная стоимость), для товара структурно не подходит.
- `receipt-act` **без расхождений** → АктПриёмки (RTF) неполный, форсится PDF; **с расхождениями** (`data.discrepancies` непустой список) → RPA разрешён, `api.py._resolve_spec` выбирает Excel «АктРасхождения», филлер заполняется реальными `qty_expected/qty_actual`.

Если `mode=rpa` пришёл для PDF-only типа — `generate()` тихо переключает на `auto` (PDF). На фронте `<GenerationModeCheckbox docType="…">` для PDF-only возвращает `null` — чекбокс не рендерится.

Bodies are `Map<String,Object>` (no strict DTO — payload shape is implicit). `DataEnrichmentService` enriches the payload with org/warehouse name/address pulled via internal endpoints. Metadata + retrieval lives in **product-service** `DocumentRegistryService` (`/api/document-registry`), not here — `document-service` is stateless. `GET /api/documents/rpa/health` proxies `PythonRpaClient.isAvailable()` (used by Settings UI to show channel status). `GET /api/documents/stub-info` returns a dev-info bag. `MockErpController` (`@RequestMapping("/mock-erp")`) lives in this service to feed product-service's RPA extractor with HTML/JSON delivery data — useful for local testing and not a real ERP shim.

### Channel selection (response header)

`document-service` writes the actual channel used into `X-Generation-Channel`:
- `auto` / no header → Apache POI (PDF default).
- `rpa` → Python (`X-Generation-Channel: rpa`).
- Python failure → Apache POI fallback (`X-Generation-Channel: rpa-fallback-error`).

### RPA data flow (2026-05-25)

Python адаптер `rpa/api.py:wms_payload_to_order` маппит WMS-payload в `PurchaseOrder` (`rpa/models.py`). `OrderItem` теперь несёт реальные поля: `vat_rate`/`vat_amount`/`weight`/`volume`/`hs_code`/`package_count`/`old_price`/`new_price`/`qty_expected`/`qty_actual` (всё optional, при отсутствии — fallback на оценку qty×5кг / 20% inclusive / qty=expected). `excel_filler._line_amounts(item)` использует реальный `vat_rate` (price = net, VAT добавляется сверху) когда поле пришло — иначе legacy-split. Итоги ТН/ТТН/CMR/инвойса (`total_weight`/`total_vat`/`total_with_vat`/`total_volume`) считаются по реальным per-item значениям; «Акт расхождения» в Excel получает реальные `qty_expected/qty_actual` + `diff_count/diff_amount` из настоящих расхождений; «Опись» получает раздельные `qty_fact`/`qty_accounted` и итоги факт/учёт. `word_filler._item_cell_text` берёт реальные weight/volume/hs_code из payload; «Общий вес» в инвойсе считается из суммы. Counterparty `organization` (= получатель в шаблонах) приоритизирует `consigneeName/buyerName` над `organizationName` — на отгрузке грузополучатель = клиент (а не наша орг), на приёмке (consignee отсутствует) — fallback на нашу орг. `RoleToPosition` (document-service) даёт WORKER → «Кладовщик», DIRECTOR → «Заведующий складом» для подписей.

## Persistent flows & cron jobs

| Service | Class | Cron | What it does |
|---|---|---|---|
| organization-service | `InvitationCodeScheduler` | hourly | Marks `organization_invitation_codes` past `expires_at` inactive |
| product-service | `AbcAnalysisService` | `0 0 2 * * *` | Recomputes `abc_class` on `product_read_model` from last-90-day operations |
| product-service | `ErpExtractorJob` | `0 0 3 * * *` | Plain trigger for the Python RPA extractor (`SupplyExtractor` interface, only `PythonRpaExtractor` implementation остался — Jsoup/REST extractors снесены 2026-05-21). Создаёт `Supply`+`SupplyItem` через `SupplyImportService`. Запись в `extraction_log`. Cron-запуск не имеет `organizationId` → только check connectivity, без записи в БД; запись через `POST /api/supplies/import-1c` (с headers org/warehouse/user). |

## Schema (no Flyway — sql-scripts is single source of truth)

`spring.jpa.hibernate.ddl-auto=validate` for every DB-backed service. Schema lives in `../sql-scripts/<service>DB.sql` and is applied once on first Postgres boot via `docker-compose.yml` volume mount.

**Flyway was removed 2026-05-17** — it was never actually wired (no `org.flywaydb` in any `build.gradle`), and `db/migration/V*.sql` files were sitting unused. When changing schema:
1. Edit the relevant `sql-scripts/<service>DB.sql`.
2. Add the matching JPA entity field / column / index.
3. Recreate the DB volume (`cleanup-docker.ps1` + `deploy-docker.ps1`).
4. **Do not** create new `V*.sql` files under `db/migration/` — the directory no longer exists.

`eureka-server`, `api-gateway`, `document-service` have no schema (gateway/eureka stateless, document-service stateless).

## Auth (SSOService anchor)

- **Access token**: JWT, RS256, **4-hour TTL** (`app.security.jwt.access-ttl-seconds=14400`). Private key on SSO; public key served by `JwtPublicKeyController` (and via `JwkUtils`). Other services (and `api-gateway`) validate against that JWK. Claims: `sub` (userId), `email`, `role`, plus `organizationId` / `warehouseId` when set on the user.
- **Refresh token**: opaque UUID, stored in Redis keyed by userId, **30-day TTL** (`app.security.jwt.refresh-ttl-seconds=2592000`); a hash is also written to `login_audit` so `getActiveSessions()` can flag the current session.
- **Roles**: `WORKER`, `ACCOUNTANT`, `DIRECTOR` (see `model/enums/UserRole`); RBAC enforced in `SecurityConfig`/`SecurityFilterChain`. The user has explicitly fixed this set — don't introduce a fourth role. **Display labels (2026-05-25)**: WORKER → «Кладовщик», ACCOUNTANT → «Бухгалтер», DIRECTOR → «Заведующий» (UI, emails via `EmailService.translateRole`, document positions via `document-service/util/RoleToPosition`). Enum values, routing key `user.director.deleted`, URL `/register/director` and code identifiers (`directorName`, `getDirector`) are intentionally unchanged — labels are display-only.
- **Registration paths** (only two — `AuthController` exposes nothing else):
  1. `POST /api/auth/register/director` — DIRECTOR account *without* organization/warehouse fields. After register, frontend redirects to `/main/organization?firstTime=true`; the org is created by a separate `POST /api/organizations` request to org-service, which then PATCHes `UserReadModel.organizationId` via SSO `/api/internal/users/{userId}/organization` and writes an `OrganizationEmployee` row.
  2. `POST /api/auth/register/invitation` — invitee flow. SSO calls org-service `GET /api/invitations/validate?token=…`, creates the user with email/role/orgId/warehouseId baked in from the invitation, calls org-service `POST /api/internal/organizations/{orgId}/employees` to add the employee, then marks the invitation used. The whole sequence is `@Transactional` — if `addEmployee` fails, SSO rolls back and the invitation stays valid.
  There is **no** generic `POST /api/auth/register` — OAuth completion goes through `POST /api/oauth/complete-registration` (`OAuthController`), which dispatches into the same `OAuthService` paths.
- **JWT validation in non-SSO services**: each service has its own `config/JwtAuthenticationFilter` (a `OncePerRequestFilter`) that pulls `Authorization: Bearer …`, validates against the SSO public key, and populates `SecurityContextHolder` with `UsernamePasswordAuthenticationToken` + role authority. The same pattern lives in `api-gateway/filter/JwtAuthenticationFilter.java` (WebFlux variant). Don't write a new filter — extend the existing one. The gateway forwards `X-User-Id`, `X-User-Role`, `X-Organization-Id`, `X-Warehouse-Id` to downstream services; controllers read the latter three for tenant filtering.
- **`/api/internal/**`** is whitelisted by every service's JWT filter (no auth) so cross-service calls work without forging tokens. Don't ever add an `/api/internal/**` route to the gateway.
- **OAuth providers**: Yandex + Google. Two-stage flow — OAuth callback creates an `OAuthPendingRegistration` row; the user then picks a role via `/api/oauth/complete-registration`.
- **Audit**: every login attempt (success + failure) writes a `LoginAudit` row including userId, loginTime, IP, User-Agent, result. IP is encrypted via `EncryptedStringConverter` (key from `${APP_DB_ENCRYPTION_KEY}`).
- **Cascades on user/org/warehouse lifecycle** (RabbitMQ-driven, **refactored 2026-05-21**):
  - SSO `DELETE /api/profile` of a DIRECTOR → `ProfileService.deleteAccount` физически удаляет `UserReadModel` + `UserEvent` + `LoginAudit` + все Redis refresh-токены (email освобождается, можно зарегистрироваться повторно) → публикует `user.director.deleted` → org-service `UserEventListener.handleDirectorDeleted` вызывает `deleteOrganizationOnDirectorDelete`: физический wipe org + employees-row + invitation-codes + публикация **`organization.deleted`** с `employeeUserIds`. Это новое событие слушают: warehouse-service (cascade DELETE через `WarehouseService.deleteWarehousesByOrganization`), product-service (`OrganizationDeletionListener` — JdbcTemplate DELETE по списку org-scoped таблиц + MinIO cleanup), SSO (`EmployeeEventListener.handleOrganizationDeleted` — физически удаляет `UserReadModel` бывших сотрудников + их LoginAudit/UserEvent/Redis-токены).
  - Старый путь `organization.archived` оставлен для ручного `deleteOrganization` (soft-archive) и backward-compat consumer'ов.
  - org-service `PATCH .../employees/{userId}/status` (block/unblock) → публикует `employee.status.changed` → SSO toggles `UserReadModel.isActive`.
  - SSO `ProfileService.terminateSession` (новое 2026-05-21): **DELETE login_audit** (не UPDATE is_active=false) + `RefreshTokenService.deleteUserTokenByHash` — pattern-scan по `refresh_token:*` с BCrypt-матчингом против `LoginAudit.refreshTokenHash`, удаление найденного. Каждая итерация защищена try/catch чтобы битый ключ не валил весь запрос.
- **Secrets**: on HEAD, `SSOService/src/main/resources/application.properties` reads OAuth client IDs/secrets through `${YANDEX_OAUTH_CLIENT_ID:}` / `${GOOGLE_OAUTH_CLIENT_SECRET:}` etc. with empty defaults — no secret is committed in the current code. A leak landed in git history after a bypass push-protection on 2026-05-22; rotation through Google/Yandex consoles is still pending. SMTP password is parameterized the same way (`spring.mail.password=${MAIL_PASSWORD:}`), but `req.md` at the umbrella root still contains an active Gmail app-password — that file is the bigger active leak.

## Testing conventions

- JUnit 5 + Mockito + AssertJ + MockMvc; Testcontainers (`org.testcontainers:postgresql`) for full integration; H2 for fast repository tests. Same dependency set in every service's `build.gradle`.
- Test packages mirror main:
  ```
  src/test/java/by/bsuir/<service>/
  ├── <Service>ApplicationTests.java   # context-load smoke test
  ├── controller/                       # @WebMvcTest, MockMvc, @MockBean
  ├── service/                          # @ExtendWith(MockitoExtension.class), @Mock + @InjectMocks
  ├── integration/                      # @SpringBootTest with Testcontainers; usually extend BaseIntegrationTest
  └── utils/                            # plain unit tests for utilities
  ```
- Naming: `methodName_GivenX_WhenY_ThenZ`. `@DisplayName` strings are written in **Russian** — match the existing style, don't translate.
- Controller tests: `@WebMvcTest(controllers = X.class)` + `@AutoConfigureMockMvc(addFilters = false)` + `@MockBean` for the service layer. Use `mockMvc.perform(...)` and `jsonPath()` assertions.
- Service tests: pattern is **AAA** (Arrange / Act / Assert) with comments delimiting each block — preserve this when extending tests.
- For Rabbit consumers there's a precedent (`WarehouseMessageListenerTest`) — mock `RabbitTemplate` and verify `convertAndSend(...)` calls; don't spin up a real broker in unit tests.

## Local run notes

A service started locally needs its dependencies up first. Easiest path is to start infra from the parent dir:

```bash
# From repo root (one level up)
docker-compose up -d postgres-sso postgres-org postgres-warehouse postgres-product redis rabbitmq
```

Then start `eureka-server` (composite — `cd eureka-server && ./gradlew bootRun`), then `api-gateway`, then any service. `application.properties` defaults all point at `localhost` ports matching the compose file.

## Code style (enforced by Spotless + Checkstyle)

- google-java-format AOSP variant, 4-space indent, trailing newline, no unused imports.
- Max line length 200 (Checkstyle).
- Lombok is present everywhere (`@Slf4j`, `@RequiredArgsConstructor`, etc.) — keep using it; don't manually inline getters/setters.
- Java 21 features are fair game (records for DTOs are the dominant style — see `dto/` packages).
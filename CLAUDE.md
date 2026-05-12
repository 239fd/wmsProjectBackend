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
| `organization-service` | 8010 | `organization_db` (PG :5433) | Org CRUD, employees, invitation tokens (`Invitation`) and legacy invitation codes (`OrganizationInvitationCode`), SMTP outbound (`mail.gmail.com`), `InvitationCodeScheduler` cleans expired codes hourly |
| `warehouse-service` | 8020 | `warehouse_db` (PG :5434) | Warehouses + racks (SHELF/CELL/FRIDGE/PALLET) + pallet places, request/response RPC queues for org-service |
| `product-service` | 8030 | `product_db` (PG :5435) | Products / batches / inventory / operations; `saga/SagaOrchestrator` (persistent — see below); `validation/BusinessValidator`; supplies + suppliers; ship-requests (`/api/operations/ship-requests`); `AbcAnalysisService` (cron 02:00); `ErpExtractorJob` (cron 03:00) |
| `document-service` | 8040 | — (stateless) | `rpa/DocumentRpaService` — Apache POI templates in `documents template/`; `MockErpController` to feed product-service's RPA extractor; default response format is **PDF** (XLS/DOCX still available via `?format=`) |

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
| `/api/organizations/**` | `lb://ORGANIZATION-SERVICE` |
| `/api/warehouses/**`, `/api/racks/**` | `lb://WAREHOUSE-SERVICE` |
| `/api/organizations/**`, `/api/invitations/**` | `lb://ORGANIZATION-SERVICE` |
| `/api/products/**`, `/api/batches/**`, `/api/operations/**`, `/api/inventory/**`, `/api/inventory-check/**`, `/api/analytics/**`, `/api/supplies/**`, `/api/suppliers/**`, `/api/erp-extractor/**`, `/api/product-card/**` | `lb://PRODUCT-SERVICE` |
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

## RPA (document-service)

`DocumentRpaService` reads `.xls` / `.docx` templates from `document-service/documents template/` (Cyrillic filenames — keep them, the service references them by exact name) and fills them via Apache POI. `PdfDocumentService` then converts to PDF (the default output, `defaultValue = "pdf"` on the `format` query param) — XLS/DOCX is still available via `?format=xls`/`?format=docx` on each generator. The 14 generators on `DocumentController` (all POST `/api/documents/<type>`):

`receipt-order` (приходный ордер) · `release-order` / `shipment-order` (отпускной / расходный ордер — alias) · `inventory-report` (инвентаризационная опись) · `revaluation-act` (акт переоценки) · `write-off-act` (акт списания) · `waybill` (ТТН) · `picking-list` (лист подбора) · `receipt-act` (акт приёмки) · `invoice-fact` (счёт-фактура) · `invoice` (инвойс) · `transport-note` (ТН / товарная накладная) · `cmr` (CMR) · `discrepancy-act` (акт о расхождении).

All endpoints accept a `Map<String,Object>` body (no strict DTO yet — payload shape is implicit) and return `{documentId, type, status}`. Read endpoints: `GET /api/documents/{id}` regenerates the PDF on demand from stored metadata (with `X-Organization-Id` ownership enforcement); `GET /api/documents/{id}/metadata`; `GET /api/documents` paginated and org-filtered; `GET /api/documents/stub-info` for meta. `MockErpController` (`@RequestMapping("/mock-erp")`) lives in this service to feed product-service's RPA extractor with HTML/JSON delivery data — useful for local testing and not a real ERP shim.

## Persistent flows & cron jobs

| Service | Class | Cron | What it does |
|---|---|---|---|
| organization-service | `InvitationCodeScheduler` | hourly | Marks `organization_invitation_codes` past `expires_at` inactive |
| product-service | `AbcAnalysisService` | `0 0 2 * * *` | Recomputes `abc_class` on `product_read_model` from last-90-day operations |
| product-service | `ErpExtractorJob` | `0 0 3 * * *` | Polls a configured ERP (Jsoup HTML + REST `ApiExtractorImpl`, switched by `erp.extraction.mode=rpa|api`); writes new rows into `planned_deliveries` keyed by `external_id` UNIQUE; logs to `extraction_log` |

## Flyway migrations

`spring.jpa.hibernate.ddl-auto=validate` for every service (except product-service, which currently has `update` — see PLAN.md / D-PR-11; do NOT switch to `validate` casually because it'll regress the saga-state columns). Migrations live in `src/main/resources/db/migration/`:

- `SSOService` — `V1__init.sql`, `V2__encrypt_sensitive_fields.sql` (AES converter on IP / sensitive columns)
- `organization-service` — `V1__init.sql`, `V2__widen_encrypted_columns.sql` (UNP/address column widths after AES)
- `warehouse-service` — `V1__init.sql`
- `product-service` — `V1__init.sql`, `V2__inventory_and_operation_events.sql` (adds `inventory_events` / `product_operation_events`)
- `eureka-server`, `api-gateway`, `document-service` — no migrations (eureka and gateway are stateless; document-service is stateless)

`baseline-on-migrate=true` is set, so a Flyway run on a database already populated by `sql-scripts/*.sql` will not error out.

## Auth (SSOService anchor)

- **Access token**: JWT, RS256, **4-hour TTL** (`app.security.jwt.access-ttl-seconds=14400`). Private key on SSO; public key served by `JwtPublicKeyController` (and via `JwkUtils`). Other services (and `api-gateway`) validate against that JWK. Claims: `sub` (userId), `email`, `role`, plus `organizationId` / `warehouseId` when set on the user.
- **Refresh token**: opaque UUID, stored in Redis keyed by userId, **30-day TTL** (`app.security.jwt.refresh-ttl-seconds=2592000`); a hash is also written to `login_audit` so `getActiveSessions()` can flag the current session.
- **Roles**: `WORKER`, `ACCOUNTANT`, `DIRECTOR` (see `model/enums/UserRole`); RBAC enforced in `SecurityConfig`/`SecurityFilterChain`. The user has explicitly fixed this set — don't introduce a fourth role.
- **Registration paths** (three of them — match against requirements):
  1. `POST /api/auth/register` — generic flow (kept around for OAuth completion fallback).
  2. `POST /api/auth/register/director` — DIRECTOR account *without* organization/warehouse fields. The org is created in a separate request to org-service afterwards; org-service then PATCHes `UserReadModel.organizationId` via `PATCH /api/internal/users/{userId}/organization` and writes an `OrganizationEmployee` row.
  3. `POST /api/auth/register/invitation` — invitee flow. SSO calls org-service `GET /api/invitations/validate?token=…`, creates the user with email/role/orgId/warehouseId baked in from the invitation, calls org-service `POST /api/internal/organizations/{orgId}/employees` to add the employee, then marks the invitation used. The whole sequence is `@Transactional` — if `addEmployee` fails, SSO rolls back and the invitation stays valid.
- **JWT validation in non-SSO services**: each service has its own `config/JwtAuthenticationFilter` (a `OncePerRequestFilter`) that pulls `Authorization: Bearer …`, validates against the SSO public key, and populates `SecurityContextHolder` with `UsernamePasswordAuthenticationToken` + role authority. The same pattern lives in `api-gateway/filter/JwtAuthenticationFilter.java` (WebFlux variant). Don't write a new filter — extend the existing one. The gateway forwards `X-User-Id`, `X-User-Role`, `X-Organization-Id`, `X-Warehouse-Id` to downstream services; controllers read the latter three for tenant filtering.
- **`/api/internal/**`** is whitelisted by every service's JWT filter (no auth) so cross-service calls work without forging tokens. Don't ever add an `/api/internal/**` route to the gateway.
- **OAuth providers**: Yandex + Google. Two-stage flow — OAuth callback creates an `OAuthPendingRegistration` row; the user then picks a role via `/api/oauth/complete-registration`.
- **Audit**: every login attempt (success + failure) writes a `LoginAudit` row including userId, loginTime, IP, User-Agent, result. IP is encrypted via `EncryptedStringConverter` (key from `${APP_DB_ENCRYPTION_KEY}`).
- **Cascades on user/org/warehouse lifecycle** (RabbitMQ-driven):
  - SSO `DELETE /api/profile` of a DIRECTOR → publishes `user.director.deleted` → org-service archives the org and warehouse-service deletes its warehouses; warehouse deletion in turn fires `warehouse.deleted` so SSO clears `warehouse_id` on each affected user.
  - org-service archive of an organization → publishes `organization.archived` → SSO sets `is_active=false` on non-DIRECTOR employees and clears their `organization_id`.
  - org-service `PATCH .../employees/{userId}/status` (block/unblock) → publishes `employee.status.changed` → SSO toggles `UserReadModel.isActive`.
- **Secrets**: OAuth client IDs/secrets are currently checked into `SSOService/src/main/resources/application.properties`. Treat that file as sensitive — don't rotate or regenerate without coordinating with the user.

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
# Модуль catalog

> Справочники чек-ина: эмоции, факторы, события, отслеживаемые метрики. Владелец схемы БД `catalog`.
> Правила границ модулей — [architecture-overview.md](../architecture-overview.md), REST-контракт — [api-design.md](../api-design.md), сущности и пресеты — [domain-model.md](../domain-model.md).

## 1. Ответственность и границы

**Делает:**

- CRUD персональных элементов справочников: эмоции (с цветом), факторы, события, метрики (со шкалой);
- список видимых элементов: системные пресеты + персональные, сортировка `sort_order`, фильтр `includeInactive`;
- валидации справочника: имя 1–100 символов, уникально в рамках пользователя; шкала метрики `minValue < maxValue`.

**Не делает:**

- не владеет HTTP: контроллер живёт в `apps/rest-api` и является тонким адаптером над портами модуля;
- не хранит связи «запись ↔ элемент» (это `entry`, ссылки по id без FK);
- не рассылает и не анализирует — потребители справочников (`entry`, `analytics`, бот) читают через порты.

| Параметр | Значение |
|---|---|
| Схема БД | `catalog` (чейнджлоги Liquibase внутри модуля) |
| Публичный пакет | `com.aura.catalog.api` (`@NamedInterface("api")` — единственная точка входа) |
| Зависимости | только `modules/shared` (+ Spring Data JPA / Modulith) |
| Потребители | `apps/rest-api` (`CatalogController`); с этапа 3 — модуль `entry` (проверка id элементов) |
| Кандидат в микросервис | да — общий справочный сервис для core и бота ([architecture-overview.md](../architecture-overview.md) §8) |

## 2. Публичный контракт (`com.aura.catalog.api`)

### 2.1 Порты (по одному на справочник, 4 операции: list/create/update/delete)

| Порт | Модели | Особенности |
|---|---|---|
| `EmotionsPort` | `Emotion`, `CreateEmotion`, `UpdateEmotion` | `color` (RGB int, nullable) |
| `FactorsPort` | `Factor`, `CreateFactor`, `UpdateFactor` | — |
| `EventsPort` | `CatalogEvent`, `CreateCatalogEvent`, `UpdateCatalogEvent` | системных пресетов нет — события строго персональны |
| `MetricsPort` | `TrackedMetric`, `CreateTrackedMetric`, `UpdateTrackedMetric` | шкала `minValue`/`maxValue` (`BigDecimal`, по умолчанию 1 и 5), `unit` |

Семантика единая:

- `list(userId, includeInactive)` — системные (с этапа 2.2) + персональные пользователя, `order by sort_order, id`; неактивные отдаются только с `includeInactive = true`;
- `create` — создаёт персональный элемент; `sortOrder = null` → сервер присваивает следующий (max + 1);
- `update` — PATCH-семантика: `null` поле не менять;
- `delete` — только персональные.

### 2.2 Ошибки (RFC 9457 problem+json)

| Исключение | HTTP | `code` |
|---|---|---|
| `CatalogItemNameAlreadyExistsException` | 409 | `NAME_ALREADY_EXISTS` |
| `InvalidMetricScaleException` (minValue ≥ maxValue) | 422 | `VALIDATION_FAILED`, `errors=[{field: "maxValue"}]` |
| чужой или несуществующий элемент | 404 | `NOT_FOUND` (через `shared.NotFoundException` — существование чужого не раскрываем) |
| правка системного не админом (этап 2.2) | 403 | `SYSTEM_ITEM_FORBIDDEN` |

## 3. Паттерн «системное + персональное»

- `owner_user_id IS NULL` — системный пресет, общий для всех; иначе — персональный элемент владельца.
- Системные элементы правят **только админы** (роль `ADMIN` из JWT; этап 2.2). Пользователи персонализируют: создают свои копии (дубль имени системного — легален) и деактивируют свои.
- Уникальность `(owner_user_id, name)` в Postgres не действует для строк с `NULL` владельцем — системные наборы контролирует seed, персональные — БД + проверка в сервисе.

## 4. Данные (схема `catalog`)

| Таблица | Колонки | Constraints |
|---|---|---|
| `emotions` | `owner_user_id`, `name(100)`, `color int`, `icon(128)`, `is_active`, `sort_order` | `pk_emotions`, `uq_emotions_owner_name` |
| `factors` | `owner_user_id`, `name(100)`, `icon(128)`, `is_active`, `sort_order` | `pk_factors`, `uq_factors_owner_name` |
| `events` | как `factors` | `pk_events`, `uq_events_owner_name` |
| `tracked_metrics` | `owner_user_id`, `name(100)`, `min_value numeric`, `max_value numeric`, `unit(16)`, `is_active`, `sort_order` | `pk_tracked_metrics`, `uq_tracked_metrics_owner_name` |

- Чейнджлоги: `db/changelog/catalog/changesets/` — `000-create-schema`, `001-create-emotions`, `002-create-factors`, `003-create-events`, `004-create-tracked-metrics`; с этапа 2.2 — `005-create-user-hidden-items` (пер-пользовательское скрытие системных) и `900-seed-*` (пресеты, context `seed`).
- FK в схеме нет: `owner_user_id` — ссылка на `auth.users.id` по договорённости, без FK (правило границ модулей).
- Запросы листинга (`findVisible`) уже учитывают системные строки (`owner_user_id IS NULL`) — готовы к этапу 2.2.
- Поиск с throw («найти свой элемент или 404») живёт в репозиториях: `requireOwnedBy(id, ownerUserId)` поверх derived-запроса `findByIdAndOwnerUserId`. Сервисы получают готовую сущность и не содержат `findById().orElseThrow()`.
- Точка записи — явная: `create` и `update` завершаются вызовом `save()` (хотя dirty checking Hibernate сохранил бы изменения и без него — явный вызов делает место записи видимым в коде).

## 5. Интеграция в приложение (`apps/rest-api`)

- `web.catalog.CatalogController` реализует сгенерированный `CatalogApi` (тег `catalog`, 16 операций `/api/v1/catalog/{emotions,factors,events,metrics}`); маппинг контракта — `web.catalog.CatalogResponses` (алиасы `Valence` между `com.aura.api.model` и `com.aura.catalog.api`).
- Публичные маршруты защищены пользовательским JWT (`/api/v1/**` → `ROLE_USER|ROLE_ADMIN`, см. `SecurityConfig`); идентификатор пользователя — `CurrentUser.requireUserId()`.
- Транзакции — в сервисах модуля; контроллер их не открывает (проверяется `ArchitectureTest`).

## 6. Тесты

- `CatalogControllerTest` (`@WebMvcTest`): маппинг контракта, 201/200/204, 401, 404 (чужое), 409 (дубль имени), 422 (имя 1–100, Bean Validation на сгенерированных DTO); MockK-порты, `@WithMockAuraUser`.
- `CatalogFlowIntegrationTest` (`@SpringBootTest` + Testcontainers Postgres + Liquibase master): полный CRUD эмоций c изоляцией пользователей, дефолты и валидация шкалы метрик, CRUD факторов/событий, идемпотентность ошибок.
- `ArchitectureTest`: `catalog.internal` недоступен снаружи; модуль не зависит от web/security-слоёв.
- `ModulithVerificationTest`: границы `com.aura.catalog.api`.

## 7. Структура каталога

```
modules/catalog/src/main/
├── java/com/aura/catalog/api/package-info.java   # @NamedInterface("api")
├── kotlin/com/aura/catalog/api/                  # публичный контракт: порты, модели, ошибки
└── kotlin/com/aura/catalog/internal/
    ├── entity/        # EmotionEntity, FactorEntity, EventEntity, TrackedMetricEntity
    ├── repository/    # Spring Data + @Query findVisible (системные + свои)
    ├── service/       # EmotionService, FactorService, EventService, MetricService
    └── mapping/       # entity -> api-модель
```

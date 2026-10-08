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

- `list(userId, includeInactive)` — системные (не скрытые пользователем) + персональные пользователя, `order by sort_order, id`; с `includeInactive = true` добавляются деактивированные персональные и скрытые системные (`isActive=false` в ответе);
- `create` — создаёт персональный элемент (и для админа тоже); `sortOrder = null` → сервер присваивает следующий (max + 1);
- `update(userId, id, command, isAdmin)` — PATCH-семантика (`null` поле не менять), роль берётся из JWT-клеймов (`CurrentUser.isAdmin()`):
  - персональная запись — правит владелец;
  - **системная + ADMIN** — правка полей системной строки (включая глобальную деактивацию `isActive`);
  - **системная + USER** — только `{isActive:false|true}`: персональное скрытие/возврат через `user_hidden_items`; любые другие поля или пустой PATCH — 403;
- `delete` — только персональные записи; системные не удаляются никем (403).

### 2.2 Ошибки (RFC 9457 problem+json)

| Исключение | HTTP | `code` |
|---|---|---|
| `CatalogItemNameAlreadyExistsException` | 409 | `NAME_ALREADY_EXISTS` |
| `InvalidMetricScaleException` (minValue ≥ maxValue) | 422 | `VALIDATION_FAILED`, `errors=[{field: "maxValue"}]` |
| `SystemItemForbiddenException` | 403 | `SYSTEM_ITEM_FORBIDDEN` (правка полей системного не админом, пустой PATCH системного, удаление системного) |
| чужой или несуществующий элемент | 404 | `NOT_FOUND` (через `shared.NotFoundException` — существование чужого не раскрываем) |

## 3. Паттерн «системное + персональное»

- `owner_user_id IS NULL` — системный пресет, общий для всех; иначе — персональный элемент владельца.
- Системные элементы правит **только ADMIN** — через те же эндпоинты (роль — параметр `isAdmin` в `update`, значение из JWT-клеймов). Пользователи персонализируют: создают свои копии (дубль имени системного — легален), деактивируют свои записи и **скрывают системные лично**.
- Персональное скрытие: `PATCH системного {isActive:false}` → запись `(user_id, EMOTION|FACTOR|EVENT|METRIC, item_id)` в `catalog.user_hidden_items`; `{isActive:true}` — возврат (идемпотентно). Скрытие влияет только на листинг этого пользователя; в ответе `isActive=false, system=true`.
- Уникальность `(owner_user_id, name)` в Postgres не действует для строк с `NULL` владельцем — системные наборы контролирует seed, персональные — БД + проверка в сервисе (для админ-правок системной строки — `existsByOwnerUserIdIsNullAndName`).

## 4. Данные (схема `catalog`)

| Таблица | Колонки | Constraints |
|---|---|---|
| `emotions` | `owner_user_id`, `name(100)`, `color int`, `icon(128)`, `is_active`, `sort_order` | `pk_emotions`, `uq_emotions_owner_name` |
| `factors` | `owner_user_id`, `name(100)`, `icon(128)`, `is_active`, `sort_order` | `pk_factors`, `uq_factors_owner_name` |
| `events` | как `factors` | `pk_events`, `uq_events_owner_name` |
| `tracked_metrics` | `owner_user_id`, `name(100)`, `min_value numeric`, `max_value numeric`, `unit(16)`, `is_active`, `sort_order` | `pk_tracked_metrics`, `uq_tracked_metrics_owner_name` |
| `user_hidden_items` | `user_id`, `item_type (EMOTION\|FACTOR\|EVENT\|METRIC)`, `item_id`, `created_at` | `pk_user_hidden_items`, `uq_user_hidden_items (user_id, item_type, item_id)` |

- Чейнджлоги: `db/changelog/catalog/changesets/` — `000-create-schema`, `001…004` (таблицы справочников), `005-create-user-hidden-items`, seed `900-seed-emotions` (13 пресетов), `901-seed-factors` (8), `902-seed-tracked-metrics` (4) — `context: seed`, идемпотентны (preCondition «системный набор пуст» + `MARK_RAN`).
- FK в схеме нет: `owner_user_id`/`user_id`/`item_id` — ссылки по договорённости, без FK (правило границ модулей); целостность скрытия — в сервисе.
- Листинг: репозитарный `findAllForUser(userId)` возвращает свои (любые) + активные системные; сервис вычитает скрытые (`findItemIds`) и фильтрует по `includeInactive`, проставляя `isActive` глазами пользователя.
- Поиск с throw («найти свой элемент или 404») живёт в репозиториях: `requireOwnedBy(id, ownerUserId)` поверх derived-запроса `findByIdAndOwnerUserId`. Сервисы получают готовую сущность и не содержат `findById().orElseThrow()`.
- Точка записи — явная: `create` и `update` завершаются вызовом `save()` (хотя dirty checking Hibernate сохранил бы изменения и без него — явный вызов делает место записи видимым в коде).

## 5. Интеграция в приложение (`apps/rest-api`)

- `web.catalog.CatalogController` реализует сгенерированный `CatalogApi` (тег `catalog`, 16 операций `/api/v1/catalog/{emotions,factors,events,metrics}`); маппинг контракта — `web.catalog.CatalogResponses`.
- Публичные маршруты защищены пользовательским JWT (`/api/v1/**` → `ROLE_USER|ROLE_ADMIN`, см. `SecurityConfig`); в сервисы передаются `CurrentUser.requireUserId()` и `CurrentUser.isAdmin()` (роль из JWT-клеймов).
- Транзакции — в сервисах модуля; контроллер их не открывает (проверяется `ArchitectureTest`).

## 6. Тесты

- `CatalogControllerTest` (`@WebMvcTest`): маппинг контракта, 201/200/204, 401, 404 (чужое), 409 (дубль имени), 422 (имя 1–100, Bean Validation на сгенерированных DTO); проброс `isAdmin` из роли в порт; MockK-порты, `@WithMockAuraUser`.
- `CatalogFlowIntegrationTest` (`@SpringBootTest` + Testcontainers Postgres + Liquibase master):
  - системные пресеты видны новому пользователю (13 эмоций / 8 факторов / 4 метрики, события — пусто);
  - идемпотентность seed: seed-чейнджсеты удаляются из `DATABASECHANGELOG`, повторный прогон переоценивает preConditions (`MARK_RAN`) и не дублирует строки;
  - скрытие/возврат системной эмоции (`{isActive}`), изоляция пользователей, 403 на правку полей и удаление системного;
  - ADMIN правит системную строку через тот же эндпоинт (глобальный эффект), глобальная деактивация убирает элемент у всех;
  - персональный CRUD по всем справочникам, уникальность имён, шкала метрик.
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

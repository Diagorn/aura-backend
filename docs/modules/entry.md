# Модуль entry

> Ядро: записи чек-ина — эмоции с интенсивностью/влиянием, факторы, события, метрики. Владелец схемы БД `entry`.
> Правила границ модулей — [architecture-overview.md](../architecture-overview.md), REST-контракт — [api-design.md](../api-design.md), жизненный цикл и таймзоны — [domain-model.md](../domain-model.md).

## 1. Ответственность и границы

**Делает:**

- создание записи одной командой с вложенными коллекциями (`POST /entries`, `status=COMPLETED`);
- DRAFT-цикл: частичное сохранение и дозаполнение через `PATCH` (переданная коллекция заменяется целиком);
- вычисление `entry_date` по таймзоне пользователя (`UserProfilePort`) из момента сохранения;
- навигацию по датам: список за `date` либо `from+to`, фильтры `status`/`source`, пагинация;
- календарь месяца (`GET /entries/calendar?month=`): дни с числом записей и наличием завершённых;
- суточный лимит записей (включая черновики), настраиваемый (`aura.entry.max-per-day`, по умолчанию 5);
- валидацию состава: интенсивность/влияние 1–5, значение метрики в шкале своей метрики, ≥ 1 эмоция для `COMPLETED`;
- публикацию фактов `EntryCompleted` / `EntryUpdated` / `EntryDeleted`.

**Не делает:**

- не владеет HTTP: контроллер живёт в `apps/rest-api` и является тонким адаптером над портом модуля;
- не владеет справочниками: имена элементов и шкалы метрик читает через публичные порты `catalog`;
- не считает агрегаты: `analytics` (этап 5) получит данные через query-порт модуля `entry`;
- не рассылает напоминания (это `notification`, этап 7).

| Параметр | Значение |
|---|---|
| Схема БД | `entry` (`entries`, `entry_emotions`, `entry_factors`, `entry_events`, `entry_metrics`; чейнджлоги внутри модуля) |
| Публичный пакет | `com.aura.entry.api` (`@NamedInterface("api")` — единственная точка входа) |
| Зависимости | `modules/shared`, `modules/user` (`UserProfilePort` — таймзона), `modules/catalog` (порты справочников) |
| Потребители | `apps/rest-api` (`EntryController`); с этапа 5 — модуль `analytics` (query-порт), этап 7 — `notification` (факты) |
| Кандидат в микросервис | да — вместе с `note` образует Tracking-сервис ([architecture-overview.md](../architecture-overview.md) §8) |

## 2. Публичный контракт (`com.aura.entry.api`)

### 2.1 Порт `EntriesPort`

| Операция | Семантика |
|---|---|
| `create(userId, command)` | запись одним командой; `entry_date` и `recorded_at` проставляет сервер; `source` не передан — `WEB` |
| `get(userId, id)` | полная запись с именами справочников на момент чтения; чужая/несуществующая — 404 |
| `update(userId, id, command)` | PATCH: `null` поле не менять; переданная коллекция заменяется целиком; `note=""` — очистить; `COMPLETED → DRAFT` запрещён |
| `delete(userId, id)` | удаляет запись и коллекции; доставляет `EntryDeleted` |
| `list(userId, filter, page, size, sort)` | `date` **или** `from+to` (взаимоисключающи), фильтры `status`/`source`; сортировка по умолчанию `recordedAt desc` |
| `calendar(userId, month)` | дни месяца в таймзоне пользователя, в которые есть записи; `month = null` — текущий |

Модели: `EntryDetails` (полная запись), `EntryPage` (конверт `{items, page, size, totalElements, totalPages}`), `EntryFilter`, `EntrySort`; команды `CreateEntry`/`UpdateEntry` с вложенными `EntryEmotionInput`, `EntryFactorInput`, `EntryEventInput`, `EntryMetricInput`.

### 2.2 Ошибки (RFC 9457 problem+json)

| Исключение | HTTP | `code` |
|---|---|---|
| `DailyEntriesLimitExceededException` | 409 | `ENTRY_DAILY_LIMIT_EXCEEDED` |
| `EntryValidationException` / `EntryFilterValidationException` | 422 | `VALIDATION_FAILED` (`errors` — по полям: `emotions[0].emotionId`, `date`, `metrics[2].value`…) |
| `InvalidEntryStatusTransitionException` (COMPLETED → DRAFT) | 422 | `VALIDATION_FAILED`, `errors=[{field: "status"}]` |
| чужая или несуществующая запись | 404 | `NOT_FOUND` (через `shared.NotFoundException`) |

Валидация состава: интенсивность/влияние 1–5; значение метрики в `[minValue, maxValue]` своей метрики; ≥ 1 эмоция для `COMPLETED`; дубликаты id внутри коллекции запрещены (в БД — unique `(entry_id, <item>_id)`); элемент справочника должен быть **системным или личным пользователя** и **активным** (`isActive=true`; пер-пользовательское скрытие системных на запись не влияет). Все ошибки коллекций собираются в один problem (422) со списком `errors`.

### 2.3 Факты (`EntryEvents`)

| Факт | Когда | Потребители |
|---|---|---|
| `EntryCompleted` | создана сразу `COMPLETED` или переход `DRAFT → COMPLETED` | analytics (этап 5), notification (этап 7 — закрыть «напоминание на сегодня») |
| `EntryUpdated` | успешный PATCH без перехода в COMPLETED | analytics |
| `EntryDeleted` | удаление записи | analytics |

Доставка — синхронный вызов `EntryEventsPort` в транзакции продюсера (`EntryEventsDispatcher`, тот же паттерн, что `AuthEventsDispatcher` в auth); реализаций сегодня нет — вызовы no-op.

## 3. Данные

| Таблица | Назначение |
|---|---|
| `entries` | `user_id`, `entry_date` (локальная дата), `recorded_at` (UTC), `source` (`WEB/TELEGRAM`), `status` (`DRAFT/COMPLETED`), `note`, таймстемпы |
| `entry_emotions` | `(entry_id, emotion_id)` unique, `intensity`, `influence` — CHECK 1–5 |
| `entry_factors` | `(entry_id, factor_id)` unique, `intensity` nullable — CHECK 1–5 |
| `entry_events` | `(entry_id, event_id)` unique |
| `entry_metrics` | `(entry_id, metric_id)` unique, `value numeric` (валидация шкалы — в сервисе, диапазон у каждой метрики свой) |

- FK — только внутри схемы `entry` (`… → entries`, `ON DELETE CASCADE`); ссылки на `catalog.*` — по id, без FK (см. [architecture-overview.md](../architecture-overview.md) §4).
- Индексы: `idx_entries_user_date (user_id, entry_date)` — «быстрый возврат к дате»; `idx_entries_user_date_status (user_id, entry_date, status)` — календарь; индексы по id элементов коллекций.
- `entry_date` вычисляется при создании (`LocalDate.ofInstant(recorded_at, users.timezone)`) и **не меняется** при PATCH — правки не переносят запись между днями. При смене таймзоны пользователем исторические записи остаются на своих датах.
- `recorded_at` проставляется сервером при создании и не меняется; `updated_at` обновляется при каждой записи.
- Чейнджлоги: `modules/entry/src/main/resources/db/changelog/entry/changesets/` (`000` — схема, `001`–`005` — таблицы и индексы).

## 4. Кросс-модульное взаимодействие

- **user**: `UserProfilePort.get(userId).timezone` — источник таймзоны для `entry_date`.
- **catalog**: `findVisibleByIds(userId, ids)` на четырёх портах (`EmotionsPort`, `FactorsPort`, `EventsPort`, `MetricsPort`) — батч-валидация элементов (один запрос на коллекцию) и имена для ответов. Отсутствие id в результате — элемент не существует или чужой; `isActive=false` — использовать в записи нельзя. Скрытые пользователем системные элементы остаются пригодными для записи: скрытие влияет только на листинг.
- **Имена элементов в ответе** — на момент чтения; удалённый из каталога персональный элемент даёт пустое имя в записях (id сохраняются, денормализации нет).

## 5. Тесты

| Тест | Уровень | Покрывает |
|---|---|---|
| `EntryControllerTest` (`@WebMvcTest`) | slice | маппинг контракта, 401/404/422, разбор `sort`/`month`, календарь |
| `EntryFlowIntegrationTest` (Testcontainers) | интеграция | полный чек-ин, DRAFT-цикл, лимит 409, валидации 422 (в т.ч. чужая эмоция), таймзона `Asia/Kamchatka`, календарь, замена коллекций, доставка фактов через `EntryEventsPort` |
| `ModulithVerificationTest`, `ArchitectureTest` | архитектура | границы: наружу только `com.aura.entry.api` |

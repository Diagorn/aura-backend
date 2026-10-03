# Дизайн REST API

> Конвенции, эндпоинты, контракты ошибок. Спека — источник истины: см. [spec-first-workflow.md](spec-first-workflow.md).

## Базовые конвенции

- База: `http://localhost:8080` (dev). Все публичные эндпоинты — под `/api/v1`, внутренние — `/internal/v1`.
- Формат: `application/json; charset=utf-8`. Имена полей — `camelCase`. Идентификаторы — целые числа.
- Даты: инстанты — ISO 8601 с UTC-офсетом (`2026-10-03T20:15:00Z`); локальные даты — `yyyy-MM-dd`. Сервер принимает UTC-инстанты и сам вычисляет `entry_date` по таймзоне пользователя.
- Enum'ы — `SCREAMING_SNAKE_CASE` (`DRAFT`, `TELEGRAM`, `NEGATIVE`).
- Версия — в пути. В рамках `v1` только аддитивные изменения; breaking — через `/api/v2` (см. §Версионирование).

## Аутентификация

- Заголовок: `Authorization: Bearer <accessToken>` (кроме `auth/register`, `auth/login`, `auth/refresh`).
- Access-токен: 15 минут. Refresh: 30 дней, **ротация** — при каждом refresh старый отзывается, выдаётся новый; повторное использование отозванного → отзыв всей цепочки + `401`.
- Алгоритм: HS256 в dev (секрет из env), к продовой версии — асимметричный ключ + JWKS.
- Claims: `sub` (id пользователя), `typ` (`access` | `refresh` | `service`), `iat`, `exp`, `jti`, `roles` (`USER`, `ADMIN`). Сервисные токены бота: `typ=service`, `sub=<client_id>`, `scope=internal`.

## Ошибки — RFC 9457 (`application/problem+json`)

```json
{
  "type": "https://aura.example.com/problems/validation-failed",
  "title": "Validation failed",
  "status": 422,
  "detail": "Некоторые поля некорректны",
  "instance": "/api/v1/entries",
  "code": "VALIDATION_FAILED",
  "errors": [
    { "field": "emotions", "message": "должна быть минимум одна эмоция для COMPLETED" }
  ]
}
```

| Статус | Когда |
|---|---|
| 400 | некорректный синтаксис запроса |
| 401 | нет/просрочен токен, reuse refresh-токена |
| 403 | доступ к чужому ресурсу / правка системного элемента |
| 404 | ресурс не найден (в т.ч. чужой — не раскрываем существование) |
| 409 | конфликт состояния: > 5 записей в сутки, повторный линк Telegram |
| 422 | бизнес-валидация (`code=VALIDATION_FAILED`) |
| 429 | rate limit (заголовки `Retry-After`) |

## Пагинация и фильтры

- `?page=0&size=20` (max 100), сортировка `?sort=recordedAt,desc`.
- Ответ-конверт: `{ "items": [...], "page": 0, "size": 20, "totalElements": 124, "totalPages": 7 }`.
- Периоды для выборок и аналитики: либо `from`+`to` (локальные даты, включительно), либо `period=week|month|half_year|year` (относительно сегодня в таймзоне пользователя). Максимум — 1 год.

## Эндпоинты

### Auth

| Метод | Путь | Описание |
|---|---|---|
| POST | `/api/v1/auth/register` | `{email, password, timezone?}` → 201 `{user, accessToken, refreshToken}` |
| POST | `/api/v1/auth/login` | `{email, password}` → `{user, accessToken, refreshToken}` |
| POST | `/api/v1/auth/refresh` | `{refreshToken}` → новая пара (ротация) |
| POST | `/api/v1/auth/logout` | `{refreshToken}` → 204, отзыв |
| POST | `/api/v1/auth/telegram/link-code` | 201 `{code, expiresAt}` — код на 10 минут для `/link` в боте |

### Профиль

| Метод | Путь | Описание |
|---|---|---|
| GET | `/api/v1/me` | `{id, email, timezone, locale, telegram: {userId, username} или null}` — `telegram.userId` нужен боту для отправки сообщений (chat_id в личном чате совпадает с ним), обновляется при линке/exchange |
| PATCH | `/api/v1/me` | `{timezone?, locale?}` |
| PUT | `/api/v1/me/password` | `{currentPassword, newPassword}` |

### Справочники (единый шаблон для `emotions`, `factors`, `events`, `metrics`)

| Метод | Путь | Описание |
|---|---|---|
| GET | `/api/v1/catalog/emotions?includeInactive=false` | системные + персональные |
| POST | `/api/v1/catalog/emotions` | создать персональную → 201 |
| PATCH | `/api/v1/catalog/emotions/{id}` | правка/деактивация (`{isActive: false}`); системные → 403 |
| DELETE | `/api/v1/catalog/emotions/{id}` | только персональные |

Метрики дополнительно отдают шкалу: `{id, name, minValue, maxValue, unit, isActive}`.

### Записи (entries)

| Метод | Путь | Описание |
|---|---|---|
| POST | `/api/v1/entries` | создать (`status: DRAFT | COMPLETED`), 201 |
| GET | `/api/v1/entries` | фильтры: `date=` **или** `from`+`to`; `status`, `source`; пагинация |
| GET | `/api/v1/entries/{id}` | полная запись |
| PATCH | `/api/v1/entries/{id}` | дозаполнение/правки; переданные коллекции заменяются целиком |
| DELETE | `/api/v1/entries/{id}` | 204 |
| GET | `/api/v1/entries/calendar?month=2026-10` | `{month, days: [{date, entriesCount, hasCompleted}]}` — календарик для фронта и бота |

Пример создания:

```json
POST /api/v1/entries
{
  "status": "COMPLETED",
  "emotions": [
    { "emotionId": 12, "intensity": 4, "influence": 2 }
  ],
  "factors": [
    { "factorId": 4, "intensity": 3 }
  ],
  "events": [
    { "eventId": 9 }
  ],
  "metrics": [
    { "metricId": 2, "value": 4 }
  ],
  "note": "Тяжёлый день на работе"
}
```

Ответ 201 (сервер заполнил `entryDate` по таймзоне):

```json
{
  "id": 1024,
  "userId": 7,
  "entryDate": "2026-10-03",
  "recordedAt": "2026-10-03T20:15:00Z",
  "source": "WEB",
  "status": "COMPLETED",
  "emotions": [ { "emotionId": 12, "name": "Тревога", "intensity": 4, "influence": 2 } ],
  "factors":  [ { "factorId": 4, "name": "Работа", "intensity": 3 } ],
  "events":   [ { "eventId": 9, "name": "Ссора с коллегой" } ],
  "metrics":  [ { "metricId": 2, "name": "Энергия", "value": 4 } ],
  "note": "Тяжёлый день на работе"
}
```

`POST /entries` опционально принимает заголовок `Idempotency-Key` (≤ 64 символов): повтор с тем же ключом в течение 24 ч возвращает сохранённый ответ. Реализуется на этапе хардендинга (см. [roadmap.md](roadmap.md)).

### Заметки и шаблоны

| Метод | Путь | Описание |
|---|---|---|
| GET | `/api/v1/notes?entryId=&from=&to=&q=` | свои заметки, пагинация |
| POST | `/api/v1/notes` | `{content, entryId?}` → 201 (свободная или привязанная к записи) |
| GET/PATCH/DELETE | `/api/v1/notes/{id}` | CRUD своих |
| GET | `/api/v1/note-templates` | системные + персональные |
| POST | `/api/v1/note-templates` | создать персональный |
| PATCH/DELETE | `/api/v1/note-templates/{id}` | только персональные |

### Аналитика (все принимают `from`+`to` либо `period`)

| Метод | Путь | Ответ (сокращённо) |
|---|---|---|
| GET | `/api/v1/analytics/summary` | `{entriesCount, filledDays, streakDays, topEmotions[], avgMetrics[]}` |
| GET | `/api/v1/analytics/emotions/top?limit=5` | `[{emotionId, name, avgIntensity, count}]` — самые выраженные за период |
| GET | `/api/v1/analytics/emotions/{id}/timeline?bucket=day\|week\|month` | `{points: [{date, avgIntensity, count}]}` |
| GET | `/api/v1/analytics/metrics/{id}/timeline?bucket=` | `{points: [{date, value}]}` — настроение, энергия и др. |
| GET | `/api/v1/analytics/correlations/emotions-factors` | `[{emotionId, factorId, coOccurrences, avgIntensityWith, avgIntensityWithout}]` |
| GET | `/api/v1/analytics/correlations/emotions-events` | аналогично факторам |

Корреляции отдаём как данные (совместная встречаемость и сдвиг средней интенсивности); интерпретацию («фактор усиливает эмоцию») делает клиент.

### Напоминания (пользовательские настройки)

| Метод | Путь | Описание |
|---|---|---|
| GET | `/api/v1/reminders` | `{channel, times: ["09:00","21:30"], days: "DAILY", enabled}` |
| PUT | `/api/v1/reminders/schedule` | обновление расписания (1–3 локальных времени) |

## Internal API (только для бота)

Защита — сервисный JWT (`typ=service`, `scope=internal`). Не публикуется во внешнюю документацию, помечен тегом `internal` в спеке.

| Метод | Путь | Описание |
|---|---|---|
| POST | `/internal/v1/auth/service-token` | `{clientId, clientSecret}` → сервисный JWT (бот кэширует до exp) |
| POST | `/internal/v1/auth/telegram/link` | `{code, telegramUserId, username?}` (сервисный JWT) → привязывает пользователя по коду из `/api/v1/auth/telegram/link-code`; 404 — код не найден/истёк, 409 — аккаунт уже связан |
| POST | `/internal/v1/auth/telegram/exchange` | `{telegramUserId, username?}` → `{accessToken, expiresIn}`; 404, если аккаунт не связан. Бот передаёт свежий `username` — core обновляет его в профиле |
| GET | `/internal/v1/notifications/due?channel=telegram&limit=50` | напоминания к отправке |
| POST | `/internal/v1/notifications/{id}/ack` | 204 — отметить отправленным |

## Версионирование

- Внутри `v1`: новые опциональные поля, новые эндпоинты — не breaking.
- Переименование/удаление полей, сужение типов, изменение семантики — breaking: вводим `v2`, `v1` живёт период деградации с заголовками `Deprecation` и `Sunset`.
- Спека версионируется в git вместе с кодом; каждый breaking change фиксируется в PR, меняющем `info.version` спеки.

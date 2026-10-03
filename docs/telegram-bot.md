# Telegram-бот — отдельный репозиторий

> Бот живёт в собственном репозитории (далее `aura-telegram-bot`) и **не является частью `aura-backend`**. Этот документ — его проектирование: архитектура, инфраструктура и контракт с core API. Бот — тонкий адаптер: диалоги и кнопки — у него, данные и логика — в REST API core. Своей БД нет; состояние диалогов — свой Redis.

## Репозиторий и инфраструктура

```
aura-telegram-bot/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml      # версии — только здесь
├── src/main/kotlin/...            # Kotlin + Spring Boot (единый модуль на старте)
├── docker/
│   └── Dockerfile                 # JDK 21, образ приложения
├── docker-compose.dev.yml         # бот + redis (core поднимается из aura-backend)
└── .github/workflows/ci.yml       # build, test, detekt
```

- **Стек:** Kotlin 2.x, JDK 21, Spring Boot 4, `org.telegram:telegrambots`, Spring Data Redis (состояние диалогов, кэш токенов), actuator; detekt; свой `libs.versions.toml`.
- **Клиент core:** генерируется из **опубликованного артефакта спеки** core (CI `aura-backend` бандлит `api/openapi` через `redocly bundle` и публикует GitHub Release/артефакт; см. [spec-first-workflow.md](spec-first-workflow.md)). Бот пинит версию спеки и генерирует клиент у себя (openapi-generator, `library=spring-http-interface`); обновление версии — осознанный PR.
- **CI:** сборка, тесты, detekt; сборка docker-образа. CD — по мере появления стенда.

## Запуск и деплой

- **Dev:** `docker compose -f docker-compose.dev.yml up` → **long polling**, публичный HTTPS не нужен; `CORE_BASE_URL=http://host.docker.internal:8080` (core запущен в `aura-backend`).
- **Prod:** **webhook** (Telegram требует HTTPS) за nginx/traefik с TLS; healthcheck — `/actuator/health` на `:8081`.
- **Redis** — собственный инстанс бота (не общий с core).

## Слои

```
Telegram adapter   — приём updates, клавиатуры, отправка сообщений (telegrambots)
Application        — сценарии/состояния диалога (машина состояний)
Core client        — сгенерированный из спеки (HttpExchange + RestClient)
```

## Аутентификация

```mermaid
sequenceDiagram
    participant B as Бот (aura-telegram-bot)
    participant C as Core API (aura-backend)
    Note over B,C: 1. Сервисная аутентификация (при старте / по exp)
    B->>C: POST /internal/v1/auth/service-token {clientId, clientSecret}
    C-->>B: service JWT (typ=service, scope=internal)
    Note over B,C: 2. Пользователь не связан
    B->>B: /start → нет линка
    B-->>B: «Возьмите код на сайте: Профиль → Привязать Telegram»
    B->>B: /link 8f3a…
    B->>C: POST /internal/v1/auth/telegram/link {code, telegramUserId, username} (service JWT)
    C-->>B: 204, событие TelegramAccountLinked
    Note over B,C: 3. Связанный пользователь
    B->>C: POST /internal/v1/auth/telegram/exchange {telegramUserId, username} (service JWT)
    C-->>B: user accessToken (15 мин; кэш в Redis до exp)
    B->>C: обычные вызовы /api/v1/** с пользовательским токеном
```

- Связка `telegram_id ↔ user` хранится в core (`auth.users.telegram_id`, уникальный) вместе с `telegram_username` — бот передаёт свежий `username` при link и exchange, поэтому `GET /api/v1/me` всегда отдаёт актуальные `{userId, username}`.
- `telegram_id` для приватных чатов совпадает с `chat_id` — по нему бот доставляет уведомления.
- Повторный `/link` с уже связанным аккаунтом → 409; неверный/истёкший код → 404.
- Пользовательский токен кэшируется в Redis; при 401 от core — повторный exchange.

## Сценарии MVP

| Сценарий | Реализация |
|---|---|
| `/start`, `/help` | приветствие, краткая справка, статус связки |
| **Чек-ин** | пошаговые inline-клавиатуры: эмоции (multi-select) → интенсивность (1–5) → влияние (1–5) → факторы → события → метрики (1–5) → «Сохранить / В черновик». Всё через `POST/PATCH /entries` (DRAFT на время диалога) |
| **Просмотр за дату** | кнопки «Сегодня / Вчера / Выбрать дату» (inline-календарь) → `GET /entries?date=…`; список записей дня с деталями |
| Быстрая заметка | `POST /notes` |
| Настройка напоминаний | инлайн-меню времён → `PUT /reminders/schedule` |

Сессия диалога: ключ `tg:dialog:{chatId}` в Redis, TTL 15 мин; «Отмена» сбрасывает.

## Напоминания

- Бот раз в 30–60 с опрашивает `GET /internal/v1/notifications/due?channel=telegram&limit=50`, отправляет и подтверждает `POST /internal/v1/notifications/{id}/ack` (ack — только после успешной отправки; доставка at-least-once, дубликаты допустимы).
- Адресат — `telegram_id` из профиля пользователя (= `chat_id`).
- При распиле core пуллинг заменяется Kafka-консьюмером; контракт сообщений сохраняется (см. [architecture-overview.md](architecture-overview.md)).

## Конфигурация (env)

| Переменная | Назначение |
|---|---|
| `TELEGRAM_BOT_TOKEN` | токен от @BotFather |
| `CORE_BASE_URL` | `http://localhost:8080` (dev, core из `aura-backend`) / URL прода |
| `CORE_CLIENT_ID`, `CORE_CLIENT_SECRET` | сервисные креды для `/internal` (core инициализирует их seed-чейнджлогом) |
| `REDIS_HOST`, `REDIS_PORT` | состояние диалогов, кэш токенов (свой Redis бота) |

`server.port=8081` — только actuator (`/actuator/health`), своего REST API у бота нет.

## Отказоустойчивость

- Core недоступен → сообщение «сервис временно недоступен, попробуйте позже», retry с backoff в фоне для команд чтения.
- Лимиты Telegram (~30 msg/s на бота): исходящая отправка через очередь с троттлингом.
- Все входящие updates обрабатываются в отдельном executor'е — long polling не блокируется.

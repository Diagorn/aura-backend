# Архитектура: Aura — трекер эмоций

Aura — сервис самонаблюдения: пользователь 1–3 раза в день фиксирует эмоции (их интенсивность и влияние), факторы, события и опциональные показатели (энергия, тревожность, либидо и т.п.). По накопленным данным строятся графики и связи. Доступ — через веб-фронтенд и Telegram-бота. Всё, что пользователь отслеживает (эмоции, факторы, события, метрики, шаблоны заметок), кастомизируемо.

## Документы

| Документ | О чём |
|---|---|
| [system-overview.md](system-overview.md) | система целиком в рантайме: путь запроса, сквозные сценарии, карта документации |
| [domain-model.md](domain-model.md) | сущности, ERD, жизненный цикл записи, таймзоны |
| [api-design.md](api-design.md) | конвенции REST, эндпоинты, ошибки, internal-API |
| [spec-first-workflow.md](spec-first-workflow.md) | OpenAPI-спека как источник истины, генерация кода |
| [gradle-structure.md](gradle-structure.md) | multi-module, `libs.versions.toml`, convention-плагины |
| [liquibase-migrations.md](liquibase-migrations.md) | организация миграций по модулям |
| [telegram-bot.md](telegram-bot.md) | архитектура бота, связка аккаунтов, напоминания |
| [modules/auth.md](modules/auth.md) | модуль `auth`: контракт, JWT, потоки, схема БД |
| [local-dev.md](local-dev.md) | docker-compose, запуск, команды |
| [roadmap.md](roadmap.md) | этапы реализации |

## 1. Общий подход

- **Модульный монолит** (Spring Boot 4 + Spring Modulith) для REST API: один деплой, но жёсткие границы между доменными модулями, проверяемые тестами.
- **Telegram-бот — отдельное приложение в отдельном репозитории** (`aura-telegram-bot`): общается с монолитом по REST (как обычный клиент), своей БД не имеет. В этом репозитории — только core API; архитектура и инфраструктура бота: [telegram-bot.md](telegram-bot.md).
- Каждый модуль спроектирован как **кандидат в микросервис**: изолированная схема БД, публичный API, доменные события.

## 2. Компоненты системы

```mermaid
flowchart LR
    FE[Веб-фронтенд<br/>(отдельный репозиторий)] -->|REST /api/v1 + JWT| API
    TB["Telegram-бот<br/>(отдельный репозиторий)"] -->|REST /api/v1 + /internal + JWT| API
    TB <-->|Bot API: long polling / webhook| TG[Telegram]
    API["REST API монолит<br/>apps/rest-api"]
    API --> PG[(PostgreSQL)]
    API --> RD[(Redis)]
```

Фронтенд и Telegram-бот — отдельные репозитории; контракт для обоих — OpenAPI-спека core (см. [spec-first-workflow.md](spec-first-workflow.md)).

## 3. Модули монолита

| Модуль | Ответственность |
|---|---|
| `shared` | общие типы: идентификаторы, базовые ошибки, утилиты дат/таймзон. Не зависит ни от кого |
| `auth` | регистрация/вход, JWT (access + refresh с ротацией), коды связки Telegram, сервисные токены для бота |
| `user` | профиль, таймзона, локаль, настройки |
| `catalog` | кастомизируемые справочники: эмоции, факторы, события, отслеживаемые метрики; системные пресеты + персональные записи |
| `entry` | ядро: чек-ины (эмоции + интенсивность/влияние, факторы, события, метрики), черновики, навигация по датам |
| `note` | заметки (свободные и привязанные к записи) и шаблоны (системные/персональные) |
| `analytics` | агрегации и графики. Данные получает через публичный query-API модуля `entry`, своих таблиц в MVP не имеет |
| `notification` | расписание напоминаний (1–3 раза в день), очередь «due» для бота |
| `apps/rest-api` | сборка: wiring модулей, security, Liquibase master, конфигурация |

## 4. Правила границ модулей

Проверяются ArchUnit + Spring Modulith verification-тестами (см. [gradle-structure.md](gradle-structure.md)).

1. Модуль публикует **только** пакет `….<module>.api` (интерфейсы + DTO). Всё остальное — `internal`, доступ извне запрещён.
2. Кросс-модульное взаимодействие — вызовы публичных интерфейсов и **доменные события** (Spring Modulith application events). Никаких прямых вызовов внутренних сервисов и репозиториев.
3. **Никаких SQL JOIN между схемами разных модулей.** Ссылки между модулями — по идентификаторам, без FK на уровне БД.
4. Одна схема БД на модуль (`auth`, `catalog`, `entry`, `note`, `notification`). Свои Liquibase-чейнджлоги внутри модуля.
5. Доменные события — только факты («запись завершена»), не команды. Потребитель не может повлиять на исход транзакции продюсера.

## 5. Доменные события

| Событие | Источник | Потребители | Зачем |
|---|---|---|---|
| `UserRegistered` | auth | notification | создать расписание напоминаний по умолчанию |
| `TelegramAccountLinked` | auth | notification | включить канал напоминаний «telegram» |
| `EntryCompleted` | entry | analytics, notification | пересчёт/регистрация данных; закрыть «напоминание на сегодня» |
| `EntryUpdated` | entry | analytics | инвалидация кэшей агрегатов |
| `EntryDeleted` | entry | analytics | пересчёт агрегатов |

В MVP доставка фактов — синхронный вызов порта (auth.api → `AuthEventsPort`) в транзакции продюсера, без асинхронщины: потребители реализуют порт своим бином и не должны бросать исключений. При распиле внешнализируются в Kafka/Rabbit без изменения контрактов (см. §8).

## 6. Данные

- PostgreSQL. Схема на модуль; `liquibase` — служебная схема для `DATABASECHANGELOG`.
- `timestamptz` хранится в UTC; локальная дата пользователя (`entry_date`) вычисляется по его IANA-таймзоне из профиля.
- Миграции — Liquibase, чейнджлоги живут внутри модулей (см. [liquibase-migrations.md](liquibase-migrations.md)).
- Redis: кэш справочников, rate limiting. Не источник истины. У бота (отдельный репозиторий) — свой Redis для состояния диалогов.

## 7. Безопасность

- Пользователи: JWT access (15 мин) + refresh (ротация, хэши в БД). См. [api-design.md](api-design.md).
- Бот: сервисный JWT (выдаётся по `client_id`/`client_secret`) для `/internal/**`; пользовательские действия — по краткоживущему пользовательскому токену, полученному через exchange (см. [telegram-bot.md](telegram-bot.md)).
- Роли: `USER`, `ADMIN` (управление системными справочниками и шаблонами).

## 8. Стратегия разделения на микросервисы

Распил не планируется сейчас, но архитектура к нему готова:

| Модуль(и) | Будущий сервис | Что нужно будет сделать |
|---|---|---|
| `auth` + `user` | Identity | выпустить ключи/JWKS, раздавать токены клиентам напрямую |
| `catalog` | Catalog | вынести схему, свой liquibase-раннер |
| `entry` + `note` | Tracking | то же; события → Kafka |
| `analytics` | Analytics | собственный read-model (сейчас читает через порт `entry`), консьюмер событий |
| `notification` | Notifications | консьюмер событий + собственный шедулер |
| `telegram-bot` | отдельный сервис с первого дня (свой репозиторий) | заменить polling `/internal/notifications/due` на Kafka-консьюмера |

Благодаря правилам §4 (изолированные схемы, отсутствие кросс-схемных JOIN, события вместо прямых вызовов) распил сводится в основном к инфраструктуре.

## 9. Стек (кратко)

- Kotlin 2.x, JDK 21, Gradle (Kotlin DSL, multi-module, `libs.versions.toml`)
- Spring Boot 4.x / Framework 7, Spring Modulith, Spring Security (JWT), Spring Data (JPA + Redis)
- PostgreSQL, Redis; Liquibase
- OpenAPI spec-first; detekt (+ ktlint); Kover; Testcontainers
- Telegram-бот — отдельный репозиторий: его стек и инфраструктура описаны в [telegram-bot.md](telegram-bot.md)
- Точные версии — только в `gradle/libs.versions.toml` (см. [gradle-structure.md](gradle-structure.md))

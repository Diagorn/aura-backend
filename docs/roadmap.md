# Roadmap

> Этапы идут в порядке зависимостей. MVP-1 = этапы 0–6; MVP-2 добавляет напоминания и хардендинг. Этапы 6–7 частично выполняются в **отдельном репозитории бота** (`aura-telegram-bot`, см. [telegram-bot.md](telegram-bot.md)).

## Этап 0 — Каркас

Gradle multi-module: `build-logic` с convention-плагинами (`aura.*`), `gradle/libs.versions.toml`, detekt (FAIL на warnings), Kover, `apps/rest-api` (Boot 4, actuator), docker-compose (postgres, redis, init-db.sql), Liquibase master + первые changesets схемы, скелет OpenAPI (`api.yaml` + auth/paths) и генерация (`aura.openapi`), CI-пайплайн (build, test, detekt, redocly lint) **+ публикация бандла спеки** (артефакт для фронтенда и репозитория бота).

**Готово, когда:** `./gradlew build` зелёный; `docker compose up -d` поднимает БД; rest-api стартует и накатывает master-чейнджлог; `/actuator/health` отвечает; бандл спеки публикуется CI.

## Этап 1 — Auth и профиль

Модуль `auth`: регистрация/логин/refresh с ротацией и reuse detection, JWT (HS256 dev), модуль `user` (таймзона/локаль), коды связки Telegram, сервисные токены для бота, RFC 9457 error handling, `@WebMvcTest` на контроллеры.

**Готово, когда:** register → login → refresh → logout проходят интеграционно; `/me` отдаёт профиль (включая `telegram: {userId, username}` после связки); internal service-token работает.

## Этап 2 — Catalog

Модуль `catalog`: CRUD эмоций/факторов/событий/метрик, паттерн «системное + персональное», seed-чейнджлог с пресетами, валидации, проверка прав (системные не редактируются).

**Готово, когда:** пользователь видит системные пресеты, создаёт/деактивирует свои; seed идемпотентен.

## Этап 3 — Entries (ядро)

Модуль `entry`: `POST /entries` с вложенными коллекциями, DRAFT-цикл (PATCH), навигация по датам (`?date=`, `from/to`, `calendar?month=`), индексы `(user_id, entry_date)`, лимит 5 записей/сутки, вычисление `entry_date` по таймзоне, события `EntryCompleted/Updated/Deleted` (Modulith).

**Готово, когда:** полный флоу чек-ина проходит через API; быстрый возврат к дате — один запрос; Modulith verification зелёный.

## Этап 4 — Notes

Модуль `note`: заметки (свободные + привязанные), шаблоны (системные + персональные, seed 2–3 штук).

**Готово, когда:** CRUD заметок и шаблонов работает; заметка при создании с `entryId` проверяет принадлежность записи.

## Этап 5 — Analytics MVP

Модуль `analytics` через query-порт `entry`: summary (streak, filled days), топ эмоций, таймлайны эмоций и метрик (day/week/month), корреляции эмоции↔факторы/события.

**Готово, когда:** все 6 аналитических эндпоинтов отвечают за week/month/half_year/year на данных этапа 3; интеграционные тесты на агрегациях.

## Этап 6 — Telegram-бот MVP (репозиторий `aura-telegram-bot`)

Отдельный репозиторий: каркас по [telegram-bot.md](telegram-bot.md) (Gradle, `libs.versions.toml`, detekt, Dockerfile, docker-compose.dev.yml, CI), генерация клиента из опубликованной спеки, связка аккаунтов, сервисный JWT, exchange, диалог чек-ина, просмотр за дату, быстрая заметка, long polling. На стороне core к этому моменту должны существовать `/internal/v1/auth/telegram/link|exchange` (этап 1).

**Готово, когда:** пользователь полностью проходит чек-ин в боте и видит запись через API (общий контракт, общая БД остаётся только у core).

## Этап 7 — Напоминания

Модуль `notification` (это репо): расписания (1–3 времени, дни), генерация due-очереди, `/internal/notifications/due` + ack, пользовательский `PUT /reminders/schedule`. Репозиторий бота: пуллинг due и отправка по `telegram_id`.

**Готово, когда:** напоминание приходит в выбранное локальное время; ack не даёт дубликатов в рамках цикла опроса.

## Этап 8 — Hardening

Idempotency-Key для `POST /entries`; rate limiting (Redis); RS256 + JWKS вместо HS256; экспорт данных (CSV/JSON); метрики/трейсинг (Micrometer + OTLP); продовые dockerfile'ы; нагрузочная проверка аналитических запросов (материализованные представления при необходимости).

## За кадром (отдельные репозитории/треки)

- **Фронтенд** — отдельный репозиторий; контракт — публикуемая OpenAPI-спека (см. [spec-first-workflow.md](spec-first-workflow.md)).
- **Telegram-бот** — отдельный репозиторий `aura-telegram-bot`; проектирование и инфраструктура — [telegram-bot.md](telegram-bot.md).
- **Распил на микросервисы** — по стратегии из [architecture-overview.md](architecture-overview.md), только при реальной необходимости.

# aura-backend

Backend сервиса **Aura** — трекера эмоций: запись эмоций и их силы, факторов и жизненных событий, заметки и графики. Два клиента: веб-фронтенд (отдельный репозиторий) и Telegram-бот.

- Модульный монолит (Spring Boot 4 / Kotlin), готовый к распилу на микросервисы
- Веб-фронтенд и Telegram-бот — отдельные репозитории; контракт — OpenAPI-спека этого API
- OpenAPI spec-first, JWT, Liquibase, PostgreSQL + Redis

## Документация (`docs/`)

| Документ | О чём |
|---|---|
| [architecture-overview.md](docs/architecture-overview.md) | общий обзор: подход, модули, события, стратегия распила |
| [domain-model.md](docs/domain-model.md) | сущности, ERD, жизненный цикл записи, таймзоны, пресеты |
| [api-design.md](docs/api-design.md) | конвенции REST, эндпоинты, ошибки, internal-API |
| [spec-first-workflow.md](docs/spec-first-workflow.md) | OpenAPI-спека и генерация кода |
| [gradle-structure.md](docs/gradle-structure.md) | multi-module, `libs.versions.toml`, convention-плагины |
| [liquibase-migrations.md](docs/liquibase-migrations.md) | организация миграций по модулям |
| [telegram-bot.md](docs/telegram-bot.md) | бот — отдельный репозиторий: архитектура, инфраструктура, связка аккаунтов, напоминания |
| [local-dev.md](docs/local-dev.md) | docker-compose, запуск, команды |
| [roadmap.md](docs/roadmap.md) | этапы реализации |

# Локальная разработка

## Требования

- JDK 21 (Temurin)
- Docker + Docker Compose (инфраструктура и Testcontainers)
- Gradle wrapper (`./gradlew`) — отдельная установка Gradle не нужна

## Инфраструктура (docker-compose)

`docker-compose.yml` в корне — **только инфраструктура**, приложения запускаются Gradle'ом:

```yaml
services:
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: aura
      POSTGRES_USER: aura
      POSTGRES_PASSWORD: aura
    ports: ["5432:5432"]
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./docker/init-db.sql:/docker-entrypoint-initdb.d/init-db.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U aura -d aura"]
      interval: 5s
      retries: 10

  redis:
    image: redis:8-alpine
    ports: ["6379:6379"]
    volumes:
      - redisdata:/data

volumes:
  pgdata:
  redisdata:
```

`docker/init-db.sql` — создаёт схемы ( Liquibase создаёт таблицы):

```sql
CREATE SCHEMA IF NOT EXISTS liquibase;
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS catalog;
CREATE SCHEMA IF NOT EXISTS entry;
CREATE SCHEMA IF NOT EXISTS note;
CREATE SCHEMA IF NOT EXISTS notification;
```

Запуск: `docker compose up -d`.

## Запуск приложений

```bash
# 1. инфраструктура
docker compose up -d

# 2. REST API (профиль local) → http://localhost:8080
./gradlew :apps:rest-api:bootRun

# 3. (опционально) Telegram-бот — отдельный репозиторий (aura-telegram-bot):
#    клонируйте его и запустите по его README, указав CORE_BASE_URL=http://localhost:8080
```

> Профиль `local` активен **по умолчанию** (`spring.profiles.default` в `application.yml`) — запуск из IDE или `java -jar` работает без дополнительной настройки. Прод-окружение обязано задавать `SPRING_PROFILES_ACTIVE` явно.

> На этапе каркаса security настроен минимально: `/actuator/health` открыт, всё остальное отвечает `401` (JWT-аутентификация — этап 1, см. [roadmap.md](roadmap.md)).

- Swagger UI: <http://localhost:8080/docs/index.html> (корень `/` — редирект на него; спека — <http://localhost:8080/openapi/api.yaml>).
- Порты: `8080` — API, `5432` — Postgres, `6379` — Redis (`8081` — actuator бота из его репозитория).

## Переменные окружения (rest-api, профиль local)

| Переменная | По умолчанию |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/aura` |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | `aura` / `aura` |
| `JWT_SECRET` | ≥ 32 символов, для dev — любое значение из `.env` |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` |
| `CORE_CLIENT_ID` / `CORE_CLIENT_SECRET` | креды бота, инициализируются seed-чейнджлогом |

## Тесты и качество

```bash
./gradlew test              # все тесты; интеграционные используют Testcontainers → нужен запущенный Docker
./gradlew detekt            # статический анализ
./gradlew koverHtmlReport   # покрытие
```

## Полезное

- Полный сброс БД: `docker compose down -v && docker compose up -d` (Liquibase накатит всё с нуля, включая seed).
- Проверка webhook'а бота без публичного сервера: `ngrok http 8081` в репозитории бота (в проде webhook ходит на ingress бота; в dev достаточно long polling).
- Настройки remind-пула бота для локальной отладки: интервал опроса уменьшается конфигом без правок кода.

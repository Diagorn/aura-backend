# Локальная разработка

## Требования

- JDK 21 (Temurin)
- Docker + Docker Compose (инфраструктура и Testcontainers)
- Gradle wrapper (`./gradlew`) — отдельная установка Gradle не нужна

Gradle-демон пиннут на JDK 21 через `gradle/gradle-daemon-jvm.properties` (Daemon JVM criteria):
если JDK 21 не установлена, Gradle скачает её автоматически (foojay-resolver подключён в `settings.gradle.kts`).
Это позволяет запускать сборку с любой JVM-лаунчером — detekt и остальные задачи всегда идут на 21.

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

> Аутентификация — JWT (HS256): `/api/v1/auth/register|login|refresh|logout` открыты, всё остальное под `/api/v1/**` требует `Authorization: Bearer <accessToken>`, внутренние маршруты `/internal/v1/**` — сервисный JWT (`typ=service`). Сваггер-ручки — см. `/docs/index.html` (спека — `/openapi/api.yaml`).

- Swagger UI: <http://localhost:8080/docs/index.html> (корень `/` — редирект на него; спека — <http://localhost:8080/openapi/api.yaml>).
- Порты: `8080` — API, `5432` — Postgres, `6379` — Redis (`8081` — actuator бота из его репозитория).

## Переменные окружения (rest-api, профиль local)

| Переменная | По умолчанию |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/aura` |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | `aura` / `aura` |
| `JWT_SECRET` | в local — dev-значение из `application-local.yml` (≥ 32 байт); в проде задаётся всегда |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` |

## Сервисные креды Telegram-бота (`/internal`)

Инициализируются seed-чейнджлогом (`auth-900-seed-service-clients`, context `seed`):

- `CORE_CLIENT_ID` = `aura-telegram-bot`
- `CORE_CLIENT_SECRET` = `aura-bot-dev-secret` (в БД — только SHA-256 хэш)

Обмен на сервисный JWT: `POST /internal/v1/auth/service-token {clientId, clientSecret}`. Для прода креды переносятся в выделенный (не seed) чейнджлог окружения с секретом из хранилища секретов.

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

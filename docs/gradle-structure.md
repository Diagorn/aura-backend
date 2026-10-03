# Gradle: multi-module структура

## Дерево проекта

```
aura-backend/
├── settings.gradle.kts               # include всех модулей
├── build.gradle.kts                  # корневой: алиасы плагинов, kover-агрегация
├── gradle/
│   └── libs.versions.toml            # ЕДИНСТВЕННОЕ место версий
├── build-logic/                      # included build: convention-плагины
│   └── src/main/kotlin/
│       ├── aura.kotlin.gradle.kts
│       ├── aura.kotlin-spring.gradle.kts
│       ├── aura.detekt.gradle.kts
│       ├── aura.kover.gradle.kts
│       ├── aura.spring.module.gradle.kts
│       ├── aura.spring.app.gradle.kts
│       └── aura.openapi.gradle.kts
├── api/openapi/                      # спека (см. spec-first-workflow.md)
├── modules/
│   ├── shared/
│   ├── auth/
│   ├── user/
│   ├── catalog/
│   ├── entry/
│   ├── note/
│   ├── analytics/
│   └── notification/
└── apps/
    └── rest-api/                     # единственное приложение репозитория (монолит)
```

> Фронтенд и Telegram-бот — отдельные репозитории: оба потребляют публикуемую OpenAPI-спеку (см. [spec-first-workflow.md](spec-first-workflow.md), [telegram-bot.md](telegram-bot.md)).

## `gradle/libs.versions.toml`

Версии указываются **только** здесь; в билд-файлах модулей — алиасы. Значения ниже — ориентир; финальные пины ставим при инициализации каркаса.

```toml
[versions]
kotlin = "2.2.x"            # актуальную stable проверить при настройке
spring-boot = "4.x"
spring-dependency-management = "1.1.x"
spring-modulith = "2.x"
detekt = "1.23.x"
kover = "0.9.x"
liquibase = "4.x"
openapi-generator = "7.x"
openapi-processor = "2025.x"
telegrambots = "9.x"
testcontainers = "1.20.x"
archunit = "1.3.x"
springdoc = "3.x"           # совместимость с Boot 4 проверить

[libraries]
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }

spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web" }
spring-boot-starter-security = { module = "org.springframework.boot:spring-boot-starter-security" }
spring-boot-starter-data-jpa = { module = "org.springframework.boot:spring-boot-starter-data-jpa" }
spring-boot-starter-validation = { module = "org.springframework.boot:spring-boot-starter-validation" }
spring-boot-starter-actuator = { module = "org.springframework.boot:spring-boot-starter-actuator" }
spring-boot-starter-data-redis = { module = "org.springframework.boot:spring-boot-starter-data-redis" }
spring-boot-oauth2-resource-server = { module = "org.springframework.boot:spring-boot-starter-oauth2-resource-server" }

# Boot 4: использовать модульные стартеры там, где они доступны;
# имена сверять с BOM при инициализации (см. AGENTS.md)
spring-modulith-starter-core = { module = "org.springframework.modulith:spring-modulith-starter-core", version.ref = "spring-modulith" }
spring-modulith-starter-test = { module = "org.springframework.modulith:spring-modulith-starter-test", version.ref = "spring-modulith" }

liquibase-core = { module = "org.liquibase:liquibase-core", version.ref = "liquibase" }
postgresql = { module = "org.postgresql:postgresql" }            # версия из BOM Boot
telegrambots = { module = "org.telegram:telegrambots", version.ref = "telegrambots" }
archunit = { module = "com.tngtech.archunit:archunit-junit5", version.ref = "archunit" }

spring-boot-starter-test = { module = "org.springframework.boot:spring-boot-starter-test" }
spring-security-test = { module = "org.springframework.security:spring-security-test" }
spring-boot-starter-data-jpa-test = { module = "org.springframework.boot:spring-boot-starter-data-jpa-test" }   # dedicated technology test starters (Boot 4)
spring-boot-starter-webmvc-test = { module = "org.springframework.boot:spring-boot-starter-webmvc-test" }
testcontainers-postgresql = { module = "org.testcontainers:postgresql", version.ref = "testcontainers" }
testcontainers-junit-jupiter = { module = "org.testcontainers:junit-jupiter", version.ref = "testcontainers" }

[bundles]
spring-module = ["spring-boot-starter-data-jpa", "spring-boot-starter-validation", "spring-modulith-starter-core"]
spring-app = ["spring-boot-starter-web", "spring-boot-starter-security", "spring-boot-starter-actuator"]
spring-tests = ["spring-boot-starter-test", "spring-modulith-starter-test"]

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-spring = { id = "org.jetbrains.kotlin.plugin.spring", version.ref = "kotlin" }
kotlin-jpa = { id = "org.jetbrains.kotlin.plugin.jpa", version.ref = "kotlin" }
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
spring-dependency-management = { id = "io.spring.dependency-management", version.ref = "spring-dependency-management" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
kover = { id = "org.jetbrains.kotlinx.kover", version.ref = "kover" }
openapi-generator = { id = "org.openapi.generator", version.ref = "openapi-generator" }
# openapi-processor = { id = "com.github.hauner.openapi", version.ref = "openapi-processor" }
```

## Convention-плагины (`build-logic`)

| Плагин | Содержит |
|---|---|
| `aura.kotlin` | `kotlin-jvm`, jvmToolchain(21), настройки тестов JUnit 5 |
| `aura.kotlin-spring` | `kotlin-spring` (all-open для `@Configuration`, `@Transactional` и т.п.) + `kotlin-jpa` (open-энтити) |
| `aura.detekt` | detekt, конфиг `config/detekt.yml`, отчёты; FAIL на warnings в CI |
| `aura.kover` | Kover; корневой модуль агрегирует покрытие |
| `aura.spring.module` | библиотечный модуль домена: `aura.kotlin` + `aura.kotlin-spring` + bundles `spring-module`/`spring-tests` + dedicated test starters |
| `aura.spring.app` | приложение: `spring-boot` + dependency-management + bundles `spring-app` + liquibase + detekt/kover |
| `aura.openapi` | задачи генерации из `api/openapi/api.yaml` (openapi-processor-spring для сервера); выход в sourceSets. Клиент бота генерируется в его репозитории из опубликованного бандла спеки |

Пример билд-файла модуля:

```kotlin
// modules/entry/build.gradle.kts
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    id("aura.spring.module")
}

dependencies {
    implementation(project(":modules:shared"))
    implementation(project(":modules:catalog"))   // только :api-артефакт каталога
    api(project(":modules:entry-api"))            // опц.: выделенный api-модуль
}
```

> Упрощение на старте: если выделенные `*-api` подмодули кажутся избыточными, публичный API держим пакетом `<module>.api` внутри модуля и контролируем ArchUnit-тестом. Переход на выделенные api-модули — механический.

## Правила зависимостей

| От | Разрешено зависеть от |
|---|---|
| `modules:shared` | — (ничего внутреннего) |
| `modules:*` | `modules:shared` и публичные API других модулей; **никогда** от `apps:*` |
| `modules:analytics` | публичный query-порт `modules:entry` |
| `apps:rest-api` | все `modules:*` (только wiring и конфигурация) |

Проверки (тесты в `apps/rest-api`):

- **Spring Modulith verification**: отсутствие циклов, доступ только к `api`-пакетам (`ApplicationModules.of(...).verify()`).
- **ArchUnit**: слои, запрет обращений к `internal`-пакетам чужих модулей, запрет `@Transactional` в контроллерах.

## Команды

```bash
./gradlew build                                  # сборка + тесты + detekt
./gradlew test                                   # все тесты (Testcontainers → нужен Docker)
./gradlew test --tests "com.aura.entry.*"        # тесты одного модуля
./gradlew detekt                                 # статический анализ
./gradlew koverHtmlReport                        # покрытие
./gradlew :apps:rest-api:bootRun                 # запуск API (профиль local)
./gradlew generateApi                            # перегенерация из спеки
```

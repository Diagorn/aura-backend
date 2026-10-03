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

Версии указываются **только** здесь; в билд-файлах модулей — алиасы. Файл `gradle/libs.versions.toml` — источник истины; ниже — снимок на момент инициализации каркаса. Зависимости Spring подключаются через `implementation(platform(libs.spring.boot.bom))` — Gradle platform вместо плагина dependency-management; liquibase, postgresql, testcontainers и spring-security-test версионируются BOM'ом Boot.

```toml
[versions]
kotlin = "2.4.20"
spring-boot = "4.1.1"
spring-modulith = "2.1.1"
detekt = "1.23.8"
kover = "0.9.11"
mockk = "1.14.11"
springmockk = "4.0.2"          # com.ninja-squad:springmockk — @MockkBean для slice-тестов
archunit = "1.5.1"
openapi-generator = "7.14.0"   # генерация сервера (kotlin-spring) и клиентов из api/openapi

[libraries]
spring-boot-bom = { module = "org.springframework.boot:spring-boot-dependencies", version.ref = "spring-boot" }

# Boot 4: модульные стартеры (web → webmvc) + dedicated technology test starters
spring-boot-starter-webmvc = { module = "org.springframework.boot:spring-boot-starter-webmvc" }
spring-boot-starter-security = { module = "org.springframework.boot:spring-boot-starter-security" }
spring-boot-starter-data-jpa = { module = "org.springframework.boot:spring-boot-starter-data-jpa" }
spring-boot-starter-validation = { module = "org.springframework.boot:spring-boot-starter-validation" }
spring-boot-starter-actuator = { module = "org.springframework.boot:spring-boot-starter-actuator" }
spring-boot-starter-data-redis = { module = "org.springframework.boot:spring-boot-starter-data-redis" }

spring-boot-starter-test = { module = "org.springframework.boot:spring-boot-starter-test" }
spring-boot-starter-webmvc-test = { module = "org.springframework.boot:spring-boot-starter-webmvc-test" }
spring-boot-starter-data-jpa-test = { module = "org.springframework.boot:spring-boot-starter-data-jpa-test" }
spring-security-test = { module = "org.springframework.security:spring-security-test" }

spring-modulith-starter-core = { module = "org.springframework.modulith:spring-modulith-starter-core", version.ref = "spring-modulith" }
spring-modulith-starter-test = { module = "org.springframework.modulith:spring-modulith-starter-test", version.ref = "spring-modulith" }

liquibase-core = { module = "org.liquibase:liquibase-core" }   # версия из BOM Boot
postgresql = { module = "org.postgresql:postgresql" }          # версия из BOM Boot

mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
springmockk = { module = "com.ninja-squad:springmockk", version.ref = "springmockk" }

archunit-junit5 = { module = "com.tngtech.archunit:archunit-junit5", version.ref = "archunit" }

[bundles]
spring-module = ["spring-boot-starter-data-jpa", "spring-boot-starter-validation", "spring-modulith-starter-core"]
spring-app = ["spring-boot-starter-webmvc", "spring-boot-starter-security", "spring-boot-starter-actuator"]
spring-tests = ["spring-boot-starter-test", "spring-modulith-starter-test"]
mockk = ["mockk", "springmockk"]

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-spring = { id = "org.jetbrains.kotlin.plugin.spring", version.ref = "kotlin" }
kotlin-jpa = { id = "org.jetbrains.kotlin.plugin.jpa", version.ref = "kotlin" }
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
kover = { id = "org.jetbrains.kotlinx.kover", version.ref = "kover" }
openapi-generator = { id = "org.openapi.generator", version.ref = "openapi-generator" }
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
| `aura.openapi` | — задача `generateApi` объявлена в `apps/rest-api/build.gradle.kts` (openapi-generator, `kotlin-spring`); выход в `build/`, не коммитится. Клиент бота генерируется в его репозитории из опубликованного бандла спеки |

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

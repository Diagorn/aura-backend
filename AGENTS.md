# Spring Boot 4 Project Guidance

## Stack

- Spring Boot 4.x, Spring Framework 7, Kotlin 2.x, JDK 21
- Gradle wrapper: `./gradlew`, Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`)
- Jakarta EE 11, Jackson 3, and Boot 4 modular starters
- Use dedicated technology test starters rather than the classic test starter
- Keep the `kotlin("plugin.spring")` (all-open) compiler plugin applied so `@Configuration`, `@Transactional`, and proxied beans are not final; open JPA entities with `kotlin("plugin.jpa")`

## Commands

- Build: `./gradlew build`
- Unit tests: `./gradlew test`
- Single test: `./gradlew test --tests "com.example.ClassName"`
- Native tests: use the configured native profile; do not invent one

## Engineering Rules

- Inspect neighboring code and the dependency tree before choosing an API or starter.
- Keep controllers as adapters and transactions in application services.
- Use DTOs; do not expose JPA entities as external contracts.
- Use `tools.jackson` APIs for Jackson customization.
- Use `@MockitoBean`/`@MockitoSpyBean` and explicit test auto-configuration.
- Add Flyway migrations for schema changes and never edit applied migrations.
- Preserve error, security, pagination, nullability, and observability contracts.
- Model optionality with Kotlin nullable types; avoid platform types at API and persistence boundaries.

## Skills

Load relevant skills from `.opencode/skills/`. Typical combinations:

- Upgrade: `spring-boot-migration`, `testing-pyramid`
- Endpoint: `rest-api-conventions`, `api-versioning`, `testing-pyramid`
- Persistence: `spring-data-jpa`, `transactional-patterns`, `flyway-migrations`
- Production: `production-observability`, `container-native-deployment`

Skill examples are written for Maven and Java — translate build steps to Gradle Kotlin DSL and code samples to Kotlin idioms.

## Verification

Run focused tests and then `./gradlew build`. For native changes, test the produced native executable.
Report any verification that could not run.

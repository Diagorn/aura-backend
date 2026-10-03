# Liquibase-миграции

> Решение проекта: **Liquibase** (не Flyway). Формат чейнджлогов — YAML.

## Организация

Чейнджлоги принадлежат своим модулям и едут вместе с ними — это часть подготовки к распилу:

```
modules/
├── auth/src/main/resources/db/changelog/auth/
│   ├── changelog.yaml            # includeAll всех changesets модуля (см. ниже)
│   └── changesets/
│       ├── 000-create-schema.yaml
│       ├── 001-create-users.yaml
│       ├── 002-create-refresh-tokens.yaml
│       ├── 003-create-telegram-link-codes.yaml
│       ├── 004-create-service-clients.yaml
│       └── 900-seed-service-clients.yaml   # context: seed
├── catalog/src/main/resources/db/changelog/catalog/
│   ├── changelog.yaml
│   └── changesets/…
├── entry/src/main/resources/db/changelog/entry/...
├── note/src/main/resources/db/changelog/note/...
└── notification/src/main/resources/db/changelog/notification/...

apps/rest-api/src/main/resources/db/changelog/
└── master.yaml                   # включает модульные changelog.yaml в фиксированном порядке
```

`master.yaml` (Liquibase 5 требует явный атрибут `file`; внутри модулей — полные `classpath:`-пути, относительные include через classpath не резолвятся):

```yaml
databaseChangeLog:
  - include:
      file: classpath:db/changelog/auth/changelog.yaml
  - include:
      file: classpath:db/changelog/catalog/changelog.yaml
  - include:
      file: classpath:db/changelog/entry/changelog.yaml
  - include:
      file: classpath:db/changelog/note/changelog.yaml
  - include:
      file: classpath:db/changelog/notification/changelog.yaml
```

Модульный `changelog.yaml` подключает **все файлы подпапки `changesets/`** через `includeAll` — новые changesets подхватываются без правки списка:

```yaml
databaseChangeLog:
  - includeAll:
      path: db/changelog/auth/changesets/
```

Порядок внутри `includeAll` — алфавитный по имени файла, поэтому номер в имени обязателен и кодирует порядок применения (`000-create-schema.yaml`, `001-…`, `900-seed-…`). Сам `changelog.yaml` лежит вне `changesets/`, чтобы не зациклить `includeAll`; зависимости по данным (seed каталога до seed настроек) кодируются номерами файлов.

## Конфигурация (apps/rest-api)

```yaml
spring:
  liquibase:
    change-log: classpath:db/changelog/master.yaml
    liquibase-schema: liquibase     # служебная таблица DATABASECHANGELOG — в схеме liquibase
```

- ⚠️ **Boot 4**: авто-конфигурация Liquibase вынесена в отдельный модуль — нужен `runtimeOnly("org.springframework.boot:spring-boot-liquibase")` помимо `liquibase-core`. Boot BOM версионирует Liquibase 5.x.
- Запуск — при старте приложения (autoconfig). В тестах — Testcontainers + тот же master.
- Схемы `liquibase`, `auth`, `catalog`, `entry`, `note`, `notification` создаёт init-скрипт docker-compose (см. [local-dev.md](local-dev.md)); первый changeset каждого модуля дополнительно делает `CREATE SCHEMA IF NOT EXISTS` (idempotent).

## Пример changeset

```yaml
# modules/entry/src/main/resources/db/changelog/entry/001-create-entries.yaml
databaseChangeLog:
  - changeSet:
      id: entry-001-create-entries
      author: a.ivanov   # всегда текущий git-пользователь (git config user.name)
      preConditions:
        - onFail: MARK_RAN
        - not:
            - tableExists:
                schemaName: entry
                tableName: entries
      changes:
        - createTable:
            schemaName: entry
            tableName: entries
            columns:
              - column: { name: id, type: bigint, autoIncrement: true, constraints: { primaryKey: true } }
              - column: { name: user_id, type: bigint, constraints: { nullable: false } }
              - column: { name: entry_date, type: date, constraints: { nullable: false } }
              - column: { name: recorded_at, type: timestamptz, constraints: { nullable: false } }
              - column: { name: source, type: varchar(16), constraints: { nullable: false } }
              - column: { name: status, type: varchar(16), constraints: { nullable: false } }
              - column: { name: note, type: text }
              - column: { name: created_at, type: timestamptz, constraints: { nullable: false } }
              - column: { name: updated_at, type: timestamptz, constraints: { nullable: false } }
        - createIndex:
            schemaName: entry
            indexName: idx_entries_user_date
            tableName: entries
            columns:
              - column: { name: user_id }
              - column: { name: entry_date }
      rollback:
        - dropTable: { schemaName: entry, tableName: entries }
```

## Правила

1. **Применённый changeset никогда не редактируется.** Любое изменение схемы — новый changeset.
2. Идентификатор: `<module>-<NNN>-<slug>` (`entry-004-add-entry-status-index`). Author — **всегда текущий git-пользователь** (`git config user.name`); заглушки вида `aura` или `dev` не допускаются.
3. Схемы модулей изолированы: FK — только внутри своей схемы. Между модулями — ссылки по id без FK.
4. Seed-данные (системные пресеты, см. [domain-model.md](domain-model.md)) — отдельные changesets с `context: seed`, чтобы можно было запускать без сидов (`spring.liquibase.contexts=seed` управляется профилем).
5. Для DDL описывать `rollback`, где это не бесплатно (`createTable` умеет сам).
6. `preConditions` — `MARK_RAN` для идемпотентных вещей (создание схемы), в остальных случаях — без условий: чистые ошибки лучше тихих пропусков.
7. Типы: `timestamptz` (UTC), `date` для локальных дат, `bigint ... generated by default as identity` для id, snake_case в БД / camelCase в DTO.
8. Индексы — в том же changeset, что и таблица; отдельные changesets — только для постфактум-оптимизаций.

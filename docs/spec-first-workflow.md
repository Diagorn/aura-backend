# Spec-first OpenAPI

> Спека — единственный источник истины о контракте для всех трёх сторон: сервера, веб-фронта и Telegram-бота.

## Структура спеки

```
api/openapi/
├── api.yaml                  # корень: info, servers, tags, security
├── paths/
│   ├── auth.yaml
│   ├── me.yaml
│   ├── catalog.yaml          # emotions, factors, events, metrics
│   ├── entries.yaml
│   ├── notes.yaml
│   ├── analytics.yaml
│   └── internal.yaml         # только для бота, tag: internal
└── components/
    ├── securitySchemes.yaml
    ├── parameters.yaml       # page, size, sort, from, to, period...
    ├── responses.yaml        # Problem, Paginated*
    └── schemas/
        ├── common.yaml       # PageMetadata, Problem fields
        ├── auth.yaml
        ├── catalog.yaml
        ├── entry.yaml
        ├── note.yaml
        └── analytics.yaml
```

Правила:

- `$ref` — относительные пути; инлайновые схемы длиннее ~5 строк не допускаются.
- Каждый эндпоинт имеет тег; internal-эндпоинты — тег `internal` (исключаются из публичной документации).
- Спека валидируется `redocly lint` (конфиг в `api/redocly.yaml`) в CI.

## Генерация

| Что | Инструмент | Результат |
|---|---|---|
| Сервер | **openapi-processor-spring** | интерфейсы контроллеров + DTO (Kotlin) в `build/generated/` |
| Клиент бота (в его репозитории) | **openapi-generator**, `library=spring-http-interface` | генерируется из **опубликованного** бандла спеки; версия пинится в репозитории бота |
| Фронтенд | любой генератор на стороне фронта | потребляет тот же `api.yaml` |

- Задачи генерации объявлены в convention-плагине `aura.openapi` (см. [gradle-structure.md](gradle-structure.md)); выход добавлен в source sets, **в git не коммитится**.
- Контроллеры модулей реализуют сгенерированные интерфейсы — компиляция «показывает» незакрытые контракты.
- ⚠️ На этапе каркаса проверить совместимость версий генераторов с Boot 4 / Jackson 3 (openapi-processor-spring 2025.x+, openapi-generator 7.x+). Fallback: openapi-generator (spring generator) для сервера и клиента.

## Процесс работы

1. Изменение контракта начинается со спеки (PR со спекой — отдельно или первым коммитом).
2. `./gradlew generateApi` — перегенерация.
3. Реализация/правка контроллеров и DTO-использований в модулях; сборка падает, пока контракт не закрыт.
4. CI: `redocly lint` → генерация → тесты. Сгенерированный код в PR не попадает.

## Документация

- Dev: Swagger UI на rest-api (`/swagger-ui.html`, только профиль `local`).
- Публикация: CI-задача бандлит спеку (`redocly bundle`) и публикует артефакт (GitHub Release/артефакт) + Redoc-страницу. Потребители — фронтенд и репозиторий Telegram-бота: оба пинят версию спеки и генерируют свой код у себя.

## Эволюция контракта

- См. раздел «Версионирование» в [api-design.md](api-design.md): аддитивность внутри `v1`, `Deprecation`/`Sunset` перед удалением, breaking — через `v2`.
- Изменение спеки без изменения кода = пустой PR — такие ловим ревью (спека и реализация едут вместе).

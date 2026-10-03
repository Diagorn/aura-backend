// Общие типы: идентификаторы, базовые ошибки, утилиты дат/таймзон.
// Не зависит ни от каких модулей и Spring (см. docs/architecture-overview.md).
// Исключение — compileOnly-аннотация Modulith для package-info: помечает модуль
// открытым (shared kernel), в рантайме класс предоставляется приложением.
plugins {
    id("aura.kotlin")
    id("aura.kover")
}

dependencies {
    compileOnly(libs.spring.modulith.core)
}

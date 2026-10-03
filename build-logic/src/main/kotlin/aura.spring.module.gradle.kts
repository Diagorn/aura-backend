// Библиотечный модуль домена. Зависимости (BOM + бандлы) подключаются в самом модуле
// через алиасы каталога libs — см. docs/gradle-structure.md.
plugins {
    id("aura.kotlin-spring")
    id("aura.kover")
}

dependencies {
    // Spring Data + Kotlin: интроспекция сущностей требует kotlin-reflect в runtime.
    // Версию даёт platform(libs.spring.boot.bom), подключённый в модуле
    // (precompiled plugin не имеет доступа к version catalog — см. junit-platform-launcher ниже).
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // Gradle 9: JUnit Platform launcher обязан быть на test runtime classpath явно;
    // версию даёт platform(libs.spring.boot.bom), подключённый в модуле.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

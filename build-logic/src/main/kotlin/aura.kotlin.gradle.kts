// Базовая конфигурация Kotlin-модуля: toolchain 21, JUnit 5, detekt.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("aura.detekt")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

plugins {
    `kotlin-dsl`
}

// Marker-артефакты плагинов: обязательны для применения плагинов по id
// внутри precompiled script plugins (см. docs/gradle-structure.md).
dependencies {
    implementation("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:${libs.versions.kotlin.get()}")
    implementation("org.jetbrains.kotlin.plugin.spring:org.jetbrains.kotlin.plugin.spring.gradle.plugin:${libs.versions.kotlin.get()}")
    implementation("org.jetbrains.kotlin.plugin.jpa:org.jetbrains.kotlin.plugin.jpa.gradle.plugin:${libs.versions.kotlin.get()}")
    implementation("org.springframework.boot:org.springframework.boot.gradle.plugin:${libs.versions.spring.boot.get()}")
    implementation("io.gitlab.arturbosch.detekt:io.gitlab.arturbosch.detekt.gradle.plugin:${libs.versions.detekt.get()}")
    implementation("org.jetbrains.kotlinx.kover:org.jetbrains.kotlinx.kover.gradle.plugin:${libs.versions.kover.get()}")
}

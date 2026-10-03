package com.aura

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional

/**
 * Архитектурные правила (см. docs/gradle-structure.md «Проверки»).
 * Базовые границы модулей дополнительно проверяет [ModulithVerificationTest].
 */
class ArchitectureTest {

    private val classes = ClassFileImporter().importPackages("com.aura")

    @Test
    fun `web layer must not open transactions`() {
        noClasses()
            .that().resideInAPackage("com.aura.web..")
            .should().beAnnotatedWith(Transactional::class.java)
            .because("транзакции живут в application-сервисах модулей")
            .check(classes)
    }

    @Test
    fun `auth internals are not reachable from outside auth`() {
        noClasses()
            .that().resideOutsideOfPackage("com.aura.auth..")
            .should().dependOnClassesThat().resideInAPackage("com.aura.auth.internal..")
            .because("модуль публикует только пакет com.aura.auth.api")
            .check(classes)
    }

    @Test
    fun `user internals are not reachable from outside user`() {
        noClasses()
            .that().resideOutsideOfPackage("com.aura.user..")
            .should().dependOnClassesThat().resideInAPackage("com.aura.user.internal..")
            .because("модуль публикует только пакет com.aura.user.api")
            .check(classes)
    }

    @Test
    fun `modules must not depend on app layers`() {
        noClasses()
            .that().resideInAnyPackage("com.aura.auth..", "com.aura.user..", "com.aura.shared..")
            .should().dependOnClassesThat().resideInAnyPackage("com.aura.web..", "com.aura.security..")
            .because("зависимость направлена только от приложения к модулям")
            .check(classes)
    }
}

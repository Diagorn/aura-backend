package com.aura

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

/**
 * Границы модулей по Spring Modulith: отсутствие циклов, доступ извне
 * только к публичным контрактам (`*.api` / открытые модули shared и сгенерированного api).
 */
class ModulithVerificationTest {

    @Test
    fun `module structure is valid`() {
        ApplicationModules.of(AuraApplication::class.java).verify()
    }
}

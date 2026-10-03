package com.aura.auth.internal.config

import com.aura.auth.internal.security.JwtProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

/** Конфигурация модуля auth: параметры JWT и кодирование паролей. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties::class)
class AuthConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(BCRYPT_STRENGTH)

    companion object {
        private const val BCRYPT_STRENGTH = 12
    }
}

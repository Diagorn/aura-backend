package com.aura.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint

/**
 * Промежуточная конфигурация каркаса (этап 0):
 * - пробы (/actuator/health) и документация (/docs, /openapi, /webjars) — без аутентификации;
 * - всё остальное требует аутентификации и отвечает чистым 401
 *   (RFC 9457-контракт ошибок появится вместе с auth в этапе 1);
 * - сессий нет — API остаётся stateless.
 *
 * Реальная аутентификация (JWT access/refresh) — этап 1, см. docs/roadmap.md.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            sessionManagement {
                sessionCreationPolicy = SessionCreationPolicy.STATELESS
            }
            authorizeHttpRequests {
                authorize("/", permitAll)
                authorize("/error", permitAll)
                authorize("/docs/**", permitAll)
                authorize("/openapi/**", permitAll)
                authorize("/webjars/**", permitAll)
                authorize("/actuator/health", permitAll)
                authorize("/actuator/health/**", permitAll)
                authorize(anyRequest, authenticated)
            }
            httpBasic { }
            exceptionHandling {
                authenticationEntryPoint = HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
            }
        }
        return http.build()
    }

    /** Пустой сервис пользователей — убирает сгенерированный Boot'ом пароль из логов. */
    @Bean
    fun userDetailsService(): UserDetailsService = InMemoryUserDetailsManager()
}

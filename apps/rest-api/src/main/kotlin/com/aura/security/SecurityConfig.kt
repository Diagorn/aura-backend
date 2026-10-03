package com.aura.security

import com.aura.auth.api.AccessTokenVerifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import tools.jackson.databind.ObjectMapper

/**
 * Stateless security на JWT (этап 1, см. docs/roadmap.md):
 * - аутентификация — [JwtAuthenticationFilter] + verifier модуля auth;
 * - публичные: регистрация/вход/refresh/logout, выдача сервисного токена бота, документация, health;
 * - маршруты /api/v1 — пользовательские токены (роли USER/ADMIN), сервисный токен сюда не пускают;
 * - маршруты /internal — только сервисный токен (scope=internal), никогда не публичны;
 * - всё остальное — denyAll;
 * - 401/403 отдаются в формате RFC 9457 problem+json.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        accessTokenVerifier: AccessTokenVerifier,
        objectMapper: ObjectMapper,
    ): SecurityFilterChain {
        val problemWriter = ProblemWriter(objectMapper)
        http {
            csrf { disable() }
            sessionManagement {
                sessionCreationPolicy = SessionCreationPolicy.STATELESS
            }
            authorizeHttpRequests {
                authorize("/api/v1/auth/register", permitAll)
                authorize("/api/v1/auth/login", permitAll)
                authorize("/api/v1/auth/refresh", permitAll)
                authorize("/api/v1/auth/logout", permitAll)
                authorize("/api/v1/**", hasAnyRole("USER", "ADMIN"))
                authorize("/internal/v1/auth/service-token", permitAll)
                authorize("/internal/**", hasAuthority("SCOPE_internal"))
                authorize("/", permitAll)
                authorize("/error", permitAll)
                authorize("/docs/**", permitAll)
                authorize("/openapi/**", permitAll)
                authorize("/webjars/**", permitAll)
                authorize("/actuator/health", permitAll)
                authorize("/actuator/health/**", permitAll)
                authorize(anyRequest, denyAll)
            }
            addFilterBefore<UsernamePasswordAuthenticationFilter>(
                JwtAuthenticationFilter(accessTokenVerifier, problemWriter),
            )
            exceptionHandling {
                authenticationEntryPoint = ProblemAuthenticationEntryPoint(problemWriter)
                accessDeniedHandler = ProblemAccessDeniedHandler(problemWriter)
            }
        }
        return http.build()
    }

    /** Пустой сервис пользователей — убирает сгенерированный Boot'ом пароль из логов. */
    @Bean
    fun userDetailsService(): UserDetailsService = InMemoryUserDetailsManager()
}

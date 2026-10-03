package com.aura.security

import com.aura.auth.api.TokenPrincipal
import com.aura.auth.api.TokenType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.support.WithSecurityContext
import org.springframework.security.test.context.support.WithSecurityContextFactory
import java.lang.annotation.Inherited

/**
 * Аутентификация для slice-тестов: кладёт в контекст [TokenPrincipal] —
 * тот же тип principal, что создаёт JwtAuthenticationFilter в проде.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
@WithSecurityContext(factory = WithMockAuraUserFactory::class)
annotation class WithMockAuraUser(
    val userId: Long = 1L,
    val roles: Array<String> = ["USER"],
    val scopes: Array<String> = [],
)

class WithMockAuraUserFactory : WithSecurityContextFactory<WithMockAuraUser> {

    override fun createSecurityContext(annotation: WithMockAuraUser): SecurityContext {
        val principal = TokenPrincipal(
            type = TokenType.ACCESS,
            userId = annotation.userId,
            roles = annotation.roles.toSet(),
            scopes = annotation.scopes.toSet(),
        )
        val authorities = annotation.roles.map { SimpleGrantedAuthority("ROLE_$it") } +
            annotation.scopes.map { SimpleGrantedAuthority("SCOPE_$it") }
        val authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities)
        return SecurityContextHolder.createEmptyContext().apply { this.authentication = authentication }
    }
}

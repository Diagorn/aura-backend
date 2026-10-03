package com.aura.security

import com.aura.auth.api.AccessTokenVerifier
import com.aura.auth.api.TokenPrincipal
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Bearer-аутентификация: предъявленный JWT проверяется через [AccessTokenVerifier]
 * (модуль auth) и превращается в Authentication с ролями ROLE_* и scope-авторитетами.
 *
 * Нет заголовка — запрос идёт дальше анонимным (решение принимает авторизация).
 * Есть, но недействителен — сразу 401 problem+json, даже на публичных эндпоинтах.
 */
class JwtAuthenticationFilter(
    private val verifier: AccessTokenVerifier,
    private val problemWriter: ProblemWriter,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header.isNullOrBlank()) {
            filterChain.doFilter(request, response)
            return
        }
        val token = header.removePrefix(BEARER_PREFIX).trim()
        if (!header.startsWith(BEARER_PREFIX) || token.isBlank()) {
            problemWriter.write(request, response, 401, CODE_INVALID_TOKEN, DETAIL_INVALID_TOKEN)
            return
        }
        val principal = verifier.verify(token)
        if (principal == null) {
            problemWriter.write(request, response, 401, CODE_INVALID_TOKEN, DETAIL_INVALID_TOKEN)
            return
        }
        val authentication = UsernamePasswordAuthenticationToken.authenticated(
            principal,
            null,
            principal.toAuthorities(),
        ).apply { details = WebAuthenticationDetailsSource().buildDetails(request) }
        SecurityContextHolder.getContext().authentication = authentication
        filterChain.doFilter(request, response)
    }

    private fun TokenPrincipal.toAuthorities(): List<SimpleGrantedAuthority> =
        roles.map { SimpleGrantedAuthority("ROLE_$it") } + scopes.map { SimpleGrantedAuthority("SCOPE_$it") }

    companion object {
        const val BEARER_PREFIX = "Bearer "
        const val CODE_INVALID_TOKEN = "INVALID_TOKEN"
        const val DETAIL_INVALID_TOKEN = "Токен доступа недействителен или истёк"
    }
}

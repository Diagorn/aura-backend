package com.aura.security

import com.aura.auth.api.TokenPrincipal
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler

/** 401 для защищённых маршрутов без аутентификации — problem+json (code=UNAUTHORIZED). */
class ProblemAuthenticationEntryPoint(
    private val problemWriter: ProblemWriter,
) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        problemWriter.write(request, response, 401, "UNAUTHORIZED", "Требуется аутентификация")
    }
}

/** 403 — аутентифицирован, но прав нет (например, пользовательский токен на внутреннем маршруте) — problem+json. */
class ProblemAccessDeniedHandler(
    private val problemWriter: ProblemWriter,
) : AccessDeniedHandler {

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        problemWriter.write(request, response, 403, "FORBIDDEN", "Недостаточно прав для этого запроса")
    }
}

/** Id пользователя из [TokenPrincipal], положенного в контекст JwtAuthenticationFilter'ом. */
object CurrentUser {

    fun requireUserId(): Long {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: error("Аутентификация отсутствует — маршрут обязан быть защищён")
        val principal = authentication.principal
        if (principal !is TokenPrincipal) {
            error("Неожиданный principal: ${principal?.javaClass} — используйте @WithMockAuraUser в тестах")
        }
        return principal.userId ?: error("Сервисный токен не имеет userId — /api/v1 недоступен для него")
    }
}

package com.aura.auth.api

/**
 * Проверка предъявленного Bearer-токена для security-фильтра приложения.
 * Подтверждает подпись и срок; refresh-токены аутентификацией не считаются.
 */
interface AccessTokenVerifier {

    /** null — токен недействителен (подпись, срок, тип). */
    fun verify(token: String): TokenPrincipal?
}

enum class TokenType {
    /** Пользовательский access-токен: доступ к маршрутам /api/v1 по ролям. */
    ACCESS,

    /** Сервисный токен доверенного клиента (бот): доступ к внутренним маршрутам по scope. */
    SERVICE,
}

/** Утверждения проверенного токена. */
data class TokenPrincipal(
    val type: TokenType,
    /** Id пользователя; не null только для ACCESS. */
    val userId: Long?,
    /** Роли пользователя (USER, ADMIN) — для ACCESS. */
    val roles: Set<String>,
    /** Scope'ы (internal) — для SERVICE. */
    val scopes: Set<String>,
)

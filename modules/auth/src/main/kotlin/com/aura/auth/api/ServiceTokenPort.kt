package com.aura.auth.api

/**
 * Выдача сервисных JWT для доверенных клиентов (Telegram-бот) — доступ к внутренним маршрутам.
 * Креды клиента инициализируются seed-чейнджлогом (см. docs/local-dev.md).
 */
interface ServiceTokenPort {

    /** Неверные креды — [InvalidClientCredentialsException]. */
    fun issue(credentials: ClientCredentials): ServiceTokenIssued
}

data class ClientCredentials(
    val clientId: String,
    val clientSecret: String,
)

data class ServiceTokenIssued(
    val accessToken: String,
    val expiresIn: Long,
)

package com.aura.auth.api

/**
 * Публичные модели модуля auth (см. docs/architecture-overview.md §4):
 * только факты и команды, без внутренностей (сущности, репозитории, JWT-детали).
 */

/** Публичный взгляд на аккаунт: то, что отдают register/login и профиль. */
data class AccountView(
    val id: Long,
    val email: String,
    val timezone: String,
    val locale: String,
    val telegram: TelegramAccountView?,
)

/** Привязанный Telegram-аккаунт; userId — chat_id личного чата. */
data class TelegramAccountView(
    val userId: String,
    val username: String?,
)

/** Результат аутентификации: аккаунт + пара токенов. */
data class AuthSession(
    val user: AccountView,
    val accessToken: String,
    val refreshToken: String,
)

/** Команда регистрации; timezone опциональна (по умолчанию Europe/Moscow, UTC+3). */
data class RegisterCommand(
    val email: String,
    val password: String,
    val timezone: String?,
)

/** Команда входа по email+паролю. */
data class LoginCommand(
    val email: String,
    val password: String,
)

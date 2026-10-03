package com.aura.auth.api

import com.aura.shared.ApiException

/** Email уже занят — 409. */
class EmailAlreadyExistsException :
    ApiException(
        status = 409,
        code = "EMAIL_ALREADY_EXISTS",
        message = "Пользователь с таким email уже зарегистрирован",
    )

/** Неверный email или пароль — 401 (существование аккаунта не раскрываем). */
class InvalidCredentialsException :
    ApiException(
        status = 401,
        code = "INVALID_CREDENTIALS",
        message = "Неверный email или пароль",
    )

/** Refresh-токен неизвестен, просрочен или имеет неверный тип — 401. */
class InvalidRefreshTokenException :
    ApiException(
        status = 401,
        code = "INVALID_REFRESH_TOKEN",
        message = "Refresh-токен недействителен или истёк",
    )

/** Попытка повторного использования отозванного refresh-токена: цепочка отозвана — 401. */
class RefreshTokenReuseException :
    ApiException(
        status = 401,
        code = "REFRESH_TOKEN_REUSE",
        message = "Обнаружено повторное использование refresh-токена; все сессии отозваны",
    )

/** Неверные clientId/clientSecret сервисного клиента — 401. */
class InvalidClientCredentialsException :
    ApiException(
        status = 401,
        code = "INVALID_CLIENT_CREDENTIALS",
        message = "Неверный clientId или clientSecret",
    )

/** Код связки не найден или истёк — 404 (существование не раскрываем). */
class TelegramLinkCodeNotFoundException :
    ApiException(
        status = 404,
        code = "TELEGRAM_LINK_CODE_NOT_FOUND",
        message = "Код связки не найден или истёк",
    )

/** Telegram-аккаунт уже связан с профилем — 409. */
class TelegramAlreadyLinkedException :
    ApiException(
        status = 409,
        code = "TELEGRAM_ALREADY_LINKED",
        message = "Telegram-аккаунт уже привязан",
    )

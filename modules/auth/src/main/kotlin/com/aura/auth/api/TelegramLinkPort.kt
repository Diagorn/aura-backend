package com.aura.auth.api

import java.time.Instant

/**
 * Связка аккаунта с Telegram и обмен «telegram → пользовательский access-токен».
 * Вызывается ботом через /internal-эндпоинты приложения.
 */
interface TelegramLinkPort {

    /** Выдаёт одноразовый код связки (10 минут). Аккаунт уже связан — [TelegramAlreadyLinkedException]. */
    fun createLinkCode(userId: Long): LinkCodeIssued

    /**
     * Привязывает Telegram-аккаунт по коду. Код не найден/истёк — [TelegramLinkCodeNotFoundException];
     * Telegram уже связан (этим или другим пользователем) — [TelegramAlreadyLinkedException].
     */
    fun link(command: TelegramLinkCommand)

    /**
     * Обменивает Telegram-аккаунт на краткоживущий пользовательский access-токен (15 минут).
     * Свежий username, если передан, сохраняется в профиле. Не привязан — [NotFoundException].
     */
    fun exchange(command: TelegramExchangeCommand): AccessTokenIssued
}

/** Одноразовый код связки. */
data class LinkCodeIssued(
    val code: String,
    val expiresAt: Instant,
)

/** Команда привязки по коду (сервисный контур бота). */
data class TelegramLinkCommand(
    val code: String,
    val telegramUserId: String,
    val username: String?,
)

/** Команда обмена telegram_id на пользовательский токен. */
data class TelegramExchangeCommand(
    val telegramUserId: String,
    val username: String?,
)

/** Выданный токен и его срок жизни в секундах. */
data class AccessTokenIssued(
    val accessToken: String,
    val expiresIn: Long,
)

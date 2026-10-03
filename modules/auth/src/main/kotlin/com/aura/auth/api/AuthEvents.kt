package com.aura.auth.api

/**
 * Факты модуля auth и порт их доставки.
 *
 * Доставка — синхронный вызов [AuthEventsPort] в транзакции продюсера (без асинхронщины):
 * потребители реализуют порт своим бином (этап 7 — notification) и не должны бросать
 * исключений. При отсутствии реализаций факты никуда не доставляются.
 */

/** Пользователь зарегистрирован. */
data class UserRegistered(
    val userId: Long,
)

/** К профилю привязан Telegram-аккаунт (или обновлён его username при exchange). */
data class TelegramAccountLinked(
    val userId: Long,
    val telegramUserId: String,
)

/** Порт фактов auth; реализуется модулями-потребителями (см. docs/architecture-overview.md §5). */
interface AuthEventsPort {

    fun userRegistered(event: UserRegistered)

    fun telegramAccountLinked(event: TelegramAccountLinked)
}

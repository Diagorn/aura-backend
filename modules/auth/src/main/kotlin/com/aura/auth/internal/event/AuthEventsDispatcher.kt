package com.aura.auth.internal.event

import com.aura.auth.api.AuthEventsPort
import com.aura.auth.api.TelegramAccountLinked
import com.aura.auth.api.UserRegistered
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component

/**
 * Доставка фактов auth потребителям — вместо ApplicationEventPublisher:
 * синхронный вызов методов всех реализаций [AuthEventsPort] в транзакции продюсера.
 *
 * Реализаций сегодня нет — вызовы no-op; на этапе 7 notification добавит свой бин,
 * и факты начнут доставляться без изменений в auth.
 */
@Component
class AuthEventsDispatcher(
    ports: ObjectProvider<AuthEventsPort>,
) {

    private val listeners: List<AuthEventsPort> = ports.orderedStream().toList()

    fun userRegistered(event: UserRegistered) {
        listeners.forEach { it.userRegistered(event) }
    }

    fun telegramAccountLinked(event: TelegramAccountLinked) {
        listeners.forEach { it.telegramAccountLinked(event) }
    }
}

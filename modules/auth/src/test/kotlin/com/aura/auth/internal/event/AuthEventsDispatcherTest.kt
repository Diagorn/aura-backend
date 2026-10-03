package com.aura.auth.internal.event

import com.aura.auth.api.AuthEventsPort
import com.aura.auth.api.TelegramAccountLinked
import com.aura.auth.api.UserRegistered
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.util.stream.Stream

/** Диспетчер доставляет факт всем реализациям порта; без реализаций — no-op. */
class AuthEventsDispatcherTest {

    @Test
    fun `delivers facts to every port`() {
        val first = mockk<AuthEventsPort>(relaxUnitFun = true)
        val second = mockk<AuthEventsPort>(relaxUnitFun = true)
        val ports = mockk<ObjectProvider<AuthEventsPort>> {
            every { orderedStream() } returns Stream.of(first, second)
        }
        val dispatcher = AuthEventsDispatcher(ports)

        dispatcher.userRegistered(UserRegistered(userId = 7))
        dispatcher.telegramAccountLinked(TelegramAccountLinked(userId = 7, telegramUserId = "900168"))

        verify(exactly = 1) { first.userRegistered(UserRegistered(userId = 7)) }
        verify(exactly = 1) { second.userRegistered(UserRegistered(userId = 7)) }
        verify(exactly = 1) { first.telegramAccountLinked(TelegramAccountLinked(userId = 7, telegramUserId = "900168")) }
        verify(exactly = 1) { second.telegramAccountLinked(TelegramAccountLinked(userId = 7, telegramUserId = "900168")) }
    }

    @Test
    fun `without consumers is a no-op`() {
        val ports = mockk<ObjectProvider<AuthEventsPort>> {
            every { orderedStream() } returns Stream.empty()
        }
        val dispatcher = AuthEventsDispatcher(ports)

        // не бросает и не требует реализаций порта
        dispatcher.userRegistered(UserRegistered(userId = 1))
        dispatcher.telegramAccountLinked(TelegramAccountLinked(userId = 1, telegramUserId = "1"))
    }
}

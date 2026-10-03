package com.aura.user.internal

import com.aura.auth.api.AccountView
import com.aura.auth.api.TelegramAccountView
import com.aura.auth.api.UpdateProfileCommand as AuthUpdate
import com.aura.auth.api.UserAccountPort
import com.aura.user.api.UpdateProfileCommand
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Маппинг данных аккаунта auth в публичный контракт профиля user. */
class UserProfileAdapterTest {

    private val accounts: UserAccountPort = mockk()
    private val adapter = UserProfileAdapter(accounts)

    @Test
    fun `get maps account view to profile including telegram`() {
        every { accounts.getProfile(7) } returns AccountView(
            id = 7,
            email = "anna@example.com",
            timezone = "Europe/Moscow",
            locale = "ru",
            telegram = TelegramAccountView(userId = "900168", username = "anna"),
        )

        val profile = adapter.get(7)

        assertThat(profile.id).isEqualTo(7)
        assertThat(profile.email).isEqualTo("anna@example.com")
        assertThat(profile.timezone).isEqualTo("Europe/Moscow")
        assertThat(profile.telegram?.userId).isEqualTo("900168")
        assertThat(profile.telegram?.username).isEqualTo("anna")
    }

    @Test
    fun `get without linked telegram maps to null`() {
        every { accounts.getProfile(1) } returns AccountView(1, "a@b.ru", "UTC", "ru", telegram = null)

        assertThat(adapter.get(1).telegram).isNull()
    }

    @Test
    fun `update delegates to account port preserving nullable fields`() {
        every { accounts.updateProfile(7, any()) } returns AccountView(7, "a@b.ru", "UTC", "en", null)

        adapter.update(7, UpdateProfileCommand(timezone = null, locale = "en"))

        verify(exactly = 1) {
            accounts.updateProfile(7, AuthUpdate(timezone = null, locale = "en"))
        }
    }

    @Test
    fun `change password delegates to account port`() {
        every { accounts.changePassword(7, any()) } returns Unit

        adapter.changePassword(7, com.aura.user.api.ChangePasswordCommand("old", "new"))

        verify(exactly = 1) {
            accounts.changePassword(7, com.aura.auth.api.ChangePasswordCommand("old", "new"))
        }
    }
}

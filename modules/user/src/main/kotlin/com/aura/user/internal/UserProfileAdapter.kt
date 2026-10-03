package com.aura.user.internal

import com.aura.auth.api.AccountView
import com.aura.auth.api.UpdateProfileCommand as AuthUpdateProfileCommand
import com.aura.auth.api.ChangePasswordCommand as AuthChangePasswordCommand
import com.aura.auth.api.UserAccountPort
import com.aura.user.api.ChangePasswordCommand
import com.aura.user.api.TelegramAccount
import com.aura.user.api.UpdateProfileCommand
import com.aura.user.api.UserProfile
import com.aura.user.api.UserProfilePort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Реализация контракта профиля поверх данных аккаунта модуля auth.
 * user не имеет собственного хранилища: профиль — часть схемы auth,
 * а этот модуль — его публичное «лицо» для /me и остальных модулей.
 */
@Service
@Transactional
class UserProfileAdapter(
    private val accounts: UserAccountPort,
) : UserProfilePort {

    @Transactional(readOnly = true)
    override fun get(userId: Long): UserProfile = accounts.getProfile(userId).toProfile()

    override fun update(userId: Long, command: UpdateProfileCommand): UserProfile =
        accounts.updateProfile(
            userId,
            AuthUpdateProfileCommand(timezone = command.timezone, locale = command.locale),
        ).toProfile()

    override fun changePassword(userId: Long, command: ChangePasswordCommand) {
        accounts.changePassword(
            userId,
            AuthChangePasswordCommand(
                currentPassword = command.currentPassword,
                newPassword = command.newPassword,
            ),
        )
    }

    private fun AccountView.toProfile(): UserProfile = UserProfile(
        id = id,
        email = email,
        timezone = timezone,
        locale = locale,
        telegram = telegram?.let { TelegramAccount(userId = it.userId, username = it.username) },
    )
}

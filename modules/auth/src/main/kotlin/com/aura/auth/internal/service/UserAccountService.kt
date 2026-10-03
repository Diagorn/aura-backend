package com.aura.auth.internal.service

import com.aura.auth.api.AccountView
import com.aura.auth.api.ChangePasswordCommand
import com.aura.auth.api.UpdateProfileCommand
import com.aura.auth.api.UserAccountPort
import com.aura.auth.internal.mapping.toAccountView
import com.aura.auth.internal.repository.UserRepository
import com.aura.auth.internal.validation.ProfileValidation.requireValidLocale
import com.aura.auth.internal.validation.ProfileValidation.requireValidTimezone
import com.aura.shared.ValidationFailedException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Чтение и изменение данных аккаунта для других модулей (user -> /me). */
@Service
@Transactional
class UserAccountService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) : UserAccountPort {

    @Transactional(readOnly = true)
    override fun getProfile(userId: Long): AccountView = userRepository.getUserById(userId).toAccountView()

    override fun updateProfile(userId: Long, command: UpdateProfileCommand): AccountView {
        val timezone = command.timezone?.let { requireValidTimezone(it) }
        val locale = command.locale?.let { requireValidLocale(it) }
        val user = userRepository.getUserById(userId)
        user.updateProfile(timezone, locale)
        return user.toAccountView()
    }

    override fun changePassword(userId: Long, command: ChangePasswordCommand) {
        val user = userRepository.getUserById(userId)
        if (!passwordEncoder.matches(command.currentPassword, user.passwordHash)) {
            throw ValidationFailedException("currentPassword", "Неверный текущий пароль")
        }
        user.changePassword(
            requireNotNull(passwordEncoder.encode(command.newPassword)) { "BCrypt вернул null" },
        )
    }
}

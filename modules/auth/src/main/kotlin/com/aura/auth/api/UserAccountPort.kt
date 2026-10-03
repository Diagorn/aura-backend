package com.aura.auth.api

/**
 * Порт данных аккаунта для других модулей (например, `user` для /me).
 * Владелец данных — схема auth; изменение профиля идёт только через этот контракт.
 */
interface UserAccountPort {

    /** Текущее состояние аккаунта. */
    fun getProfile(userId: Long): AccountView

    /** Обновляет только переданные поля (timezone/locale); возвращает актуальный профиль. */
    fun updateProfile(userId: Long, command: UpdateProfileCommand): AccountView

    /** Меняет пароль, проверяя текущий. Неверный текущий — [ValidationFailedException]. */
    fun changePassword(userId: Long, command: ChangePasswordCommand)
}

/** Частичное обновление профиля: null — поле не менять. */
data class UpdateProfileCommand(
    val timezone: String?,
    val locale: String?,
)

/** Смена пароля. */
data class ChangePasswordCommand(
    val currentPassword: String,
    val newPassword: String,
)

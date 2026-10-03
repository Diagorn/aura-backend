package com.aura.user.api

/**
 * Публичный контракт профиля пользователя: чтение и обновление настроек.
 * Другие модули (например, entry для entry_date по таймзоне) зависят от этого
 * контракта, а не от внутренних данных auth.
 */
interface UserProfilePort {

    /** Профиль пользователя; нет такого пользователя — [com.aura.shared.NotFoundException]. */
    fun get(userId: Long): UserProfile

    /** Обновляет только переданные поля (timezone/locale); возвращает актуальный профиль. */
    fun update(userId: Long, command: UpdateProfileCommand): UserProfile

    /** Меняет пароль, проверяя текущий; неверный текущий — [com.aura.shared.ValidationFailedException]. */
    fun changePassword(userId: Long, command: ChangePasswordCommand)
}

/** Профиль, который видит владелец (GET /api/v1/me) и другие модули. */
data class UserProfile(
    val id: Long,
    val email: String,
    val timezone: String,
    val locale: String,
    val telegram: TelegramAccount?,
)

/** Привязанный Telegram-аккаунт; userId — chat_id личного чата. */
data class TelegramAccount(
    val userId: String,
    val username: String?,
)

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

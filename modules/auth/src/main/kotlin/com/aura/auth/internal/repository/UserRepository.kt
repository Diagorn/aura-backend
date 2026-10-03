package com.aura.auth.internal.repository

import com.aura.auth.internal.entity.UserEntity
import com.aura.shared.NotFoundException
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<UserEntity, Long> {

    fun findByEmail(email: String): UserEntity?

    fun findByTelegramId(telegramId: String): UserEntity?

    /**
     * Аккаунт обязателен; отсутствие — 404 NOT_FOUND
     * (чужие ресурсы не раскрываем: detail общий, без упоминания email).
     */
    fun getUserById(userId: Long): UserEntity =
        findById(userId).orElseThrow { NotFoundException("Пользователь не найден") }

    /** Пользователь по привязанному Telegram-аккаунту; нет — 404 NOT_FOUND. */
    fun getByTelegramId(telegramId: String): UserEntity =
        findByTelegramId(telegramId) ?: throw NotFoundException("Telegram-аккаунт не привязан к профилю")
}

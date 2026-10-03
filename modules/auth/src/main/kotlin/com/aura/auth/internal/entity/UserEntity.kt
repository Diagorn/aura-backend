package com.aura.auth.internal.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

/** Роль пользователя (см. docs/architecture-overview.md §7). */
enum class UserRole {
    USER,
    ADMIN,
}

/**
 * Аккаунт пользователя — таблица auth.users.
 * Владелец таблицы — модуль auth; другие модули читают/пишут только через auth.api.
 * Инварианты меняются только методами (linkTelegram/changePassword/...), не сеттерами.
 */
@Entity
@Table(name = "users", schema = "auth")
class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false, unique = true, length = 255)
    var email: String = "",
    /** BCrypt-хэш пароля. */
    @Column(name = "password_hash", nullable = false, length = 255)
    var passwordHash: String = "",
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val role: UserRole = UserRole.USER,
    /** null до связки; уникален; = chat_id личного чата. */
    @Column(name = "telegram_id", length = 64)
    var telegramId: String? = null,
    /** Обновляется ботом при link/exchange. */
    @Column(name = "telegram_username", length = 255)
    var telegramUsername: String? = null,
    /** IANA-таймзона профиля. */
    @Column(nullable = false, length = 64)
    var timezone: String = "",
    @Column(nullable = false, length = 16)
    var locale: String = "",
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {

    /** Обновляет только переданные поля профиля. */
    fun updateProfile(timezone: String?, locale: String?) {
        if (timezone != null) this.timezone = timezone
        if (locale != null) this.locale = locale
    }

    /** Привязывает Telegram-аккаунт. */
    fun linkTelegram(telegramUserId: String, username: String?) {
        require(telegramId == null) { "Telegram-аккаунт уже привязан" }
        this.telegramId = telegramUserId
        this.telegramUsername = username
    }

    /** Обновляет username Telegram-аккаунта (бот передаёт свежий при exchange). */
    fun updateTelegramUsername(username: String?) {
        check(telegramId != null) { "Telegram-аккаунт не привязан" }
        this.telegramUsername = username
    }

    /** Меняет хэш пароля. */
    fun changePassword(newPasswordHash: String) {
        this.passwordHash = newPasswordHash
    }

    companion object {

        /** Фабрика регистрации: email нормализуется в нижний регистр. */
        fun register(
            email: String,
            passwordHash: String,
            timezone: String,
            locale: String,
        ): UserEntity = UserEntity(
            email = email.lowercase(),
            passwordHash = passwordHash,
            timezone = timezone,
            locale = locale,
        )
    }
}

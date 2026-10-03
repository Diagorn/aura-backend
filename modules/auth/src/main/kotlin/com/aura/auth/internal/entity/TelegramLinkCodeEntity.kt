package com.aura.auth.internal.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

/**
 * Одноразовый код связки аккаунта с Telegram — таблица auth.telegram_link_codes.
 * Живёт 10 минут; при привязке удаляется (одноразовость), при выдаче нового — старые удаляются.
 */
@Entity
@Table(name = "telegram_link_codes", schema = "auth")
class TelegramLinkCodeEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,
    @Column(nullable = false, unique = true, length = 64)
    val code: String,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {

    val isExpired: Boolean
        get() = expiresAt.isBefore(Instant.now())
}

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
 * Refresh-токен — таблица auth.refresh_tokens.
 * В БД хранится только SHA-256 хэш предъявленного JWT; ротация — через revoked_at.
 * Повторное использование отозванного токена отзывает все активные токены пользователя.
 */
@Entity
@Table(name = "refresh_tokens", schema = "auth")
class RefreshTokenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,
    /** SHA-256 hex предъявленного JWT — уникален. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    val tokenHash: String,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
    /** Момент отзыва (ротация/выход/отзыв цепочки); null — токен активен. */
    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {

    val isActive: Boolean
        get() = revokedAt == null

    /** Отзывает токен, если он ещё активен. */
    fun revoke(at: Instant) {
        if (revokedAt == null) {
            revokedAt = at
        }
    }
}

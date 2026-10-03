package com.aura.auth.internal.repository

import com.aura.auth.api.InvalidRefreshTokenException
import com.aura.auth.internal.entity.RefreshTokenEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, Long> {

    /**
     * Блокировка строки на время ротации: два параллельных refresh с одним токеном
     * не должны оба пройти проверку «активен».
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByTokenHash(tokenHash: String): RefreshTokenEntity?

    /** Токен обязателен; неизвестен — 401 INVALID_REFRESH_TOKEN. */
    fun getByTokenHash(tokenHash: String): RefreshTokenEntity =
        findByTokenHash(tokenHash) ?: throw InvalidRefreshTokenException()

    /** Отзыв всех активных токенов пользователя (reuse detection / отзыв цепочки). */
    @Modifying
    @Query(
        "update RefreshTokenEntity t set t.revokedAt = :now " +
            "where t.user.id = :userId and t.revokedAt is null",
    )
    fun revokeAllActiveForUser(@Param("userId") userId: Long, @Param("now") now: Instant): Int
}

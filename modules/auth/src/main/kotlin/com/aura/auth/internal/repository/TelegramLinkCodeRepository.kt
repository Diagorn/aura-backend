package com.aura.auth.internal.repository

import com.aura.auth.internal.entity.TelegramLinkCodeEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.repository.query.Param

interface TelegramLinkCodeRepository : JpaRepository<TelegramLinkCodeEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByCode(@Param("code") code: String): TelegramLinkCodeEntity?

    fun deleteAllByUserId(userId: Long)
}

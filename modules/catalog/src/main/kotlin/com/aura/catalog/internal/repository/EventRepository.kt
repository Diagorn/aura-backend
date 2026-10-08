package com.aura.catalog.internal.repository

import com.aura.catalog.internal.entity.EventEntity
import com.aura.shared.NotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface EventRepository : JpaRepository<EventEntity, Long> {

    /** Все события пользователя; условие по системным оставлено для симметрии с остальными справочниками. */
    @Query(
        """
        select e from EventEntity e
        where e.ownerUserId = :userId or (e.ownerUserId is null and e.isActive = true)
        order by e.sortOrder asc, e.id asc
        """,
    )
    fun findAllForUser(@Param("userId") userId: Long): List<EventEntity>

    fun existsByOwnerUserIdAndName(ownerUserId: Long, name: String): Boolean

    fun existsByOwnerUserIdIsNullAndName(name: String): Boolean

    fun findFirstByOwnerUserIdOrderBySortOrderDesc(ownerUserId: Long): EventEntity?

    /** Забирает элемент по id или бросает [NotFoundException] (fetch-or-throw — в репозитории). */
    fun requireById(id: Long): EventEntity = findById(id).orElseThrow { NotFoundException("Событие не найдено") }
}

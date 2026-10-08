package com.aura.catalog.internal.repository

import com.aura.catalog.internal.entity.TrackedMetricEntity
import com.aura.shared.NotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TrackedMetricRepository : JpaRepository<TrackedMetricEntity, Long> {

    /** Все элементы, релевантные пользователю: свои (любые) и активные системные. Порядок — sortOrder, id. */
    @Query(
        """
        select m from TrackedMetricEntity m
        where m.ownerUserId = :userId or (m.ownerUserId is null and m.isActive = true)
        order by m.sortOrder asc, m.id asc
        """,
    )
    fun findAllForUser(@Param("userId") userId: Long): List<TrackedMetricEntity>

    fun existsByOwnerUserIdAndName(ownerUserId: Long, name: String): Boolean

    fun existsByOwnerUserIdIsNullAndName(name: String): Boolean

    fun findFirstByOwnerUserIdOrderBySortOrderDesc(ownerUserId: Long): TrackedMetricEntity?

    /** Забирает элемент по id или бросает [NotFoundException] (fetch-or-throw — в репозитории). */
    fun requireById(id: Long): TrackedMetricEntity =
        findById(id).orElseThrow { NotFoundException("Метрика не найдена") }
}

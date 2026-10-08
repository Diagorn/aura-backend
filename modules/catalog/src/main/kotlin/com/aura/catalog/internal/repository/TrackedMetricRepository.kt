package com.aura.catalog.internal.repository

import com.aura.catalog.internal.entity.TrackedMetricEntity
import com.aura.shared.NotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TrackedMetricRepository : JpaRepository<TrackedMetricEntity, Long> {

    /** Видимые пользователю метрики: системные и свои, неактивные — только с includeInactive. */
    @Query(
        """
        select m from TrackedMetricEntity m
        where (m.ownerUserId = :userId or m.ownerUserId is null)
          and (:includeInactive = true or m.isActive = true)
        order by m.sortOrder asc, m.id asc
        """,
    )
    fun findVisible(@Param("userId") userId: Long, @Param("includeInactive") includeInactive: Boolean): List<TrackedMetricEntity>

    fun existsByOwnerUserIdAndName(ownerUserId: Long, name: String): Boolean

    fun findFirstByOwnerUserIdOrderBySortOrderDesc(ownerUserId: Long): TrackedMetricEntity?

    fun findByIdAndOwnerUserId(id: Long, ownerUserId: Long): TrackedMetricEntity?

    /**
     * Забирает элемент пользователя или бросает [NotFoundException]:
     * чужой и несуществующий не различаются — существование чужого не раскрываем.
     * Поиск с throw живёт в репозитории, сервисы получают готовую сущность.
     */
    fun requireOwnedBy(id: Long, ownerUserId: Long): TrackedMetricEntity =
        findByIdAndOwnerUserId(id, ownerUserId) ?: throw NotFoundException("Метрика не найдена")
}

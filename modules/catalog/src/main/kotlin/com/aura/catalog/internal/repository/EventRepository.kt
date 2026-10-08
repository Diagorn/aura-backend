package com.aura.catalog.internal.repository

import com.aura.catalog.internal.entity.EventEntity
import com.aura.shared.NotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface EventRepository : JpaRepository<EventEntity, Long> {

    /** События пользователя; условие по системным оставлено для симметрии с остальными справочниками. */
    @Query(
        """
        select e from EventEntity e
        where (e.ownerUserId = :userId or e.ownerUserId is null)
          and (:includeInactive = true or e.isActive = true)
        order by e.sortOrder asc, e.id asc
        """,
    )
    fun findVisible(@Param("userId") userId: Long, @Param("includeInactive") includeInactive: Boolean): List<EventEntity>

    fun existsByOwnerUserIdAndName(ownerUserId: Long, name: String): Boolean

    fun findFirstByOwnerUserIdOrderBySortOrderDesc(ownerUserId: Long): EventEntity?

    fun findByIdAndOwnerUserId(id: Long, ownerUserId: Long): EventEntity?

    /**
     * Забирает элемент пользователя или бросает [NotFoundException]:
     * чужой и несуществующий не различаются — существование чужого не раскрываем.
     * Поиск с throw живёт в репозитории, сервисы получают готовую сущность.
     */
    fun requireOwnedBy(id: Long, ownerUserId: Long): EventEntity =
        findByIdAndOwnerUserId(id, ownerUserId) ?: throw NotFoundException("Событие не найдено")
}

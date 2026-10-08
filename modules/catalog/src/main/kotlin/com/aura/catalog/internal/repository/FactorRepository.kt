package com.aura.catalog.internal.repository

import com.aura.catalog.internal.entity.FactorEntity
import com.aura.shared.NotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FactorRepository : JpaRepository<FactorEntity, Long> {

    /** Видимые пользователю факторы: системные и свои, неактивные — только с includeInactive. */
    @Query(
        """
        select f from FactorEntity f
        where (f.ownerUserId = :userId or f.ownerUserId is null)
          and (:includeInactive = true or f.isActive = true)
        order by f.sortOrder asc, f.id asc
        """,
    )
    fun findVisible(@Param("userId") userId: Long, @Param("includeInactive") includeInactive: Boolean): List<FactorEntity>

    fun existsByOwnerUserIdAndName(ownerUserId: Long, name: String): Boolean

    fun findFirstByOwnerUserIdOrderBySortOrderDesc(ownerUserId: Long): FactorEntity?

    fun findByIdAndOwnerUserId(id: Long, ownerUserId: Long): FactorEntity?

    /**
     * Забирает элемент пользователя или бросает [NotFoundException]:
     * чужой и несуществующий не различаются — существование чужого не раскрываем.
     * Поиск с throw живёт в репозитории, сервисы получают готовую сущность.
     */
    fun requireOwnedBy(id: Long, ownerUserId: Long): FactorEntity =
        findByIdAndOwnerUserId(id, ownerUserId) ?: throw NotFoundException("Фактор не найден")
}

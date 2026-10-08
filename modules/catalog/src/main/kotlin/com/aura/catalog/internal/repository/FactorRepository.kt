package com.aura.catalog.internal.repository

import com.aura.catalog.internal.entity.FactorEntity
import com.aura.shared.NotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FactorRepository : JpaRepository<FactorEntity, Long> {

    /** Все элементы, релевантные пользователю: свои (любые) и активные системные. Порядок — sortOrder, id. */
    @Query(
        """
        select f from FactorEntity f
        where f.ownerUserId = :userId or (f.ownerUserId is null and f.isActive = true)
        order by f.sortOrder asc, f.id asc
        """,
    )
    fun findAllForUser(@Param("userId") userId: Long): List<FactorEntity>

    fun existsByOwnerUserIdAndName(ownerUserId: Long, name: String): Boolean

    fun existsByOwnerUserIdIsNullAndName(name: String): Boolean

    fun findFirstByOwnerUserIdOrderBySortOrderDesc(ownerUserId: Long): FactorEntity?

    /** Забирает элемент по id или бросает [NotFoundException] (fetch-or-throw — в репозитории). */
    fun requireById(id: Long): FactorEntity = findById(id).orElseThrow { NotFoundException("Фактор не найден") }
}

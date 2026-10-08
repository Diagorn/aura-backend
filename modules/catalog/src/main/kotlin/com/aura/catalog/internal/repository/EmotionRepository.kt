package com.aura.catalog.internal.repository

import com.aura.catalog.internal.entity.EmotionEntity
import com.aura.shared.NotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface EmotionRepository : JpaRepository<EmotionEntity, Long> {

    /**
     * Все элементы, релевантные пользователю: свои (любые, включая деактивированные —
     * фильтр по includeInactive делает сервис с учётом скрытых системных) и активные
     * системные. Порядок — sortOrder, id.
     */
    @Query(
        """
        select e from EmotionEntity e
        where e.ownerUserId = :userId or (e.ownerUserId is null and e.isActive = true)
        order by e.sortOrder asc, e.id asc
        """,
    )
    fun findAllForUser(@Param("userId") userId: Long): List<EmotionEntity>

    /** Имя уникально в рамках пользователя (у разных пользователей может повторяться). */
    fun existsByOwnerUserIdAndName(ownerUserId: Long, name: String): Boolean

    /** Имя уникально в рамках системного набора (для правок системных строк админом). */
    fun existsByOwnerUserIdIsNullAndName(name: String): Boolean

    /** Последний по sortOrder элемент пользователя — для присвоения следующего порядка. */
    fun findFirstByOwnerUserIdOrderBySortOrderDesc(ownerUserId: Long): EmotionEntity?

    /** Элементы для записей по id: только системные и личные данного пользователя. */
    @Query(
        """
        select e from EmotionEntity e
        where e.id in :ids and (e.ownerUserId = :userId or e.ownerUserId is null)
        """,
    )
    fun findVisibleByIds(@Param("userId") userId: Long, @Param("ids") ids: Collection<Long>): List<EmotionEntity>

    /** Забирает элемент по id или бросает [NotFoundException] (fetch-or-throw — в репозитории). */
    fun requireById(id: Long): EmotionEntity = findById(id).orElseThrow { NotFoundException("Эмоция не найдена") }
}

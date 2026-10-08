package com.aura.catalog.internal.repository

import com.aura.catalog.internal.entity.EmotionEntity
import com.aura.shared.NotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface EmotionRepository : JpaRepository<EmotionEntity, Long> {

    /**
     * Видимые пользователю эмоции: системные (ownerUserId == null) и свои,
     * неактивные — только с includeInactive. В этапе 2.1 системных строк ещё нет,
     * запрос сразу готов к этапу 2.2 (seed пресетов).
     */
    @Query(
        """
        select e from EmotionEntity e
        where (e.ownerUserId = :userId or e.ownerUserId is null)
          and (:includeInactive = true or e.isActive = true)
        order by e.sortOrder asc, e.id asc
        """,
    )
    fun findVisible(@Param("userId") userId: Long, @Param("includeInactive") includeInactive: Boolean): List<EmotionEntity>

    /** Имя уникально в рамках пользователя (у разных пользователей может повторяться). */
    fun existsByOwnerUserIdAndName(ownerUserId: Long, name: String): Boolean

    /** Последний по sortOrder элемент пользователя — для присвоения следующего порядка. */
    fun findFirstByOwnerUserIdOrderBySortOrderDesc(ownerUserId: Long): EmotionEntity?

    fun findByIdAndOwnerUserId(id: Long, ownerUserId: Long): EmotionEntity?

    /**
     * Забирает элемент пользователя или бросает [NotFoundException]:
     * чужой и несуществующий не различаются — существование чужого не раскрываем.
     * Поиск с throw живёт в репозитории, сервисы получают готовую сущность.
     */
    fun requireOwnedBy(id: Long, ownerUserId: Long): EmotionEntity =
        findByIdAndOwnerUserId(id, ownerUserId) ?: throw NotFoundException("Эмоция не найдена")
}

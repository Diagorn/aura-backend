package com.aura.catalog.internal.repository

import com.aura.catalog.internal.entity.CatalogItemType
import com.aura.catalog.internal.entity.UserHiddenItemEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserHiddenItemRepository : JpaRepository<UserHiddenItemEntity, Long> {

    /** Скрыт ли пользователем конкретный элемент справочника. */
    fun existsByUserIdAndItemTypeAndItemId(userId: Long, itemType: CatalogItemType, itemId: Long): Boolean

    /** Id системных элементов типа [itemType], скрытых пользователем, — для фильтрации листинга. */
    @Query(
        """
        select h.itemId from UserHiddenItemEntity h
        where h.userId = :userId and h.itemType = :itemType
        """,
    )
    fun findItemIds(@Param("userId") userId: Long, @Param("itemType") itemType: CatalogItemType): List<Long>

    /** Возврат системного элемента в листинг пользователя (идемпотентно: нет записи — ничего не удаляется). */
    fun deleteByUserIdAndItemTypeAndItemId(userId: Long, itemType: CatalogItemType, itemId: Long)
}

package com.aura.catalog.internal.service

import com.aura.catalog.api.CatalogItemNameAlreadyExistsException
import com.aura.catalog.api.CreateFactor
import com.aura.catalog.api.Factor
import com.aura.catalog.api.FactorsPort
import com.aura.catalog.api.SystemItemForbiddenException
import com.aura.catalog.api.UpdateFactor
import com.aura.catalog.internal.entity.CatalogItemType
import com.aura.catalog.internal.entity.FactorEntity
import com.aura.catalog.internal.entity.UserHiddenItemEntity
import com.aura.catalog.internal.mapping.toModel
import com.aura.catalog.internal.repository.FactorRepository
import com.aura.catalog.internal.repository.UserHiddenItemRepository
import com.aura.shared.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * CRUD факторов: листинг «системные + персональные» и правки по ролям —
 * те же правила, что и у эмоций (см. [EmotionService]).
 */
@Service
@Transactional
class FactorService(
    private val factors: FactorRepository,
    private val hiddenItems: UserHiddenItemRepository,
) : FactorsPort {

    @Transactional(readOnly = true)
    override fun list(userId: Long, includeInactive: Boolean): List<Factor> {
        val hidden = hiddenItems.findItemIds(userId, CatalogItemType.FACTOR).toHashSet()
        return factors.findAllForUser(userId)
            .map { it to effectiveActive(it, hidden) }
            .filter { (_, active) -> includeInactive || active }
            .map { (entity, active) -> entity.toModel().copy(isActive = active) }
    }

    override fun create(userId: Long, command: CreateFactor): Factor {
        if (factors.existsByOwnerUserIdAndName(userId, command.name)) {
            throw CatalogItemNameAlreadyExistsException()
        }
        val entity = factors.save(
            FactorEntity(
                ownerUserId = userId,
                name = command.name,
                icon = command.icon,
                isActive = true,
                sortOrder = command.sortOrder ?: nextSortOrder(userId),
            ),
        )
        return entity.toModel()
    }

    override fun update(userId: Long, id: Long, command: UpdateFactor, isAdmin: Boolean): Factor {
        val entity = factors.requireById(id)
        return when {
            entity.ownerUserId == null -> updateSystem(entity, command, isAdmin, userId)
            entity.ownerUserId == userId -> applyUpdate(entity, command) { factors.existsByOwnerUserIdAndName(userId, it) }
            else -> throw NotFoundException("Фактор не найден")
        }
    }

    override fun delete(userId: Long, id: Long) {
        val entity = factors.requireById(id)
        if (entity.ownerUserId == null) {
            throw SystemItemForbiddenException("Системные факторы удалять нельзя — деактивируйте или скройте их")
        }
        if (entity.ownerUserId != userId) {
            throw NotFoundException("Фактор не найден")
        }
        factors.delete(entity)
    }

    @Transactional(readOnly = true)
    override fun findVisibleByIds(userId: Long, ids: Collection<Long>): List<Factor> {
        if (ids.isEmpty()) return emptyList()
        return factors.findVisibleByIds(userId, ids).map { it.toModel() }
    }

    private fun updateSystem(entity: FactorEntity, command: UpdateFactor, isAdmin: Boolean, userId: Long): Factor {
        if (isAdmin) {
            return applyUpdate(entity, command) { factors.existsByOwnerUserIdIsNullAndName(it) }
        }
        val editsBeyondHiding = command.name != null || command.icon != null || command.sortOrder != null
        if (editsBeyondHiding || command.isActive == null) {
            throw SystemItemForbiddenException()
        }
        setHidden(entity.id, userId, hidden = !command.isActive)
        return entity.toModel().copy(isActive = command.isActive)
    }

    private fun setHidden(itemId: Long, userId: Long, hidden: Boolean) {
        if (hidden) {
            if (!hiddenItems.existsByUserIdAndItemTypeAndItemId(userId, CatalogItemType.FACTOR, itemId)) {
                hiddenItems.save(UserHiddenItemEntity(userId = userId, itemType = CatalogItemType.FACTOR, itemId = itemId))
            }
        } else {
            hiddenItems.deleteByUserIdAndItemTypeAndItemId(userId, CatalogItemType.FACTOR, itemId)
        }
    }

    private fun applyUpdate(
        entity: FactorEntity,
        command: UpdateFactor,
        nameTaken: (String) -> Boolean,
    ): Factor {
        command.name?.takeIf { it != entity.name }?.let { name ->
            if (nameTaken(name)) {
                throw CatalogItemNameAlreadyExistsException()
            }
            entity.name = name
        }
        command.icon?.let { entity.icon = it }
        command.isActive?.let { entity.isActive = it }
        command.sortOrder?.let { entity.sortOrder = it }
        return factors.save(entity).toModel()
    }

    private fun effectiveActive(entity: FactorEntity, hidden: Set<Long>): Boolean =
        if (entity.ownerUserId == null) entity.id !in hidden else entity.isActive

    private fun nextSortOrder(userId: Long): Int =
        (factors.findFirstByOwnerUserIdOrderBySortOrderDesc(userId)?.sortOrder ?: -1) + 1
}

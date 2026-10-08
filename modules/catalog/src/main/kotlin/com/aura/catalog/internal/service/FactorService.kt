package com.aura.catalog.internal.service

import com.aura.catalog.api.CatalogItemNameAlreadyExistsException
import com.aura.catalog.api.CreateFactor
import com.aura.catalog.api.Factor
import com.aura.catalog.api.FactorsPort
import com.aura.catalog.api.UpdateFactor
import com.aura.catalog.internal.entity.FactorEntity
import com.aura.catalog.internal.mapping.toModel
import com.aura.catalog.internal.repository.FactorRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * CRUD факторов: список (системные + персональные) и правки только своих элементов.
 * Чужой или несуществующий элемент — 404 (существование чужого не раскрываем);
 * поиск с throw — в репозитории ([FactorRepository.requireOwnedBy]).
 */
@Service
@Transactional
class FactorService(
    private val factors: FactorRepository,
) : FactorsPort {

    @Transactional(readOnly = true)
    override fun list(userId: Long, includeInactive: Boolean): List<Factor> =
        factors.findVisible(userId, includeInactive).map { it.toModel() }

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

    override fun update(userId: Long, id: Long, command: UpdateFactor): Factor {
        val entity = factors.requireOwnedBy(id, userId)
        command.name?.takeIf { it != entity.name }?.let { name ->
            if (factors.existsByOwnerUserIdAndName(userId, name)) {
                throw CatalogItemNameAlreadyExistsException()
            }
            entity.name = name
        }
        command.icon?.let { entity.icon = it }
        command.isActive?.let { entity.isActive = it }
        command.sortOrder?.let { entity.sortOrder = it }
        return factors.save(entity).toModel()
    }

    override fun delete(userId: Long, id: Long) {
        factors.delete(factors.requireOwnedBy(id, userId))
    }

    private fun nextSortOrder(userId: Long): Int =
        (factors.findFirstByOwnerUserIdOrderBySortOrderDesc(userId)?.sortOrder ?: -1) + 1
}

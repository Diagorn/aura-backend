package com.aura.catalog.internal.service

import com.aura.catalog.api.CatalogEvent
import com.aura.catalog.api.CatalogItemNameAlreadyExistsException
import com.aura.catalog.api.CreateCatalogEvent
import com.aura.catalog.api.EventsPort
import com.aura.catalog.api.UpdateCatalogEvent
import com.aura.catalog.internal.entity.EventEntity
import com.aura.catalog.internal.mapping.toModel
import com.aura.catalog.internal.repository.EventRepository
import com.aura.shared.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * CRUD событий. Системных пресетов для событий нет — все события персональны,
 * роль на операции не влияет. Чужое или несуществующее событие — 404
 * (существование чужого не раскрываем); поиск с throw — в репозитории.
 */
@Service
@Transactional
class EventService(
    private val events: EventRepository,
) : EventsPort {

    @Transactional(readOnly = true)
    override fun list(userId: Long, includeInactive: Boolean): List<CatalogEvent> =
        events.findAllForUser(userId)
            .filter { includeInactive || it.isActive }
            .map { it.toModel() }

    override fun create(userId: Long, command: CreateCatalogEvent): CatalogEvent {
        if (events.existsByOwnerUserIdAndName(userId, command.name)) {
            throw CatalogItemNameAlreadyExistsException()
        }
        val entity = events.save(
            EventEntity(
                ownerUserId = userId,
                name = command.name,
                icon = command.icon,
                isActive = true,
                sortOrder = command.sortOrder ?: nextSortOrder(userId),
            ),
        )
        return entity.toModel()
    }

    override fun update(userId: Long, id: Long, command: UpdateCatalogEvent, isAdmin: Boolean): CatalogEvent {
        val entity = events.requireById(id)
        if (entity.ownerUserId != userId) {
            throw NotFoundException("Событие не найдено")
        }
        command.name?.takeIf { it != entity.name }?.let { name ->
            if (events.existsByOwnerUserIdAndName(userId, name)) {
                throw CatalogItemNameAlreadyExistsException()
            }
            entity.name = name
        }
        command.icon?.let { entity.icon = it }
        command.isActive?.let { entity.isActive = it }
        command.sortOrder?.let { entity.sortOrder = it }
        return events.save(entity).toModel()
    }

    override fun delete(userId: Long, id: Long) {
        val entity = events.requireById(id)
        if (entity.ownerUserId != userId) {
            throw NotFoundException("Событие не найдено")
        }
        events.delete(entity)
    }

    @Transactional(readOnly = true)
    override fun findVisibleByIds(userId: Long, ids: Collection<Long>): List<CatalogEvent> {
        if (ids.isEmpty()) return emptyList()
        return events.findVisibleByIds(userId, ids).map { it.toModel() }
    }

    private fun nextSortOrder(userId: Long): Int =
        (events.findFirstByOwnerUserIdOrderBySortOrderDesc(userId)?.sortOrder ?: -1) + 1
}

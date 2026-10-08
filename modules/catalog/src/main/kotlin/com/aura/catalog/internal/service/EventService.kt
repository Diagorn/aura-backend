package com.aura.catalog.internal.service

import com.aura.catalog.api.CatalogEvent
import com.aura.catalog.api.CatalogItemNameAlreadyExistsException
import com.aura.catalog.api.CreateCatalogEvent
import com.aura.catalog.api.EventsPort
import com.aura.catalog.api.UpdateCatalogEvent
import com.aura.catalog.internal.entity.EventEntity
import com.aura.catalog.internal.mapping.toModel
import com.aura.catalog.internal.repository.EventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * CRUD событий. Системных пресетов для событий нет — все события персональны.
 * Чужое или несуществующее событие — 404 (существование чужого не раскрываем);
 * поиск с throw — в репозитории ([EventRepository.requireOwnedBy]).
 */
@Service
@Transactional
class EventService(
    private val events: EventRepository,
) : EventsPort {

    @Transactional(readOnly = true)
    override fun list(userId: Long, includeInactive: Boolean): List<CatalogEvent> =
        events.findVisible(userId, includeInactive).map { it.toModel() }

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

    override fun update(userId: Long, id: Long, command: UpdateCatalogEvent): CatalogEvent {
        val entity = events.requireOwnedBy(id, userId)
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
        events.delete(events.requireOwnedBy(id, userId))
    }

    private fun nextSortOrder(userId: Long): Int =
        (events.findFirstByOwnerUserIdOrderBySortOrderDesc(userId)?.sortOrder ?: -1) + 1
}

package com.aura.catalog.internal.service

import com.aura.catalog.api.CatalogItemNameAlreadyExistsException
import com.aura.catalog.api.CreateEmotion
import com.aura.catalog.api.Emotion
import com.aura.catalog.api.EmotionsPort
import com.aura.catalog.api.UpdateEmotion
import com.aura.catalog.internal.entity.EmotionEntity
import com.aura.catalog.internal.mapping.toModel
import com.aura.catalog.internal.repository.EmotionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * CRUD эмоций: список (системные + персональные) и правки только своих элементов.
 * Чужой или несуществующий элемент — 404 (существование чужого не раскрываем);
 * поиск с throw — в репозитории ([EmotionRepository.requireOwnedBy]).
 */
@Service
@Transactional
class EmotionService(
    private val emotions: EmotionRepository,
) : EmotionsPort {

    @Transactional(readOnly = true)
    override fun list(userId: Long, includeInactive: Boolean): List<Emotion> =
        emotions.findVisible(userId, includeInactive).map { it.toModel() }

    override fun create(userId: Long, command: CreateEmotion): Emotion {
        if (emotions.existsByOwnerUserIdAndName(userId, command.name)) {
            throw CatalogItemNameAlreadyExistsException()
        }
        val entity = emotions.save(
            EmotionEntity(
                ownerUserId = userId,
                name = command.name,
                color = command.color,
                icon = command.icon,
                isActive = true,
                sortOrder = command.sortOrder ?: nextSortOrder(userId),
            ),
        )
        return entity.toModel()
    }

    override fun update(userId: Long, id: Long, command: UpdateEmotion): Emotion {
        val entity = emotions.requireOwnedBy(id, userId)
        command.name?.takeIf { it != entity.name }?.let { name ->
            if (emotions.existsByOwnerUserIdAndName(userId, name)) {
                throw CatalogItemNameAlreadyExistsException()
            }
            entity.name = name
        }
        command.color?.let { entity.color = it }
        command.icon?.let { entity.icon = it }
        command.isActive?.let { entity.isActive = it }
        command.sortOrder?.let { entity.sortOrder = it }
        return emotions.save(entity).toModel()
    }

    override fun delete(userId: Long, id: Long) {
        emotions.delete(emotions.requireOwnedBy(id, userId))
    }

    private fun nextSortOrder(userId: Long): Int =
        (emotions.findFirstByOwnerUserIdOrderBySortOrderDesc(userId)?.sortOrder ?: -1) + 1
}

package com.aura.catalog.internal.service

import com.aura.catalog.api.CatalogItemNameAlreadyExistsException
import com.aura.catalog.api.CreateEmotion
import com.aura.catalog.api.Emotion
import com.aura.catalog.api.EmotionsPort
import com.aura.catalog.api.SystemItemForbiddenException
import com.aura.catalog.api.UpdateEmotion
import com.aura.catalog.internal.entity.CatalogItemType
import com.aura.catalog.internal.entity.EmotionEntity
import com.aura.catalog.internal.entity.UserHiddenItemEntity
import com.aura.catalog.internal.mapping.toModel
import com.aura.catalog.internal.repository.EmotionRepository
import com.aura.catalog.internal.repository.UserHiddenItemRepository
import com.aura.shared.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * CRUD эмоций: листинг «системные + персональные» и правки по ролям.
 * Системную эмоцию ADMIN правит в строке; обычный пользователь может только скрыть её
 * для себя ({isActive:false} -> user_hidden_items) или вернуть; другие поля — 403.
 * Системные не удаляются никем; чужой или несуществующий элемент — 404.
 */
@Service
@Transactional
class EmotionService(
    private val emotions: EmotionRepository,
    private val hiddenItems: UserHiddenItemRepository,
) : EmotionsPort {

    @Transactional(readOnly = true)
    override fun list(userId: Long, includeInactive: Boolean): List<Emotion> {
        val hidden = hiddenItems.findItemIds(userId, CatalogItemType.EMOTION).toHashSet()
        return emotions.findAllForUser(userId)
            .map { it to effectiveActive(it, hidden) }
            .filter { (_, active) -> includeInactive || active }
            .map { (entity, active) -> entity.toModel().copy(isActive = active) }
    }

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

    override fun update(userId: Long, id: Long, command: UpdateEmotion, isAdmin: Boolean): Emotion {
        val entity = emotions.requireById(id)
        return when {
            entity.ownerUserId == null -> updateSystem(entity, command, isAdmin, userId)
            entity.ownerUserId == userId -> applyUpdate(entity, command) { emotions.existsByOwnerUserIdAndName(userId, it) }
            else -> throw NotFoundException("Эмоция не найдена")
        }
    }

    override fun delete(userId: Long, id: Long) {
        val entity = emotions.requireById(id)
        if (entity.ownerUserId == null) {
            throw SystemItemForbiddenException("Системные эмоции удалять нельзя — деактивируйте или скройте их")
        }
        if (entity.ownerUserId != userId) {
            throw NotFoundException("Эмоция не найдена")
        }
        emotions.delete(entity)
    }

    /** Системная: админ правит строку; обычный пользователь — только персональное скрытие через {isActive}. */
    private fun updateSystem(entity: EmotionEntity, command: UpdateEmotion, isAdmin: Boolean, userId: Long): Emotion {
        if (isAdmin) {
            return applyUpdate(entity, command) { emotions.existsByOwnerUserIdIsNullAndName(it) }
        }
        val editsBeyondHiding = command.name != null || command.color != null ||
            command.icon != null || command.sortOrder != null
        if (editsBeyondHiding || command.isActive == null) {
            throw SystemItemForbiddenException()
        }
        setHidden(entity.id, userId, hidden = !command.isActive)
        return entity.toModel().copy(isActive = command.isActive)
    }

    /** Скрыть/вернуть системный элемент для пользователя; повторные вызовы идемпотентны. */
    private fun setHidden(itemId: Long, userId: Long, hidden: Boolean) {
        if (hidden) {
            if (!hiddenItems.existsByUserIdAndItemTypeAndItemId(userId, CatalogItemType.EMOTION, itemId)) {
                hiddenItems.save(UserHiddenItemEntity(userId = userId, itemType = CatalogItemType.EMOTION, itemId = itemId))
            }
        } else {
            hiddenItems.deleteByUserIdAndItemTypeAndItemId(userId, CatalogItemType.EMOTION, itemId)
        }
    }

    private fun applyUpdate(
        entity: EmotionEntity,
        command: UpdateEmotion,
        nameTaken: (String) -> Boolean,
    ): Emotion {
        command.name?.takeIf { it != entity.name }?.let { name ->
            if (nameTaken(name)) {
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

    /** Активность элемента глазами пользователя: скрытая системная неактивна. */
    private fun effectiveActive(entity: EmotionEntity, hidden: Set<Long>): Boolean =
        if (entity.ownerUserId == null) entity.id !in hidden else entity.isActive

    private fun nextSortOrder(userId: Long): Int =
        (emotions.findFirstByOwnerUserIdOrderBySortOrderDesc(userId)?.sortOrder ?: -1) + 1
}

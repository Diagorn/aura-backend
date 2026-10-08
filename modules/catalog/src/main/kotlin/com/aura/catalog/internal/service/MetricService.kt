package com.aura.catalog.internal.service

import com.aura.catalog.api.CatalogItemNameAlreadyExistsException
import com.aura.catalog.api.CreateTrackedMetric
import com.aura.catalog.api.InvalidMetricScaleException
import com.aura.catalog.api.MetricsPort
import com.aura.catalog.api.SystemItemForbiddenException
import com.aura.catalog.api.TrackedMetric
import com.aura.catalog.api.UpdateTrackedMetric
import com.aura.catalog.internal.entity.CatalogItemType
import com.aura.catalog.internal.entity.TrackedMetricEntity
import com.aura.catalog.internal.entity.UserHiddenItemEntity
import com.aura.catalog.internal.mapping.toModel
import com.aura.catalog.internal.repository.TrackedMetricRepository
import com.aura.catalog.internal.repository.UserHiddenItemRepository
import com.aura.shared.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * CRUD отслеживаемых метрик: листинг «системные + персональные» и правки по ролям —
 * те же правила, что и у эмоций (см. [EmotionService]). Шкала валидируется по итоговым
 * значениям: если пришла только одна граница, она сравнивается с текущей второй.
 */
@Service
@Transactional
class MetricService(
    private val metrics: TrackedMetricRepository,
    private val hiddenItems: UserHiddenItemRepository,
) : MetricsPort {

    @Transactional(readOnly = true)
    override fun list(userId: Long, includeInactive: Boolean): List<TrackedMetric> {
        val hidden = hiddenItems.findItemIds(userId, CatalogItemType.METRIC).toHashSet()
        return metrics.findAllForUser(userId)
            .map { it to effectiveActive(it, hidden) }
            .filter { (_, active) -> includeInactive || active }
            .map { (entity, active) -> entity.toModel().copy(isActive = active) }
    }

    override fun create(userId: Long, command: CreateTrackedMetric): TrackedMetric {
        if (metrics.existsByOwnerUserIdAndName(userId, command.name)) {
            throw CatalogItemNameAlreadyExistsException()
        }
        val minValue = command.minValue ?: DEFAULT_MIN_VALUE
        val maxValue = command.maxValue ?: DEFAULT_MAX_VALUE
        ensureScale(minValue, maxValue)
        val entity = metrics.save(
            TrackedMetricEntity(
                ownerUserId = userId,
                name = command.name,
                minValue = minValue,
                maxValue = maxValue,
                unit = command.unit,
                isActive = true,
                sortOrder = command.sortOrder ?: nextSortOrder(userId),
            ),
        )
        return entity.toModel()
    }

    override fun update(userId: Long, id: Long, command: UpdateTrackedMetric, isAdmin: Boolean): TrackedMetric {
        val entity = metrics.requireById(id)
        return when {
            entity.ownerUserId == null -> updateSystem(entity, command, isAdmin, userId)
            entity.ownerUserId == userId -> applyUpdate(entity, command) { metrics.existsByOwnerUserIdAndName(userId, it) }
            else -> throw NotFoundException("Метрика не найдена")
        }
    }

    override fun delete(userId: Long, id: Long) {
        val entity = metrics.requireById(id)
        if (entity.ownerUserId == null) {
            throw SystemItemForbiddenException("Системные метрики удалять нельзя — деактивируйте или скройте их")
        }
        if (entity.ownerUserId != userId) {
            throw NotFoundException("Метрика не найдена")
        }
        metrics.delete(entity)
    }

    @Transactional(readOnly = true)
    override fun findVisibleByIds(userId: Long, ids: Collection<Long>): List<TrackedMetric> {
        if (ids.isEmpty()) return emptyList()
        return metrics.findVisibleByIds(userId, ids).map { it.toModel() }
    }

    private fun updateSystem(
        entity: TrackedMetricEntity,
        command: UpdateTrackedMetric,
        isAdmin: Boolean,
        userId: Long,
    ): TrackedMetric {
        if (isAdmin) {
            return applyUpdate(entity, command) { metrics.existsByOwnerUserIdIsNullAndName(it) }
        }
        val editsBeyondHiding = command.name != null || command.minValue != null ||
            command.maxValue != null || command.unit != null || command.sortOrder != null
        if (editsBeyondHiding || command.isActive == null) {
            throw SystemItemForbiddenException()
        }
        setHidden(entity.id, userId, hidden = !command.isActive)
        return entity.toModel().copy(isActive = command.isActive)
    }

    private fun setHidden(itemId: Long, userId: Long, hidden: Boolean) {
        if (hidden) {
            if (!hiddenItems.existsByUserIdAndItemTypeAndItemId(userId, CatalogItemType.METRIC, itemId)) {
                hiddenItems.save(UserHiddenItemEntity(userId = userId, itemType = CatalogItemType.METRIC, itemId = itemId))
            }
        } else {
            hiddenItems.deleteByUserIdAndItemTypeAndItemId(userId, CatalogItemType.METRIC, itemId)
        }
    }

    private fun applyUpdate(
        entity: TrackedMetricEntity,
        command: UpdateTrackedMetric,
        nameTaken: (String) -> Boolean,
    ): TrackedMetric {
        command.name?.takeIf { it != entity.name }?.let { name ->
            if (nameTaken(name)) {
                throw CatalogItemNameAlreadyExistsException()
            }
            entity.name = name
        }
        // Пришла только одна граница — проверяем её вместе с текущей второй
        ensureScale(command.minValue ?: entity.minValue, command.maxValue ?: entity.maxValue)
        command.minValue?.let { entity.minValue = it }
        command.maxValue?.let { entity.maxValue = it }
        command.unit?.let { entity.unit = it }
        command.isActive?.let { entity.isActive = it }
        command.sortOrder?.let { entity.sortOrder = it }
        return metrics.save(entity).toModel()
    }

    private fun ensureScale(minValue: BigDecimal, maxValue: BigDecimal) {
        if (minValue >= maxValue) {
            throw InvalidMetricScaleException()
        }
    }

    private fun effectiveActive(entity: TrackedMetricEntity, hidden: Set<Long>): Boolean =
        if (entity.ownerUserId == null) entity.id !in hidden else entity.isActive

    private fun nextSortOrder(userId: Long): Int =
        (metrics.findFirstByOwnerUserIdOrderBySortOrderDesc(userId)?.sortOrder ?: -1) + 1

    companion object {
        private val DEFAULT_MIN_VALUE: BigDecimal = BigDecimal.ONE
        private val DEFAULT_MAX_VALUE: BigDecimal = BigDecimal("5")
    }
}

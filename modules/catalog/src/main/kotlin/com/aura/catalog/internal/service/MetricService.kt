package com.aura.catalog.internal.service

import com.aura.catalog.api.CatalogItemNameAlreadyExistsException
import com.aura.catalog.api.CreateTrackedMetric
import com.aura.catalog.api.InvalidMetricScaleException
import com.aura.catalog.api.MetricsPort
import com.aura.catalog.api.TrackedMetric
import com.aura.catalog.api.UpdateTrackedMetric
import com.aura.catalog.internal.entity.TrackedMetricEntity
import com.aura.catalog.internal.mapping.toModel
import com.aura.catalog.internal.repository.TrackedMetricRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * CRUD отслеживаемых метрик: список (системные + персональные) и правки только своих элементов.
 * Шкала валидируется по итоговым значениям: если пришла только одна граница,
 * она сравнивается с текущей второй. Чужая или несуществующая метрика — 404;
 * поиск с throw — в репозитории ([TrackedMetricRepository.requireOwnedBy]).
 */
@Service
@Transactional
class MetricService(
    private val metrics: TrackedMetricRepository,
) : MetricsPort {

    @Transactional(readOnly = true)
    override fun list(userId: Long, includeInactive: Boolean): List<TrackedMetric> =
        metrics.findVisible(userId, includeInactive).map { it.toModel() }

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

    override fun update(userId: Long, id: Long, command: UpdateTrackedMetric): TrackedMetric {
        val entity = metrics.requireOwnedBy(id, userId)
        command.name?.takeIf { it != entity.name }?.let { name ->
            if (metrics.existsByOwnerUserIdAndName(userId, name)) {
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

    override fun delete(userId: Long, id: Long) {
        metrics.delete(metrics.requireOwnedBy(id, userId))
    }

    private fun ensureScale(minValue: BigDecimal, maxValue: BigDecimal) {
        if (minValue >= maxValue) {
            throw InvalidMetricScaleException()
        }
    }

    private fun nextSortOrder(userId: Long): Int =
        (metrics.findFirstByOwnerUserIdOrderBySortOrderDesc(userId)?.sortOrder ?: -1) + 1

    companion object {
        private val DEFAULT_MIN_VALUE: BigDecimal = BigDecimal.ONE
        private val DEFAULT_MAX_VALUE: BigDecimal = BigDecimal("5")
    }
}

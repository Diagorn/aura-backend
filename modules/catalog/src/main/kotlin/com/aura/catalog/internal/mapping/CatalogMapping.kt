package com.aura.catalog.internal.mapping

import com.aura.catalog.api.CatalogEvent
import com.aura.catalog.api.Emotion
import com.aura.catalog.api.Factor
import com.aura.catalog.api.TrackedMetric
import com.aura.catalog.internal.entity.EmotionEntity
import com.aura.catalog.internal.entity.EventEntity
import com.aura.catalog.internal.entity.FactorEntity
import com.aura.catalog.internal.entity.TrackedMetricEntity

/** Маппинг сущностей каталога в модели публичного контракта (com.aura.catalog.api). */

internal fun EmotionEntity.toModel(): Emotion = Emotion(
    id = id,
    name = name,
    color = color,
    icon = icon,
    isActive = isActive,
    sortOrder = sortOrder,
    isSystem = ownerUserId == null,
)

internal fun FactorEntity.toModel(): Factor = Factor(
    id = id,
    name = name,
    icon = icon,
    isActive = isActive,
    sortOrder = sortOrder,
    isSystem = ownerUserId == null,
)

internal fun EventEntity.toModel(): CatalogEvent = CatalogEvent(
    id = id,
    name = name,
    icon = icon,
    isActive = isActive,
    sortOrder = sortOrder,
    isSystem = ownerUserId == null,
)

internal fun TrackedMetricEntity.toModel(): TrackedMetric = TrackedMetric(
    id = id,
    name = name,
    minValue = minValue,
    maxValue = maxValue,
    unit = unit,
    isActive = isActive,
    sortOrder = sortOrder,
    isSystem = ownerUserId == null,
)

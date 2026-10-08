package com.aura.web.catalog

import com.aura.api.model.CreateEmotionRequest
import com.aura.api.model.CreateEventRequest
import com.aura.api.model.CreateFactorRequest
import com.aura.api.model.CreateMetricRequest
import com.aura.api.model.EmotionResponse
import com.aura.api.model.EventResponse
import com.aura.api.model.FactorResponse
import com.aura.api.model.MetricResponse
import com.aura.api.model.UpdateEmotionRequest
import com.aura.api.model.UpdateEventRequest
import com.aura.api.model.UpdateFactorRequest
import com.aura.api.model.UpdateMetricRequest
import com.aura.catalog.api.CatalogEvent
import com.aura.catalog.api.CreateCatalogEvent
import com.aura.catalog.api.CreateEmotion
import com.aura.catalog.api.CreateFactor
import com.aura.catalog.api.CreateTrackedMetric
import com.aura.catalog.api.Emotion
import com.aura.catalog.api.Factor
import com.aura.catalog.api.TrackedMetric
import com.aura.catalog.api.UpdateCatalogEvent
import com.aura.catalog.api.UpdateEmotion
import com.aura.catalog.api.UpdateFactor
import com.aura.catalog.api.UpdateTrackedMetric

/** Маппинг справочников в контракт спеки и обратно (CatalogApi). */

internal fun Emotion.toResponse(): EmotionResponse = EmotionResponse(
    id = id,
    name = name,
    isActive = isActive,
    sortOrder = sortOrder,
    system = isSystem,
    color = color,
    icon = icon,
)

internal fun Factor.toResponse(): FactorResponse = FactorResponse(
    id = id,
    name = name,
    isActive = isActive,
    sortOrder = sortOrder,
    system = isSystem,
    icon = icon,
)

internal fun CatalogEvent.toResponse(): EventResponse = EventResponse(
    id = id,
    name = name,
    isActive = isActive,
    sortOrder = sortOrder,
    system = isSystem,
    icon = icon,
)

internal fun TrackedMetric.toResponse(): MetricResponse = MetricResponse(
    id = id,
    name = name,
    minValue = minValue,
    maxValue = maxValue,
    isActive = isActive,
    sortOrder = sortOrder,
    system = isSystem,
    unit = unit,
)

internal fun CreateEmotionRequest.toCommand(): CreateEmotion = CreateEmotion(
    name = name,
    color = color,
    icon = icon,
    sortOrder = sortOrder,
)

internal fun CreateFactorRequest.toCommand(): CreateFactor = CreateFactor(
    name = name,
    icon = icon,
    sortOrder = sortOrder,
)

internal fun CreateEventRequest.toCommand(): CreateCatalogEvent = CreateCatalogEvent(
    name = name,
    icon = icon,
    sortOrder = sortOrder,
)

internal fun CreateMetricRequest.toCommand(): CreateTrackedMetric = CreateTrackedMetric(
    name = name,
    minValue = minValue,
    maxValue = maxValue,
    unit = unit,
    sortOrder = sortOrder,
)

internal fun UpdateEmotionRequest.toCommand(): UpdateEmotion = UpdateEmotion(
    name = name,
    color = color,
    icon = icon,
    isActive = isActive,
    sortOrder = sortOrder,
)

internal fun UpdateFactorRequest.toCommand(): UpdateFactor = UpdateFactor(
    name = name,
    icon = icon,
    isActive = isActive,
    sortOrder = sortOrder,
)

internal fun UpdateEventRequest.toCommand(): UpdateCatalogEvent = UpdateCatalogEvent(
    name = name,
    icon = icon,
    isActive = isActive,
    sortOrder = sortOrder,
)

internal fun UpdateMetricRequest.toCommand(): UpdateTrackedMetric = UpdateTrackedMetric(
    name = name,
    minValue = minValue,
    maxValue = maxValue,
    unit = unit,
    isActive = isActive,
    sortOrder = sortOrder,
)

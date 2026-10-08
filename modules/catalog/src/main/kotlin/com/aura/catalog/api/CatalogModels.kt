package com.aura.catalog.api

import java.math.BigDecimal

/** Эмоция — элемент справочника чек-ина (таблица catalog.emotions). */
data class Emotion(
    val id: Long,
    val name: String,
    /** RGB-цвет для UI; null — не выбран. */
    val color: Int?,
    val icon: String?,
    val isActive: Boolean,
    val sortOrder: Int,
    /** true — системный пресет, общий для всех пользователей. */
    val isSystem: Boolean,
)

/** Фактор — причина/контекст состояния (таблица catalog.factors). */
data class Factor(
    val id: Long,
    val name: String,
    val icon: String?,
    val isActive: Boolean,
    val sortOrder: Int,
    val isSystem: Boolean,
)

/** Событие — конкретное жизненное событие пользователя (таблица catalog.events). */
data class CatalogEvent(
    val id: Long,
    val name: String,
    val icon: String?,
    val isActive: Boolean,
    val sortOrder: Int,
    val isSystem: Boolean,
)

/** Отслеживаемая метрика со шкалой (таблица catalog.tracked_metrics). */
data class TrackedMetric(
    val id: Long,
    val name: String,
    val minValue: BigDecimal,
    val maxValue: BigDecimal,
    val unit: String?,
    val isActive: Boolean,
    val sortOrder: Int,
    val isSystem: Boolean,
)

/** Создание персональной эмоции; sortOrder null — присваивает сервер (в конец списка). */
data class CreateEmotion(
    val name: String,
    val color: Int?,
    val icon: String?,
    val sortOrder: Int?,
)

/** Создание персонального фактора. */
data class CreateFactor(
    val name: String,
    val icon: String?,
    val sortOrder: Int?,
)

/** Создание персонального события. */
data class CreateCatalogEvent(
    val name: String,
    val icon: String?,
    val sortOrder: Int?,
)

/** Создание персональной метрики; границы шкалы null — значения по умолчанию 1 и 5. */
data class CreateTrackedMetric(
    val name: String,
    val minValue: BigDecimal?,
    val maxValue: BigDecimal?,
    val unit: String?,
    val sortOrder: Int?,
)

/** Изменение эмоции: PATCH-семантика — null поле не менять. */
data class UpdateEmotion(
    val name: String?,
    val color: Int?,
    val icon: String?,
    val isActive: Boolean?,
    val sortOrder: Int?,
)

/** Изменение фактора: PATCH-семантика — null поле не менять. */
data class UpdateFactor(
    val name: String?,
    val icon: String?,
    val isActive: Boolean?,
    val sortOrder: Int?,
)

/** Изменение события: PATCH-семантика — null поле не менять. */
data class UpdateCatalogEvent(
    val name: String?,
    val icon: String?,
    val isActive: Boolean?,
    val sortOrder: Int?,
)

/** Изменение метрики: PATCH-семантика — null поле не менять. */
data class UpdateTrackedMetric(
    val name: String?,
    val minValue: BigDecimal?,
    val maxValue: BigDecimal?,
    val unit: String?,
    val isActive: Boolean?,
    val sortOrder: Int?,
)

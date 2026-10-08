package com.aura.catalog.internal.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

/**
 * Отслеживаемая метрика — таблица catalog.tracked_metrics.
 * Паттерн «системное + персональное»: ownerUserId == null — системный пресет,
 * общий для всех пользователей; иначе — персональный элемент владельца.
 */
@Entity
@Table(name = "tracked_metrics", schema = "catalog")
class TrackedMetricEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    /** null — системный пресет; иначе id пользователя-владельца. */
    @Column(name = "owner_user_id")
    val ownerUserId: Long?,
    @Column(nullable = false, length = 100)
    var name: String,
    /** Нижняя граница шкалы (по умолчанию 1). */
    @Column(name = "min_value", nullable = false, precision = 6, scale = 2)
    var minValue: BigDecimal,
    /** Верхняя граница шкалы (по умолчанию 5); должна быть больше minValue. */
    @Column(name = "max_value", nullable = false, precision = 6, scale = 2)
    var maxValue: BigDecimal,
    /** Единица измерения, опционально. */
    @Column(length = 16)
    var unit: String?,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean,
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int,
)

package com.aura.entry.internal.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

/**
 * Значение метрики в записи — таблица entry.entry_metrics.
 * Ссылка на catalog.tracked_metrics — по id, без FK (чужая схема); значение валидируется
 * сервисом по шкале конкретной метрики, поэтому на уровне БД диапазона нет.
 */
@Entity
@Table(name = "entry_metrics", schema = "entry")
class EntryMetricEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "entry_id", nullable = false)
    val entryId: Long,
    @Column(name = "metric_id", nullable = false)
    val metricId: Long,
    @Column(nullable = false)
    val value: BigDecimal,
)

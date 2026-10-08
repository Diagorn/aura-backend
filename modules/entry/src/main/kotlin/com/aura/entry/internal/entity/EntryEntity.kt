package com.aura.entry.internal.entity

import com.aura.entry.api.EntrySource
import com.aura.entry.api.EntryStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

/**
 * Запись чек-ина — таблица entry.entries.
 * entry_date — локальная дата пользователя, вычисляется при создании по его таймзоне
 * и не меняется (правки записи не сдвигают её между днями); recorded_at — момент
 * сохранения, UTC.
 */
@Entity
@Table(name = "entries", schema = "entry")
class EntryEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "entry_date", nullable = false)
    val entryDate: LocalDate,
    @Column(name = "recorded_at", nullable = false)
    val recordedAt: Instant,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var source: EntrySource,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: EntryStatus,
    @Column
    var note: String?,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
)

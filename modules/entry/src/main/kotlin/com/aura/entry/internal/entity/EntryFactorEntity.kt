package com.aura.entry.internal.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Фактор в записи — таблица entry.entry_factors.
 * Ссылка на catalog.factors — по id, без FK (чужая схема); intensity nullable —
 * выраженность фактора отмечать не обязательно.
 */
@Entity
@Table(name = "entry_factors", schema = "entry")
class EntryFactorEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "entry_id", nullable = false)
    val entryId: Long,
    @Column(name = "factor_id", nullable = false)
    val factorId: Long,
    /** Выраженность фактора, 1–5 или null (CHECK в БД). */
    @Column
    val intensity: Int?,
)

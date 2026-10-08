package com.aura.entry.internal.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Эмоция в записи — таблица entry.entry_emotions.
 * Ссылка на catalog.emotions — по id, без FK (чужая схема); одна эмоция в записи — один раз.
 */
@Entity
@Table(name = "entry_emotions", schema = "entry")
class EntryEmotionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "entry_id", nullable = false)
    val entryId: Long,
    @Column(name = "emotion_id", nullable = false)
    val emotionId: Long,
    /** Сила эмоции, 1–5 (CHECK в БД). */
    @Column(nullable = false)
    val intensity: Int,
    /** Субъективное влияние, 1–5 (CHECK в БД). */
    @Column(nullable = false)
    val influence: Int,
)

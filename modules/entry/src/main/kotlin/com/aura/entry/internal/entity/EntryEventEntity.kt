package com.aura.entry.internal.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** Событие в записи — таблица entry.entry_events; ссылка на catalog.events — по id, без FK (чужая схема). */
@Entity
@Table(name = "entry_events", schema = "entry")
class EntryEventEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "entry_id", nullable = false)
    val entryId: Long,
    @Column(name = "event_id", nullable = false)
    val eventId: Long,
)

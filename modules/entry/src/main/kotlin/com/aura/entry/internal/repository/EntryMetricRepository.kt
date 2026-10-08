package com.aura.entry.internal.repository

import com.aura.entry.internal.entity.EntryMetricEntity
import org.springframework.data.jpa.repository.JpaRepository

interface EntryMetricRepository : JpaRepository<EntryMetricEntity, Long> {

    fun findAllByEntryIdOrderByIdAsc(entryId: Long): List<EntryMetricEntity>

    fun findAllByEntryIdInOrderByIdAsc(entryIds: Collection<Long>): List<EntryMetricEntity>

    /** Полная замена коллекции записи (см. EntryService). */
    fun deleteByEntryId(entryId: Long)
}

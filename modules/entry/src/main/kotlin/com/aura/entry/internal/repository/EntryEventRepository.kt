package com.aura.entry.internal.repository

import com.aura.entry.internal.entity.EntryEventEntity
import org.springframework.data.jpa.repository.JpaRepository

interface EntryEventRepository : JpaRepository<EntryEventEntity, Long> {

    fun findAllByEntryIdOrderByIdAsc(entryId: Long): List<EntryEventEntity>

    fun findAllByEntryIdInOrderByIdAsc(entryIds: Collection<Long>): List<EntryEventEntity>

    /** Полная замена коллекции записи (см. EntryService). */
    fun deleteByEntryId(entryId: Long)
}

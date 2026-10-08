package com.aura.entry.internal.repository

import com.aura.entry.internal.entity.EntryFactorEntity
import org.springframework.data.jpa.repository.JpaRepository

interface EntryFactorRepository : JpaRepository<EntryFactorEntity, Long> {

    fun findAllByEntryIdOrderByIdAsc(entryId: Long): List<EntryFactorEntity>

    fun findAllByEntryIdInOrderByIdAsc(entryIds: Collection<Long>): List<EntryFactorEntity>

    /** Полная замена коллекции записи (см. EntryService). */
    fun deleteByEntryId(entryId: Long)
}

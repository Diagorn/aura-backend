package com.aura.entry.internal.repository

import com.aura.entry.internal.entity.EntryEmotionEntity
import org.springframework.data.jpa.repository.JpaRepository

interface EntryEmotionRepository : JpaRepository<EntryEmotionEntity, Long> {

    fun findAllByEntryIdOrderByIdAsc(entryId: Long): List<EntryEmotionEntity>

    fun findAllByEntryIdInOrderByIdAsc(entryIds: Collection<Long>): List<EntryEmotionEntity>

    /** Полная замена коллекции записи: старые строки удаляются, новые вставляются (см. EntryService). */
    fun deleteByEntryId(entryId: Long)
}

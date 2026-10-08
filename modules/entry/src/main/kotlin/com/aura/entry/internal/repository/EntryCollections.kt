package com.aura.entry.internal.repository

import com.aura.entry.api.EntryEmotionInput
import com.aura.entry.api.EntryEventInput
import com.aura.entry.api.EntryFactorInput
import com.aura.entry.api.EntryMetricInput
import com.aura.entry.internal.entity.EntryEmotionEntity
import com.aura.entry.internal.entity.EntryEventEntity
import com.aura.entry.internal.entity.EntryFactorEntity
import com.aura.entry.internal.entity.EntryMetricEntity
import org.springframework.stereotype.Component

/** Переданные коллекции записи (для создания — все непустые, для PATCH — только переданные). */
data class EntryCollectionCommands(
    val emotions: List<EntryEmotionInput>?,
    val factors: List<EntryFactorInput>?,
    val events: List<EntryEventInput>?,
    val metrics: List<EntryMetricInput>?,
)

/**
 * Дочерние коллекции записи (эмоции/факторы/события/метрики) — фасад над четырьмя
 * репозиториями. Полная замена коллекций записи (delete+insert в одной транзакции)
 * и батч-чтение для списков: по одному запросу на коллекцию, без N+1.
 */
@Component
class EntryCollections(
    private val emotions: EntryEmotionRepository,
    private val factors: EntryFactorRepository,
    private val events: EntryEventRepository,
    private val metrics: EntryMetricRepository,
) {

    /** Переданная коллекция заменяется целиком; отсутствующая — не трогается. */
    fun replace(entryId: Long, commands: EntryCollectionCommands) {
        commands.emotions?.let { list ->
            emotions.deleteByEntryId(entryId)
            emotions.saveAll(
                list.map {
                    EntryEmotionEntity(entryId = entryId, emotionId = it.emotionId, intensity = it.intensity, influence = it.influence)
                },
            )
        }
        commands.factors?.let { list ->
            factors.deleteByEntryId(entryId)
            factors.saveAll(list.map { EntryFactorEntity(entryId = entryId, factorId = it.factorId, intensity = it.intensity) })
        }
        commands.events?.let { list ->
            events.deleteByEntryId(entryId)
            events.saveAll(list.map { EntryEventEntity(entryId = entryId, eventId = it.eventId) })
        }
        commands.metrics?.let { list ->
            metrics.deleteByEntryId(entryId)
            metrics.saveAll(list.map { EntryMetricEntity(entryId = entryId, metricId = it.metricId, value = it.value) })
        }
    }

    fun deleteByEntryId(entryId: Long) {
        emotions.deleteByEntryId(entryId)
        factors.deleteByEntryId(entryId)
        events.deleteByEntryId(entryId)
        metrics.deleteByEntryId(entryId)
    }

    fun emotionsByEntry(entryIds: Collection<Long>): Map<Long, List<EntryEmotionEntity>> =
        emotions.findAllByEntryIdInOrderByIdAsc(entryIds).groupBy { it.entryId }

    fun factorsByEntry(entryIds: Collection<Long>): Map<Long, List<EntryFactorEntity>> =
        factors.findAllByEntryIdInOrderByIdAsc(entryIds).groupBy { it.entryId }

    fun eventsByEntry(entryIds: Collection<Long>): Map<Long, List<EntryEventEntity>> =
        events.findAllByEntryIdInOrderByIdAsc(entryIds).groupBy { it.entryId }

    fun metricsByEntry(entryIds: Collection<Long>): Map<Long, List<EntryMetricEntity>> =
        metrics.findAllByEntryIdInOrderByIdAsc(entryIds).groupBy { it.entryId }

    /** Текущие эмоции записи в виде команд (для проверки итогового состава при PATCH). */
    fun currentEmotions(entryId: Long): List<EntryEmotionInput> =
        emotions.findAllByEntryIdOrderByIdAsc(entryId).map { EntryEmotionInput(it.emotionId, it.intensity, it.influence) }
}

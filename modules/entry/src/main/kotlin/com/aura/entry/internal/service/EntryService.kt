package com.aura.entry.internal.service

import com.aura.catalog.api.EmotionsPort
import com.aura.catalog.api.EventsPort
import com.aura.catalog.api.FactorsPort
import com.aura.catalog.api.MetricsPort
import com.aura.entry.api.CreateEntry
import com.aura.entry.api.DailyEntriesLimitExceededException
import com.aura.entry.api.EntriesPort
import com.aura.entry.api.EntryCalendar
import com.aura.entry.api.EntryCalendarDay
import com.aura.entry.api.EntryCompleted
import com.aura.entry.api.EntryDeleted
import com.aura.entry.api.EntryDetails
import com.aura.entry.api.EntryEmotion
import com.aura.entry.api.EntryEvent
import com.aura.entry.api.EntryFactor
import com.aura.entry.api.EntryFilter
import com.aura.entry.api.EntryFilterValidationException
import com.aura.entry.api.EntryMetric
import com.aura.entry.api.EntryPage
import com.aura.entry.api.EntrySort
import com.aura.entry.api.EntrySortField
import com.aura.entry.api.EntryStatus
import com.aura.entry.api.EntryUpdated
import com.aura.entry.api.EntryValidationException
import com.aura.entry.api.InvalidEntryStatusTransitionException
import com.aura.entry.internal.config.EntryProperties
import com.aura.entry.internal.entity.EntryEntity
import com.aura.entry.internal.event.EntryEventsDispatcher
import com.aura.entry.internal.repository.EntryCollectionCommands
import com.aura.entry.internal.repository.EntryCollections
import com.aura.entry.internal.repository.EntryRepository
import com.aura.entry.internal.repository.EntrySpecifications
import com.aura.shared.FieldError
import com.aura.user.api.UserProfilePort
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Записи чек-ина: создание с вложенными коллекциями, DRAFT-цикл, навигация по датам,
 * календарь. entry_date вычисляется по таймзоне пользователя (UserProfilePort);
 * элементы справочников валидируются через публичные порты catalog (существование,
 * владение, активность, шкала метрик). Суточный лимит — [EntryProperties.maxPerDay].
 * Факты (см. docs/architecture-overview.md §5) доставляет [EntryEventsDispatcher]
 * в транзакции продюсера.
 */
@Service
@Transactional
class EntryService(
    private val entries: EntryRepository,
    private val collections: EntryCollections,
    private val users: UserProfilePort,
    private val emotionsCatalog: EmotionsPort,
    private val factorsCatalog: FactorsPort,
    private val eventsCatalog: EventsPort,
    private val metricsCatalog: MetricsPort,
    private val facts: EntryEventsDispatcher,
    private val properties: EntryProperties,
) : EntriesPort {

    override fun create(userId: Long, command: CreateEntry): EntryDetails {
        val entryDate = currentDate(userId)
        ensureDailyLimit(userId, entryDate)
        val commands = EntryCollectionCommands(command.emotions, command.factors, command.events, command.metrics)
        validate(userId, command.status, commands)

        val now = Instant.now()
        val entity = entries.save(
            EntryEntity(
                userId = userId,
                entryDate = entryDate,
                recordedAt = now,
                source = command.source,
                status = command.status,
                note = command.note?.takeIf { it.isNotEmpty() },
                createdAt = now,
                updatedAt = now,
            ),
        )
        collections.replace(entity.id, commands)
        if (entity.status == EntryStatus.COMPLETED) {
            facts.entryCompleted(EntryCompleted(entity.id, userId, entity.entryDate))
        }
        return toDetails(userId, listOf(entity)).first()
    }

    @Transactional(readOnly = true)
    override fun get(userId: Long, id: Long): EntryDetails =
        toDetails(userId, listOf(entries.requireOwned(userId, id))).first()

    override fun update(userId: Long, id: Long, command: com.aura.entry.api.UpdateEntry): EntryDetails {
        val entity = entries.requireOwned(userId, id)
        val wasCompleted = entity.status == EntryStatus.COMPLETED
        if (wasCompleted && command.status == EntryStatus.DRAFT) {
            throw InvalidEntryStatusTransitionException()
        }

        // Итоговый состав эмоций: переданный или текущий — минимум одна для COMPLETED
        val finalEmotions = command.emotions ?: collections.currentEmotions(id)
        if ((command.status ?: entity.status) == EntryStatus.COMPLETED && finalEmotions.isEmpty()) {
            throw EntryValidationException(listOf(FieldError("emotions", REQUIRED_EMOTION_MESSAGE)))
        }

        command.status?.let { entity.status = it }
        command.source?.let { entity.source = it }
        command.note?.let { entity.note = it.takeIf { note -> note.isNotEmpty() } }
        val commands = EntryCollectionCommands(command.emotions, command.factors, command.events, command.metrics)
        validate(userId, entity.status, commands)
        collections.replace(entity.id, commands)
        entity.updatedAt = Instant.now()

        val saved = entries.save(entity)
        if (!wasCompleted && saved.status == EntryStatus.COMPLETED) {
            facts.entryCompleted(EntryCompleted(saved.id, userId, saved.entryDate))
        } else {
            facts.entryUpdated(EntryUpdated(saved.id, userId, saved.entryDate))
        }
        return toDetails(userId, listOf(saved)).first()
    }

    override fun delete(userId: Long, id: Long) {
        val entity = entries.requireOwned(userId, id)
        collections.deleteByEntryId(id)
        entries.delete(entity)
        facts.entryDeleted(EntryDeleted(entity.id, userId, entity.entryDate))
    }

    @Transactional(readOnly = true)
    override fun list(userId: Long, filter: EntryFilter, page: Int, size: Int, sort: EntrySort?): EntryPage {
        val (from, to) = resolveRange(filter)
        val pageable = PageRequest.of(page, size, toSpringSort(sort))
        val entityPage = entries.findAll(
            EntrySpecifications.listPage(userId, from, to, filter.status, filter.source),
            pageable,
        )
        return EntryPage(
            items = toDetails(userId, entityPage.content),
            page = entityPage.number,
            size = entityPage.size,
            totalElements = entityPage.totalElements,
            totalPages = entityPage.totalPages,
        )
    }

    @Transactional(readOnly = true)
    override fun calendar(userId: Long, month: YearMonth?): EntryCalendar {
        val resolved = month ?: YearMonth.from(currentDate(userId))
        val rows = entries.aggregateByDate(userId, resolved.atDay(1), resolved.atEndOfMonth())
        return EntryCalendar(
            month = resolved,
            days = rows.map { EntryCalendarDay(it.entryDate, it.total, it.completed > 0) },
        )
    }

    /** Локальная дата пользователя: текущий момент в его таймзоне профиля. */
    private fun currentDate(userId: Long): LocalDate {
        val profile = users.get(userId)
        return LocalDate.ofInstant(Instant.now(), ZoneId.of(profile.timezone))
    }

    private fun ensureDailyLimit(userId: Long, entryDate: LocalDate) {
        val created = entries.countByUserIdAndEntryDate(userId, entryDate)
        if (created >= properties.maxPerDay) {
            throw DailyEntriesLimitExceededException(properties.maxPerDay)
        }
    }

    /** Валидация состава записи; ошибки всех коллекций собираются в один problem (422). */
    private fun validate(
        userId: Long,
        status: EntryStatus,
        commands: EntryCollectionCommands,
    ) {
        if (status == EntryStatus.COMPLETED && commands.emotions != null && commands.emotions.isEmpty()) {
            // при PATCH итоговый состав проверяется по currentEmotions до этого вызова
            throw EntryValidationException(listOf(FieldError("emotions", REQUIRED_EMOTION_MESSAGE)))
        }
        val errors = mutableListOf<FieldError>()
        errors += validateDuplicates("emotions", "emotionId", commands.emotions?.map { it.emotionId })
        errors += validateDuplicates("factors", "factorId", commands.factors?.map { it.factorId })
        errors += validateDuplicates("events", "eventId", commands.events?.map { it.eventId })
        errors += validateDuplicates("metrics", "metricId", commands.metrics?.map { it.metricId })
        errors += validateRanges(commands)
        errors += validateCatalogRefs(userId, commands)
        if (errors.isNotEmpty()) {
            throw EntryValidationException(errors)
        }
    }

    private fun validateDuplicates(field: String, subField: String, ids: List<Long>?): List<FieldError> =
        ids.orEmpty()
            .groupBy { it }
            .filterValues { it.size > 1 }
            .keys
            .map { FieldError("$field.$subField", "повторяющийся идентификатор: $it") }

    /** Шкалы интенсивности/влияния зафиксированы доменом (см. docs/domain-model.md). */
    private fun validateRanges(commands: EntryCollectionCommands): List<FieldError> = buildList {
        commands.emotions?.forEachIndexed { index, item ->
            if (item.intensity !in INTENSITY_RANGE) {
                add(FieldError("emotions[$index].intensity", "должна быть от 1 до 5"))
            }
            if (item.influence !in INTENSITY_RANGE) {
                add(FieldError("emotions[$index].influence", "должно быть от 1 до 5"))
            }
        }
        commands.factors?.forEachIndexed { index, item ->
            val intensity = item.intensity
            if (intensity != null && intensity !in INTENSITY_RANGE) {
                add(FieldError("factors[$index].intensity", "должна быть от 1 до 5"))
            }
        }
    }

    /** Существование/владение и активность элементов справочников, значения метрик — в шкалах. */
    private fun validateCatalogRefs(userId: Long, commands: EntryCollectionCommands): List<FieldError> {
        val errors = mutableListOf<FieldError>()
        val emotionIds = commands.emotions?.map { it.emotionId }.orEmpty()
        val factorIds = commands.factors?.map { it.factorId }.orEmpty()
        val eventIds = commands.events?.map { it.eventId }.orEmpty()
        val metricIds = commands.metrics?.map { it.metricId }.orEmpty()

        checkAccessible(errors, emotionIds, "emotions", "emotionId") {
            emotionsCatalog.findVisibleByIds(userId, emotionIds).filter { it.isActive }.associateBy { it.id }
        }
        checkAccessible(errors, factorIds, "factors", "factorId") {
            factorsCatalog.findVisibleByIds(userId, factorIds).filter { it.isActive }.associateBy { it.id }
        }
        checkAccessible(errors, eventIds, "events", "eventId") {
            eventsCatalog.findVisibleByIds(userId, eventIds).filter { it.isActive }.associateBy { it.id }
        }

        // Метрики: те же проверки плюс значение в шкале конкретной метрики
        val visibleMetrics = metricsCatalog.findVisibleByIds(userId, metricIds).filter { it.isActive }.associateBy { it.id }
        commands.metrics?.forEachIndexed { index, item ->
            val metric = visibleMetrics[item.metricId]
            if (metric == null) {
                errors += FieldError("metrics[$index].metricId", NOT_ACCESSIBLE_MESSAGE)
                return@forEachIndexed
            }
            if (item.value < metric.minValue || item.value > metric.maxValue) {
                errors +=
                    FieldError("metrics[$index].value", "должно быть в пределах шкалы метрики (${metric.minValue}–${metric.maxValue})")
            }
        }
        return errors
    }

    /** Каждый id должен попасть в доступный набор (системные + личные пользователя, активные). */
    private fun <T> checkAccessible(
        errors: MutableList<FieldError>,
        ids: List<Long>,
        field: String,
        idField: String,
        fetchAccessible: () -> Map<Long, T>,
    ) {
        if (ids.isEmpty()) return
        val accessible = fetchAccessible()
        ids.forEachIndexed { index, id ->
            if (id !in accessible) {
                errors += FieldError("$field[$index].$idField", NOT_ACCESSIBLE_MESSAGE)
            }
        }
    }

    /** date — взаимоисключающ с парой from/to; дата сворачивается в диапазон «сама на себя».
     *  Фильтры даты не переданы вовсе — (null, null): все записи пользователя. */
    private fun resolveRange(filter: EntryFilter): Pair<LocalDate?, LocalDate?> {
        val errors = mutableListOf<FieldError>()
        if (filter.date != null && (filter.from != null || filter.to != null)) {
            errors += FieldError("date", "взаимоисключающ с параметрами from/to")
        }
        if ((filter.from == null) != (filter.to == null)) {
            errors += FieldError("from", "передаётся вместе с to")
        }
        val from = filter.from
        val to = filter.to
        if (from != null && to != null && to < from) {
            errors += FieldError("to", "должен быть не раньше from")
        }
        if (errors.isNotEmpty()) {
            throw EntryFilterValidationException(errors)
        }
        filter.date?.let { return it to it }
        return from to to
    }

    private fun toSpringSort(sort: EntrySort?): Sort {
        val field = when (sort?.field) {
            EntrySortField.ENTRY_DATE -> "entryDate"
            null, EntrySortField.RECORDED_AT -> "recordedAt"
        }
        val direction = if (sort?.descending != false) Sort.Direction.DESC else Sort.Direction.ASC
        return Sort.by(direction, field, "id")
    }

    /** Полные записи с именами справочников; на страницу — по 4 запроса на коллекцию (без N+1). */
    private fun toDetails(userId: Long, entities: List<EntryEntity>): List<EntryDetails> {
        if (entities.isEmpty()) return emptyList()
        val ids = entities.map { it.id }
        val emotionsByEntry = collections.emotionsByEntry(ids)
        val factorsByEntry = collections.factorsByEntry(ids)
        val eventsByEntry = collections.eventsByEntry(ids)
        val metricsByEntry = collections.metricsByEntry(ids)

        // Имена на момент чтения; удалённый из справочника элемент даёт пустое имя
        val emotionNames = emotionsCatalog.findVisibleByIds(userId, emotionsByEntry.values.flatten().map { it.emotionId })
            .associate { it.id to it.name }
        val factorNames = factorsCatalog.findVisibleByIds(userId, factorsByEntry.values.flatten().map { it.factorId })
            .associate { it.id to it.name }
        val eventNames = eventsCatalog.findVisibleByIds(userId, eventsByEntry.values.flatten().map { it.eventId })
            .associate { it.id to it.name }
        val metricNames = metricsCatalog.findVisibleByIds(userId, metricsByEntry.values.flatten().map { it.metricId })
            .associate { it.id to it.name }

        return entities.map { entity ->
            EntryDetails(
                id = entity.id,
                userId = entity.userId,
                entryDate = entity.entryDate,
                recordedAt = entity.recordedAt,
                source = entity.source,
                status = entity.status,
                emotions = emotionsByEntry[entity.id].orEmpty().map {
                    EntryEmotion(it.emotionId, emotionNames[it.emotionId].orEmpty(), it.intensity, it.influence)
                },
                factors = factorsByEntry[entity.id].orEmpty().map {
                    EntryFactor(it.factorId, factorNames[it.factorId].orEmpty(), it.intensity)
                },
                events = eventsByEntry[entity.id].orEmpty().map {
                    EntryEvent(it.eventId, eventNames[it.eventId].orEmpty())
                },
                metrics = metricsByEntry[entity.id].orEmpty().map {
                    EntryMetric(it.metricId, metricNames[it.metricId].orEmpty(), it.value)
                },
                note = entity.note,
            )
        }
    }

    companion object {
        /** Шкала интенсивности/влияния зафиксирована доменом (см. docs/domain-model.md). */
        private val INTENSITY_RANGE = 1..5

        private const val REQUIRED_EMOTION_MESSAGE = "должна быть минимум одна эмоция для COMPLETED"

        private const val NOT_ACCESSIBLE_MESSAGE = "не найден или недоступен"
    }
}

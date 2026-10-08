package com.aura.web.entry

import com.aura.api.model.CalendarDay
import com.aura.api.model.CalendarResponse
import com.aura.api.model.CreateEntryRequest
import com.aura.api.model.EntryEmotionItem
import com.aura.api.model.EntryEventItem
import com.aura.api.model.EntryFactorItem
import com.aura.api.model.EntryMetricItem
import com.aura.api.model.EntryPage
import com.aura.api.model.EntryResponse
import com.aura.api.model.EntrySource as ApiEntrySource
import com.aura.api.model.EntryStatus as ApiEntryStatus
import com.aura.api.model.UpdateEntryRequest
import com.aura.entry.api.EntryCalendarDay
import com.aura.entry.api.EntryDetails
import com.aura.entry.api.EntryEmotion
import com.aura.entry.api.EntryEmotionInput
import com.aura.entry.api.EntryEvent
import com.aura.entry.api.EntryEventInput
import com.aura.entry.api.EntryFactor
import com.aura.entry.api.EntryFactorInput
import com.aura.entry.api.EntryFilter
import com.aura.entry.api.EntryMetric
import com.aura.entry.api.EntryMetricInput
import com.aura.entry.api.EntryPage as DomainEntryPage
import com.aura.entry.api.EntrySort
import com.aura.entry.api.EntrySortField
import com.aura.entry.api.EntrySource
import com.aura.entry.api.EntryStatus
import com.aura.entry.api.CreateEntry
import com.aura.entry.api.UpdateEntry
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneOffset

/** Маппинг записей чек-ина в контракт спеки и обратно (EntriesApi). */

internal fun EntryDetails.toResponse(): EntryResponse = EntryResponse(
    id = id,
    userId = userId,
    entryDate = entryDate,
    recordedAt = OffsetDateTime.ofInstant(recordedAt, ZoneOffset.UTC),
    source = source.toApi(),
    status = status.toApi(),
    emotions = emotions.map { it.toItem() },
    factors = factors.map { it.toItem() },
    events = events.map { it.toItem() },
    metrics = metrics.map { it.toItem() },
    note = note,
)

private fun EntryEmotion.toItem(): EntryEmotionItem =
    EntryEmotionItem(emotionId = emotionId, name = name, intensity = intensity, influence = influence)

private fun EntryFactor.toItem(): EntryFactorItem =
    EntryFactorItem(factorId = factorId, name = name, intensity = intensity)

private fun EntryEvent.toItem(): EntryEventItem = EntryEventItem(eventId = eventId, name = name)

private fun EntryMetric.toItem(): EntryMetricItem =
    EntryMetricItem(metricId = metricId, name = name, value = value)

internal fun DomainEntryPage.toResponse(): EntryPage = EntryPage(
    items = items.map { it.toResponse() },
    page = page,
    propertySize = size,
    totalElements = totalElements,
    totalPages = totalPages,
)

internal fun EntryCalendarDay.toResponse(): CalendarDay =
    CalendarDay(date = date, entriesCount = entriesCount.toInt(), hasCompleted = hasCompleted)

internal fun com.aura.entry.api.EntryCalendar.toResponse(): CalendarResponse =
    CalendarResponse(month = month.toString(), days = days.map { it.toResponse() })

internal fun CreateEntryRequest.toCommand(): CreateEntry = CreateEntry(
    status = status.toModel(),
    source = (source ?: ApiEntrySource.WEB).toModel(),
    emotions = emotions.orEmpty().map { EntryEmotionInput(it.emotionId, it.intensity, it.influence) },
    factors = factors.orEmpty().map { EntryFactorInput(it.factorId, it.intensity) },
    events = events.orEmpty().map { EntryEventInput(it.eventId) },
    metrics = metrics.orEmpty().map { EntryMetricInput(it.metricId, it.value) },
    note = note,
)

internal fun UpdateEntryRequest.toCommand(): UpdateEntry = UpdateEntry(
    status = status?.toModel(),
    source = source?.toModel(),
    emotions = emotions?.map { EntryEmotionInput(it.emotionId, it.intensity, it.influence) },
    factors = factors?.map { EntryFactorInput(it.factorId, it.intensity) },
    events = events?.map { EntryEventInput(it.eventId) },
    metrics = metrics?.map { EntryMetricInput(it.metricId, it.value) },
    note = note,
)

internal fun EntryStatus.toApi(): ApiEntryStatus =
    if (this == EntryStatus.COMPLETED) ApiEntryStatus.COMPLETED else ApiEntryStatus.DRAFT

internal fun EntrySource.toApi(): ApiEntrySource =
    if (this == EntrySource.TELEGRAM) ApiEntrySource.TELEGRAM else ApiEntrySource.WEB

internal fun ApiEntryStatus.toModel(): EntryStatus =
    if (this == ApiEntryStatus.COMPLETED) EntryStatus.COMPLETED else EntryStatus.DRAFT

internal fun ApiEntrySource.toModel(): EntrySource =
    if (this == ApiEntrySource.TELEGRAM) EntrySource.TELEGRAM else EntrySource.WEB

/** Разбор sort=поле,направление; поддерживаются recordedAt/entryDate и asc/desc. */
internal fun parseSort(sort: String): EntrySort? {
    val parts = sort.split(',')
    val field = when (parts[0]) {
        "recordedAt" -> EntrySortField.RECORDED_AT
        "entryDate" -> EntrySortField.ENTRY_DATE
        else -> throw com.aura.shared.ValidationFailedException("sort", "поддерживаются поля recordedAt и entryDate")
    }
    val descending = when (parts.getOrNull(1)?.lowercase()) {
        null, "", "desc" -> true
        "asc" -> false
        else -> throw com.aura.shared.ValidationFailedException("sort", "направление — asc или desc")
    }
    return EntrySort(field, descending)
}

/** Разбор month=yyyy-MM; некорректный формат — 422. */
internal fun parseMonth(month: String): YearMonth = try {
    YearMonth.parse(month)
} catch (exception: java.time.format.DateTimeParseException) {
    throw com.aura.shared.ValidationFailedException("month", "ожидается формат yyyy-MM")
}

internal fun toFilter(date: LocalDate?, from: LocalDate?, to: LocalDate?, status: ApiEntryStatus?, source: ApiEntrySource?): EntryFilter =
    EntryFilter(
        date = date,
        from = from,
        to = to,
        status = status?.toModel(),
        source = source?.toModel(),
    )

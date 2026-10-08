package com.aura.entry.api

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** Статус записи: начатый, но не завершённый чек-ин или завершённый. */
enum class EntryStatus { DRAFT, COMPLETED }

/** Клиент, с которого создана запись; передаётся клиентом, по умолчанию WEB. */
enum class EntrySource { WEB, TELEGRAM }

/** Эмоция в записи: интенсивность и влияние, шкала 1–5. */
data class EntryEmotionInput(
    val emotionId: Long,
    val intensity: Int,
    val influence: Int,
)

/** Фактор в записи; intensity null — выраженность не отмечена. */
data class EntryFactorInput(
    val factorId: Long,
    val intensity: Int?,
)

/** Событие в записи. */
data class EntryEventInput(
    val eventId: Long,
)

/** Значение метрики в записи — в пределах шкалы своей метрики. */
data class EntryMetricInput(
    val metricId: Long,
    val value: BigDecimal,
)

/** Создание записи одной командой с вложенными коллекциями. */
data class CreateEntry(
    val status: EntryStatus,
    val source: EntrySource,
    val emotions: List<EntryEmotionInput>,
    val factors: List<EntryFactorInput>,
    val events: List<EntryEventInput>,
    val metrics: List<EntryMetricInput>,
    val note: String?,
)

/**
 * Обновление записи: PATCH-семантика — null поле не менять. Переданная коллекция
 * заменяется целиком. Пустая строка note — очистить (explicit null от клиента
 * неотличим от отсутствия поля).
 */
data class UpdateEntry(
    val status: EntryStatus?,
    val source: EntrySource?,
    val emotions: List<EntryEmotionInput>?,
    val factors: List<EntryFactorInput>?,
    val events: List<EntryEventInput>?,
    val metrics: List<EntryMetricInput>?,
    val note: String?,
)

/** Элементы записи с именами из справочника на момент чтения. */
data class EntryEmotion(
    val emotionId: Long,
    val name: String,
    val intensity: Int,
    val influence: Int,
)

data class EntryFactor(
    val factorId: Long,
    val name: String,
    val intensity: Int?,
)

data class EntryEvent(
    val eventId: Long,
    val name: String,
)

data class EntryMetric(
    val metricId: Long,
    val name: String,
    val value: BigDecimal,
)

/** Полная запись. */
data class EntryDetails(
    val id: Long,
    val userId: Long,
    /** Локальная дата пользователя («за какое число»), вычислена сервером по его таймзоне. */
    val entryDate: LocalDate,
    val recordedAt: Instant,
    val source: EntrySource,
    val status: EntryStatus,
    val emotions: List<EntryEmotion>,
    val factors: List<EntryFactor>,
    val events: List<EntryEvent>,
    val metrics: List<EntryMetric>,
    val note: String?,
)

/** Фильтр списка: date — взаимоисключающ с диапазоном from/to; диапазон включительный. */
data class EntryFilter(
    val date: LocalDate?,
    val from: LocalDate?,
    val to: LocalDate?,
    val status: EntryStatus?,
    val source: EntrySource?,
)

enum class EntrySortField { RECORDED_AT, ENTRY_DATE }

/** Сортировка списка записей; null — recordedAt по убыванию. */
data class EntrySort(
    val field: EntrySortField,
    val descending: Boolean,
)

/** Страница списка — конверт {items, page, size, totalElements, totalPages} (см. docs/api-design.md). */
data class EntryPage(
    val items: List<EntryDetails>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

/** День календаря с записями. */
data class EntryCalendarDay(
    val date: LocalDate,
    val entriesCount: Long,
    val hasCompleted: Boolean,
)

/** Календарь месяца: только дни, в которые есть хотя бы одна запись. */
data class EntryCalendar(
    val month: YearMonth,
    val days: List<EntryCalendarDay>,
)

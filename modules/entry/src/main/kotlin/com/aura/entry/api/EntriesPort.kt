package com.aura.entry.api

import java.time.YearMonth

/**
 * Публичный контракт записей чек-ина: создание с вложенными коллекциями, DRAFT-цикл,
 * навигация по датам и календарь месяца. Реализация — внутренний сервис модуля entry.
 */
interface EntriesPort {

    /**
     * Создаёт запись; entry_date вычисляется по таймзоне пользователя из момента
     * сохранения, recorded_at проставляет сервер. Превышен суточный лимит —
     * [DailyEntriesLimitExceededException]; нарушены правила состава (для COMPLETED
     * нужна минимум одна эмоция, элементы справочников должны быть доступны и активны,
     * значения метрик — в шкалах) — [EntryValidationException].
     */
    fun create(userId: Long, command: CreateEntry): EntryDetails

    /** Полная запись; чужая/несуществующая — [com.aura.shared.NotFoundException]. */
    fun get(userId: Long, id: Long): EntryDetails

    /**
     * Дозаполнение/правки: null поле не менять, переданная коллекция заменяется целиком.
     * COMPLETED -> DRAFT — [InvalidEntryStatusTransitionException]; нарушение правил
     * состава итоговой записи — [EntryValidationException].
     */
    fun update(userId: Long, id: Long, command: UpdateEntry): EntryDetails

    /** Удаляет запись вместе с вложенными коллекциями; чужая/несуществующая — [com.aura.shared.NotFoundException]. */
    fun delete(userId: Long, id: Long)

    /**
     * Список записей с пагинацией. Фильтр: date либо from+to (взаимоисключаемость и
     * полнота пары — [EntryFilterValidationException]); оба не заданы — все записи
     * пользователя. Сортировка null — recordedAt,desc.
     */
    fun list(userId: Long, filter: EntryFilter, page: Int, size: Int, sort: EntrySort?): EntryPage

    /** Календарь месяца в таймзоне пользователя: только дни, в которые есть записи.
     *  month null — текущий месяц по таймзоне пользователя. */
    fun calendar(userId: Long, month: YearMonth?): EntryCalendar
}

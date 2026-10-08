package com.aura.entry.api

import java.time.LocalDate

/**
 * Факты модуля entry и порт их доставки (см. docs/architecture-overview.md §5):
 * синхронный вызов [EntryEventsPort] в транзакции продюсера, без асинхронщины —
 * потребители (этап 5 — analytics, этап 7 — notification) реализуют порт своим
 * бином и не должны бросать исключений. При отсутствии реализаций факты
 * никуда не доставляются.
 */

/** Запись завершена: создана сразу COMPLETED или переведена из DRAFT. */
data class EntryCompleted(
    val entryId: Long,
    val userId: Long,
    val entryDate: LocalDate,
)

/** Запись изменена без перехода в COMPLETED. */
data class EntryUpdated(
    val entryId: Long,
    val userId: Long,
    val entryDate: LocalDate,
)

/** Запись удалена. */
data class EntryDeleted(
    val entryId: Long,
    val userId: Long,
    val entryDate: LocalDate,
)

/** Порт фактов entry; реализуется модулями-потребителями. */
interface EntryEventsPort {

    fun entryCompleted(event: EntryCompleted)

    fun entryUpdated(event: EntryUpdated)

    fun entryDeleted(event: EntryDeleted)
}

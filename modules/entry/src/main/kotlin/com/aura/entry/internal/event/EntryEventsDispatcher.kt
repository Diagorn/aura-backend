package com.aura.entry.internal.event

import com.aura.entry.api.EntryCompleted
import com.aura.entry.api.EntryDeleted
import com.aura.entry.api.EntryEventsPort
import com.aura.entry.api.EntryUpdated
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component

/**
 * Доставка фактов entry потребителям — вместо ApplicationEventPublisher:
 * синхронный вызов методов всех реализаций [EntryEventsPort] в транзакции продюсера
 * (тот же паттерн, что AuthEventsDispatcher в auth).
 *
 * Реализаций сегодня нет — вызовы no-op; этап 5 (analytics) добавит свой бин,
 * и факты начнут доставляться без изменений в entry.
 */
@Component
class EntryEventsDispatcher(
    ports: ObjectProvider<EntryEventsPort>,
) {

    private val listeners: List<EntryEventsPort> = ports.orderedStream().toList()

    fun entryCompleted(event: EntryCompleted) {
        listeners.forEach { it.entryCompleted(event) }
    }

    fun entryUpdated(event: EntryUpdated) {
        listeners.forEach { it.entryUpdated(event) }
    }

    fun entryDeleted(event: EntryDeleted) {
        listeners.forEach { it.entryDeleted(event) }
    }
}

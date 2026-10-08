package com.aura.web.entry

import com.aura.api.EntriesApi
import com.aura.api.model.CalendarResponse
import com.aura.api.model.CreateEntryRequest
import com.aura.api.model.EntryPage
import com.aura.api.model.EntryResponse
import com.aura.api.model.EntrySource
import com.aura.api.model.EntryStatus
import com.aura.api.model.UpdateEntryRequest
import com.aura.entry.api.EntriesPort
import com.aura.security.CurrentUser
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * Записи чек-ина (/api/v1/entries).
 * Контракт — сгенерированный [EntriesApi]; данные — через публичный порт модуля entry.
 */
@RestController
class EntryController(
    private val entries: EntriesPort,
) : EntriesApi {

    override fun createEntry(createEntryRequest: CreateEntryRequest): ResponseEntity<EntryResponse> =
        created(entries.create(CurrentUser.requireUserId(), createEntryRequest.toCommand()).toResponse())

    override fun listEntries(
        date: LocalDate?,
        from: LocalDate?,
        to: LocalDate?,
        status: EntryStatus?,
        source: EntrySource?,
        page: Int,
        size: Int,
        sort: String,
    ): ResponseEntity<EntryPage> = ResponseEntity.ok(
        entries
            .list(
                userId = CurrentUser.requireUserId(),
                filter = toFilter(date, from, to, status, source),
                page = page,
                size = size,
                sort = parseSort(sort),
            )
            .toResponse(),
    )

    override fun getEntry(id: Long): ResponseEntity<EntryResponse> =
        ResponseEntity.ok(entries.get(CurrentUser.requireUserId(), id).toResponse())

    override fun updateEntry(id: Long, updateEntryRequest: UpdateEntryRequest): ResponseEntity<EntryResponse> =
        ResponseEntity.ok(entries.update(CurrentUser.requireUserId(), id, updateEntryRequest.toCommand()).toResponse())

    override fun deleteEntry(id: Long): ResponseEntity<Unit> {
        entries.delete(CurrentUser.requireUserId(), id)
        return ResponseEntity.noContent().build()
    }

    override fun getEntryCalendar(month: String?): ResponseEntity<CalendarResponse> =
        ResponseEntity.ok(
            entries
                .calendar(CurrentUser.requireUserId(), month?.let(::parseMonth))
                .toResponse(),
        )

    private fun <T : Any> created(body: T): ResponseEntity<T> = ResponseEntity.status(HttpStatus.CREATED).body(body)
}

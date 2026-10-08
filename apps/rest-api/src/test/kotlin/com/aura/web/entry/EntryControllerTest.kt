package com.aura.web.entry

import com.aura.api.model.CalendarDay
import com.aura.api.model.CreateEntryRequest
import com.aura.api.model.EntryEmotionInput
import com.aura.api.model.EntryEventInput
import com.aura.api.model.EntryFactorInput
import com.aura.api.model.EntryMetricInput
import com.aura.api.model.EntrySource as ApiEntrySource
import com.aura.api.model.EntryStatus as ApiEntryStatus
import com.aura.api.model.UpdateEntryRequest
import com.aura.auth.api.AccessTokenVerifier
import com.aura.entry.api.CreateEntry
import com.aura.entry.api.EntriesPort
import com.aura.entry.api.EntryCalendar
import com.aura.entry.api.EntryCalendarDay
import com.aura.entry.api.EntryDetails
import com.aura.entry.api.EntryEmotion
import com.aura.entry.api.EntryEvent
import com.aura.entry.api.EntryFactor
import com.aura.entry.api.EntryFilter
import com.aura.entry.api.EntryMetric
import com.aura.entry.api.EntryPage as DomainEntryPage
import com.aura.entry.api.EntrySort
import com.aura.entry.api.EntrySortField
import com.aura.entry.api.EntrySource
import com.aura.entry.api.EntryStatus
import com.aura.entry.api.UpdateEntry
import com.aura.security.SecurityConfig
import com.aura.security.WithMockAuraUser
import com.aura.shared.NotFoundException
import com.aura.shared.ValidationFailedException
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneOffset

/** Slice-тест записей /api/v1/entries: маппинг контракта, 401/404/422, сортировка, календарь. */
@WebMvcTest(EntryController::class)
@Import(SecurityConfig::class)
class EntryControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
) {

    @MockkBean
    lateinit var entries: EntriesPort

    @MockkBean(relaxed = true)
    lateinit var accessTokenVerifier: AccessTokenVerifier

    private val details = EntryDetails(
        id = 1024,
        userId = 7,
        entryDate = LocalDate.of(2026, 10, 3),
        recordedAt = Instant.parse("2026-10-03T20:15:00Z"),
        source = EntrySource.WEB,
        status = EntryStatus.COMPLETED,
        emotions = listOf(EntryEmotion(12, "Тревога", 4, 2)),
        factors = listOf(EntryFactor(4, "Работа", 3)),
        events = listOf(EntryEvent(9, "Ссора с коллегой")),
        metrics = listOf(EntryMetric(2, "Энергия", BigDecimal(4))),
        note = "Тяжёлый день на работе",
    )

    @Test
    @WithMockAuraUser(userId = 7)
    fun createEntry_mapsCommandAndReturns201() {
        every { entries.create(7, any()) } returns details

        mockMvc.post("/api/v1/entries") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                CreateEntryRequest(
                    status = ApiEntryStatus.COMPLETED,
                    source = null,
                    emotions = listOf(EntryEmotionInput(12, 4, 2)),
                    factors = listOf(EntryFactorInput(4, 3)),
                    events = listOf(EntryEventInput(9)),
                    metrics = listOf(EntryMetricInput(2, BigDecimal(4))),
                    note = "Тяжёлый день на работе",
                ),
            )
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { value(1024) }
            jsonPath("$.entryDate") { value("2026-10-03") }
            jsonPath("$.recordedAt") { value("2026-10-03T20:15:00Z") }
            jsonPath("$.status") { value("COMPLETED") }
            jsonPath("$.source") { value("WEB") }
            jsonPath("$.emotions[0].name") { value("Тревога") }
            jsonPath("$.emotions[0].intensity") { value(4) }
            jsonPath("$.factors[0].name") { value("Работа") }
            jsonPath("$.events[0].name") { value("Ссора с коллегой") }
            jsonPath("$.metrics[0].name") { value("Энергия") }
            jsonPath("$.note") { value("Тяжёлый день на работе") }
        }

        verify {
            entries.create(
                7,
                withArg { command: CreateEntry ->
                    assertThat(command.status).isEqualTo(EntryStatus.COMPLETED)
                    // source не передан — сервер подставляет WEB
                    assertThat(command.source).isEqualTo(EntrySource.WEB)
                    assertThat(command.emotions).hasSize(1)
                    assertThat(command.emotions.first().emotionId).isEqualTo(12)
                    assertThat(command.factors.first().intensity).isEqualTo(3)
                    assertThat(command.events.first().eventId).isEqualTo(9)
                    assertThat(command.metrics.first().value).isEqualByComparingTo("4")
                },
            )
        }
    }

    @Test
    @WithMockAuraUser(userId = 7)
    fun getEntry_returnsFullEntry() {
        every { entries.get(7, 1024) } returns details

        mockMvc.get("/api/v1/entries/1024").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(1024) }
            jsonPath("$.userId") { value(7) }
            jsonPath("$.emotions[0].name") { value("Тревога") }
        }
    }

    @Test
    fun entries_requireAuthentication() {
        mockMvc.get("/api/v1/entries").andExpect { status { isUnauthorized() } }
        mockMvc.post("/api/v1/entries") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    @WithMockAuraUser(userId = 7)
    fun getEntry_otherUsersEntry_is404Problem() {
        every { entries.get(7, 99) } throws NotFoundException("Запись не найдена")

        mockMvc.get("/api/v1/entries/99").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("NOT_FOUND") }
        }
    }

    @Test
    @WithMockAuraUser(userId = 7)
    fun updateEntry_mapsPartialPatch() {
        every { entries.update(7, 1024, any()) } returns details.copy(status = EntryStatus.COMPLETED)

        mockMvc.patch("/api/v1/entries/1024") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                UpdateEntryRequest(
                    status = ApiEntryStatus.COMPLETED,
                    note = "обновлено",
                ),
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("COMPLETED") }
        }

        verify {
            entries.update(
                7,
                1024,
                withArg { command: UpdateEntry ->
                    assertThat(command.status).isEqualTo(EntryStatus.COMPLETED)
                    assertThat(command.note).isEqualTo("обновлено")
                    // не переданные поля — null: менять нечего
                    assertThat(command.emotions).isNull()
                    assertThat(command.source).isNull()
                },
            )
        }
    }

    @Test
    @WithMockAuraUser(userId = 7)
    fun deleteEntry_returns204() {
        every { entries.delete(7, 1024) } returns Unit

        mockMvc.delete("/api/v1/entries/1024").andExpect { status { isNoContent() } }
    }

    @Test
    @WithMockAuraUser(userId = 7)
    fun listEntries_mapsDateFilterAndSort() {
        every { entries.list(7, any(), 0, 20, any()) } returns DomainEntryPage(listOf(details), 0, 20, 1, 1)

        mockMvc.get("/api/v1/entries?date=2026-10-03&status=COMPLETED").andExpect {
            status { isOk() }
            jsonPath("$.items[0].id") { value(1024) }
            jsonPath("$.page") { value(0) }
            jsonPath("$.size") { value(20) }
            jsonPath("$.totalElements") { value(1) }
        }

        verify {
            entries.list(
                7,
                withArg { filter: EntryFilter ->
                    assertThat(filter.date).isEqualTo(LocalDate.of(2026, 10, 3))
                    assertThat(filter.from).isNull()
                    assertThat(filter.status).isEqualTo(EntryStatus.COMPLETED)
                },
                0,
                20,
                withArg { sort: EntrySort? ->
                    // default спеки recordedAt,desc распарсен в модель
                    assertThat(sort).isEqualTo(EntrySort(EntrySortField.RECORDED_AT, descending = true))
                },
            )
        }
    }

    @Test
    @WithMockAuraUser(userId = 7)
    fun listEntries_invalidSort_is422() {
        every { entries.list(any(), any(), any(), any(), any()) } throws
            ValidationFailedException("sort", "поддерживаются поля recordedAt и entryDate")

        mockMvc.get("/api/v1/entries?sort=hacker,desc").andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors[0].field") { value("sort") }
        }
    }

    @Test
    @WithMockAuraUser(userId = 7)
    fun calendar_returnsMonthDays() {
        every { entries.calendar(7, YearMonth.of(2026, 10)) } returns
            EntryCalendar(YearMonth.of(2026, 10), listOf(EntryCalendarDay(LocalDate.of(2026, 10, 3), 2, true)))

        mockMvc.get("/api/v1/entries/calendar?month=2026-10").andExpect {
            status { isOk() }
            jsonPath("$.month") { value("2026-10") }
            jsonPath("$.days[0].date") { value("2026-10-03") }
            jsonPath("$.days[0].entriesCount") { value(2) }
            jsonPath("$.days[0].hasCompleted") { value(true) }
        }
    }

    @Test
    @WithMockAuraUser(userId = 7)
    fun calendar_invalidMonth_is422() {
        mockMvc.get("/api/v1/entries/calendar?month=october").andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors[0].field") { value("month") }
        }
    }
}

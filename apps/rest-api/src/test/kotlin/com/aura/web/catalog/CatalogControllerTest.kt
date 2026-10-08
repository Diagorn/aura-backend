package com.aura.web.catalog

import com.aura.api.model.CreateEmotionRequest
import com.aura.api.model.CreateMetricRequest
import com.aura.api.model.UpdateEmotionRequest
import com.aura.auth.api.AccessTokenVerifier
import com.aura.catalog.api.CatalogItemNameAlreadyExistsException
import com.aura.catalog.api.CreateEmotion
import com.aura.catalog.api.CreateTrackedMetric
import com.aura.catalog.api.Emotion
import com.aura.catalog.api.EmotionsPort
import com.aura.catalog.api.EventsPort
import com.aura.catalog.api.FactorsPort
import com.aura.catalog.api.MetricsPort
import com.aura.catalog.api.TrackedMetric
import com.aura.catalog.api.UpdateEmotion
import com.aura.security.SecurityConfig
import com.aura.security.WithMockAuraUser
import com.aura.shared.NotFoundException
import com.ninjasquad.springmockk.MockkBean
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.slot
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

/** Slice-тест справочников /api/v1/catalog: маппинг контракта, 401/404/409/422. */
@WebMvcTest(CatalogController::class)
@Import(SecurityConfig::class)
class CatalogControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
) {

    @MockkBean
    lateinit var emotions: EmotionsPort

    @MockkBean
    lateinit var factors: FactorsPort

    @MockkBean
    lateinit var events: EventsPort

    @MockkBean
    lateinit var metrics: MetricsPort

    @MockkBean(relaxed = true)
    lateinit var accessTokenVerifier: AccessTokenVerifier

    private val personal = Emotion(
        id = 12,
        name = "Тревога",
        color = 15461355,
        icon = "storm",
        isActive = true,
        sortOrder = 3,
        isSystem = false,
    )

    private val system = Emotion(
        id = 1,
        name = "Радость",
        color = 16776960,
        icon = null,
        isActive = true,
        sortOrder = 0,
        isSystem = true,
    )

    @Test
    @WithMockAuraUser(userId = 7)
    fun listEmotions_returnsItemsWithSystemFlag() {
        every { emotions.list(7, false) } returns listOf(system, personal)

        mockMvc.get("/api/v1/catalog/emotions").andExpect {
            status { isOk() }
            jsonPath("$[0].id") { value(1) }
            jsonPath("$[0].name") { value("Радость") }
            jsonPath("$[0].system") { value(true) }
            jsonPath("$[0].color") { value(16776960) }
            jsonPath("$[1].id") { value(12) }
            jsonPath("$[1].system") { value(false) }
            jsonPath("$[1].icon") { value("storm") }
        }
    }

    @Test
    @WithMockAuraUser(userId = 7)
    fun createEmotion_mapsCommandAndReturns201() {
        val created = personal.copy(id = 55, name = "Спокойствие")
        every { emotions.create(7, any()) } returns created

        mockMvc.post("/api/v1/catalog/emotions") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                CreateEmotionRequest(name = "Спокойствие", icon = "calm"),
            )
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { value(55) }
            jsonPath("$.system") { value(false) }
        }

        verify {
            emotions.create(
                7,
                withArg { command: CreateEmotion ->
                    assertThat(command.name).isEqualTo("Спокойствие")
                    assertThat(command.icon).isEqualTo("calm")
                    assertThat(command.color).isNull()
                    assertThat(command.sortOrder).isNull()
                },
            )
        }
        confirmVerified(emotions)
    }

    @Test
    @WithMockAuraUser(userId = 7)
    fun createEmotion_withBlankName_returns422() {
        mockMvc.post("/api/v1/catalog/emotions") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                CreateEmotionRequest(name = ""),
            )
        }.andExpect {
            status { isUnprocessableEntity() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors[0].field") { value("name") }
        }
    }

    @Test
    @WithMockAuraUser(userId = 7)
    fun createEmotion_withTooLongName_returns422() {
        mockMvc.post("/api/v1/catalog/emotions") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                CreateEmotionRequest(name = "а".repeat(101)),
            )
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
        }
    }

    @Test
    @WithMockAuraUser(userId = 7)
    fun createEmotion_withDuplicateName_returns409() {
        every { emotions.create(7, any()) } throws CatalogItemNameAlreadyExistsException()

        mockMvc.post("/api/v1/catalog/emotions") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                CreateEmotionRequest(name = "Радость"),
            )
        }.andExpect {
            status { isConflict() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("NAME_ALREADY_EXISTS") }
        }
    }

    @Test
    @WithMockAuraUser(userId = 7)
    fun updateEmotion_foreignOrMissing_returns404() {
        every { emotions.update(7, 99, any(), false) } throws NotFoundException("Эмоция не найдена")

        mockMvc.patch("/api/v1/catalog/emotions/99") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(UpdateEmotionRequest(name = "Новое имя"))
        }.andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("NOT_FOUND") }
        }
    }

    @Test
    @WithMockAuraUser(userId = 7, roles = ["ADMIN"])
    fun updateEmotion_asAdmin_passesIsAdminFlag() {
        every { emotions.update(7, 1, any(), true) } returns personal.copy(id = 1, isSystem = true)

        mockMvc.patch("/api/v1/catalog/emotions/1") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(UpdateEmotionRequest(name = "Радость (обновлена)"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.system") { value(true) }
        }

        verify {
            emotions.update(
                7,
                1,
                withArg { command: UpdateEmotion -> assertThat(command.name).isEqualTo("Радость (обновлена)") },
                true,
            )
        }
        confirmVerified(emotions)
    }

    @Test
    @WithMockAuraUser(userId = 7)
    fun deleteEmotion_returns204() {
        every { emotions.delete(7, 12) } returns Unit

        mockMvc.delete("/api/v1/catalog/emotions/12").andExpect {
            status { isNoContent() }
        }

        verify(exactly = 1) { emotions.delete(7, 12) }
    }

    @Test
    @WithMockAuraUser(userId = 7)
    fun listMetrics_returnsScaleFields() {
        val metric = TrackedMetric(
            id = 2,
            name = "Энергия",
            minValue = BigDecimal("1"),
            maxValue = BigDecimal("5"),
            unit = null,
            isActive = true,
            sortOrder = 0,
            isSystem = true,
        )
        every { metrics.list(7, false) } returns listOf(metric)

        mockMvc.get("/api/v1/catalog/metrics").andExpect {
            status { isOk() }
            jsonPath("$[0].name") { value("Энергия") }
            jsonPath("$[0].minValue") { value(1) }
            jsonPath("$[0].maxValue") { value(5) }
            jsonPath("$[0].system") { value(true) }
        }
    }

    @Test
    @WithMockAuraUser(userId = 7)
    fun createMetric_mapsScaleCommand() {
        val metric = TrackedMetric(
            id = 9,
            name = "Тревожность",
            minValue = BigDecimal("0"),
            maxValue = BigDecimal("10"),
            unit = "балл",
            isActive = true,
            sortOrder = 1,
            isSystem = false,
        )
        every { metrics.create(7, any()) } returns metric
        val command = slot<CreateTrackedMetric>()
        every { metrics.create(7, capture(command)) } returns metric

        mockMvc.post("/api/v1/catalog/metrics") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                CreateMetricRequest(
                    name = "Тревожность",
                    minValue = BigDecimal("0"),
                    maxValue = BigDecimal("10"),
                    unit = "балл",
                ),
            )
        }.andExpect {
            status { isCreated() }
            jsonPath("$.minValue") { value(0) }
            jsonPath("$.maxValue") { value(10) }
        }

        assertThat(command.captured.minValue).isEqualByComparingTo("0")
        assertThat(command.captured.maxValue).isEqualByComparingTo("10")
        assertThat(command.captured.unit).isEqualTo("балл")
    }

    @Test
    fun catalog_withoutToken_returns401Problem() {
        mockMvc.get("/api/v1/catalog/emotions").andExpect {
            status { isUnauthorized() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
    }
}

package com.aura.integration

import com.aura.entry.api.EntryDeleted
import com.aura.entry.api.EntryEventsPort
import com.aura.entry.api.EntryCompleted
import com.aura.entry.api.EntryUpdated
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.testcontainers.containers.PostgreSQLContainer
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.sql.DataSource

/**
 * Интеграционные сценарии этапа 3 (см. docs/roadmap.md «Готово, когда»): полный флоу
 * чек-ина через API, DRAFT-цикл, суточный лимит, вычисление entry_date по таймзоне,
 * «быстрый возврат к дате», календарь месяца, доставка фактов Entry*.
 * Настоящий Postgres в Testcontainers; Liquibase накатывает master-чейнджлог.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(EntryFlowIntegrationTest.PostgresConfig::class, EntryFlowIntegrationTest.EntryFactsCollector::class)
@TestMethodOrder(MethodOrderer.Random::class)
class EntryFlowIntegrationTest(
    @Autowired private val restTestClient: RestTestClient,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val factsCollector: EntryFactsCollector,
) {

    @TestConfiguration(proxyBeanMethods = false)
    class PostgresConfig {

        @Bean
        @ServiceConnection
        fun postgres(): PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17-alpine")
            .withInitScript("init-test.sql")
    }

    /** Потребитель фактов entry — проверяет доставку через EntryEventsDispatcher. */
    @TestConfiguration(proxyBeanMethods = false)
    class EntryFactsCollector {

        val completed = mutableListOf<EntryCompleted>()
        val updated = mutableListOf<EntryUpdated>()
        val deleted = mutableListOf<EntryDeleted>()

        @Bean
        fun entryEventsPort(): EntryEventsPort = object : EntryEventsPort {
            override fun entryCompleted(event: EntryCompleted) {
                completed += event
            }

            override fun entryUpdated(event: EntryUpdated) {
                updated += event
            }

            override fun entryDeleted(event: EntryDeleted) {
                deleted += event
            }
        }

        fun reset() {
            completed.clear()
            updated.clear()
            deleted.clear()
        }
    }

    @Test
    fun `full check-in flow through api`() {
        factsCollector.reset()
        val token = register(uniqueEmail("checkin")).token()
        val emotionId = createPersonalEmotion(token, "Уверенность")
        val factorId = createPersonalFactor(token, "Прогулка")
        val eventId = createPersonalEvent(token, "Хороший разговор")
        val metricId = firstSystemMetricId(token)

        val created = postJson(
            "/api/v1/entries",
            mapOf(
                "status" to "COMPLETED",
                "emotions" to listOf(mapOf("emotionId" to emotionId, "intensity" to 4, "influence" to 3)),
                "factors" to listOf(mapOf("factorId" to factorId, "intensity" to 5)),
                "events" to listOf(mapOf("eventId" to eventId)),
                "metrics" to listOf(mapOf("metricId" to metricId, "value" to 4)),
                "note" to "День сложился",
            ),
            token,
            expected = 201,
        )
        val entryId = created.int("id")
        // таймзона по умолчанию — Europe/Moscow (см. AuthServiceImpl.DEFAULT_TIMEZONE)
        val expectedToday = LocalDate.ofInstant(Instant.now(), ZoneId.of("Europe/Moscow"))
        assertThatText(created, "status").isEqualTo("COMPLETED")
        assertThatText(created, "entryDate").isEqualTo(expectedToday.toString())
        assertThatText(created, "emotions[0].name").isEqualTo("Уверенность")
        assertThatText(created, "factors[0].name").isEqualTo("Прогулка")
        assertThatText(created, "events[0].name").isEqualTo("Хороший разговор")
        assertThatText(created, "metrics[0].name").isEqualTo(createPersonalMetricName(token, metricId))
        assertThatText(created, "note").isEqualTo("День сложился")

        // быстрый возврат к дате — один запрос; фильтры status/source сужают выборку
        val byDate = getJson("/api/v1/entries?date=$expectedToday", token)
        assertThat(byDate.at("/items").size()).isEqualTo(1)
        assertThatText(byDate, "items[0].id").isEqualTo(entryId.toString())
        assertThat(byDate.at("/totalElements").asInt()).isEqualTo(1)
        assertThat(getJson("/api/v1/entries?date=$expectedToday&status=DRAFT", token).at("/items").size()).isZero()
        assertThat(getJson("/api/v1/entries?date=$expectedToday&source=WEB", token).at("/items").size()).isEqualTo(1)
        assertThat(getJson("/api/v1/entries?date=$expectedToday&source=TELEGRAM", token).at("/items").size()).isZero()

        // без фильтров даты — все записи пользователя, включая чужие дни
        val all = getJson("/api/v1/entries", token)
        assertThat(all.at("/totalElements").asInt()).isGreaterThanOrEqualTo(1)
        assertThat(all.at("/items").size()).isGreaterThanOrEqualTo(1)

        // период from+to включительно: обе границы — ожидаемая запись за день создания
        val period = getJson("/api/v1/entries?from=$expectedToday&to=$expectedToday", token)
        assertThat(period.at("/items").size()).isEqualTo(1)
        assertThatText(period, "items[0].id").isEqualTo(entryId.toString())
        // диапазон, в который день создания не попадает, пуст
        val outside = getJson("/api/v1/entries?from=2020-01-01&to=2020-01-31", token)
        assertThat(outside.at("/items").size()).isZero()

        // событие завершения доставлено
        assertThat(factsCollector.completed).hasSize(1)
        assertThat(factsCollector.completed.first().entryId).isEqualTo(entryId.toLong())
        assertThat(factsCollector.completed.first().entryDate).isEqualTo(expectedToday)

        // удаление: 204, затем 404; факт EntryDeleted доставлен
        expectStatus("DELETE", "/api/v1/entries/$entryId", null, token, 204)
        expectProblem(
            "GET",
            "/api/v1/entries/$entryId",
            ExpectedProblem(status = 404, code = "NOT_FOUND"),
            bearer = token,
        )
        assertThat(factsCollector.deleted).hasSize(1)
        assertThat(factsCollector.deleted.first().entryId).isEqualTo(entryId.toLong())
    }

    @Test
    fun `draft cycle with partial patches`() {
        factsCollector.reset()
        val token = register(uniqueEmail("draft")).token()
        val emotionId = createPersonalEmotion(token, "Спокойствие черновик")

        // DRAFT может быть совсем пустым
        val draft = postJson("/api/v1/entries", mapOf("status" to "DRAFT"), token, expected = 201)
        val draftId = draft.int("id")
        assertThatText(draft, "status").isEqualTo("DRAFT")
        assertThat(draft.at("/emotions").size()).isZero()
        assertThat(factsCollector.completed).isEmpty()

        // частичное сохранение: добавили эмоцию, статус не трогали
        patchJson(
            "/api/v1/entries/$draftId",
            mapOf("emotions" to listOf(mapOf("emotionId" to emotionId, "intensity" to 2, "influence" to 2))),
            token,
        )
        assertThat(factsCollector.updated).hasSize(1)

        // дозаполнение до COMPLETED
        val completed = patchJson("/api/v1/entries/$draftId", mapOf("status" to "COMPLETED"), token)
        assertThatText(completed, "status").isEqualTo("COMPLETED")
        assertThat(factsCollector.completed).hasSize(1)

        // COMPLETED -> DRAFT запрещён
        expectProblem(
            "PATCH",
            "/api/v1/entries/$draftId",
            ExpectedProblem(status = 422, code = "VALIDATION_FAILED"),
            body = mapOf("status" to "DRAFT"),
            bearer = token,
        )

        // COMPLETED без эмоций нельзя и через PATCH, убрав коллекцию
        expectProblem(
            "PATCH",
            "/api/v1/entries/$draftId",
            ExpectedProblem(status = 422, code = "VALIDATION_FAILED"),
            body = mapOf("emotions" to emptyList<Map<String, Any>>()),
            bearer = token,
        )
    }

    @Test
    fun `daily limit counts drafts and returns 409`() {
        val token = register(uniqueEmail("limit")).token()
        repeat(5) { index ->
            postJson("/api/v1/entries", mapOf("status" to "DRAFT", "note" to "№$index"), token, expected = 201)
        }
        expectProblem(
            "POST",
            "/api/v1/entries",
            ExpectedProblem(status = 409, code = "ENTRY_DAILY_LIMIT_EXCEEDED"),
            body = mapOf("status" to "DRAFT"),
            bearer = token,
        )

        // чужие записи не мешают: у другого пользователя лимит свой
        val other = register(uniqueEmail("limit-b")).token()
        postJson("/api/v1/entries", mapOf("status" to "DRAFT"), other, expected = 201)
    }

    @Test
    fun `entry composition validation`() {
        val token = register(uniqueEmail("valid")).token()
        val emotionId = createPersonalEmotion(token, "Радость валидация")
        val foreignToken = register(uniqueEmail("valid-b")).token()
        val foreignEmotionId = createPersonalEmotion(foreignToken, "Чужая эмоция")
        val metricId = firstSystemMetricId(token)

        // COMPLETED без эмоций — 422
        expectProblem(
            "POST",
            "/api/v1/entries",
            ExpectedProblem(status = 422, code = "VALIDATION_FAILED"),
            body = mapOf("status" to "COMPLETED"),
            bearer = token,
        )

        // интенсивность вне 1..5 — 422 (bean validation спеки)
        expectProblem(
            "POST",
            "/api/v1/entries",
            ExpectedProblem(status = 422, code = "VALIDATION_FAILED"),
            body = mapOf(
                "status" to "COMPLETED",
                "emotions" to listOf(mapOf("emotionId" to emotionId, "intensity" to 6, "influence" to 1)),
            ),
            bearer = token,
        )

        // значение метрики вне шкалы — 422 по полю metrics
        expectProblem(
            "POST",
            "/api/v1/entries",
            ExpectedProblem(status = 422, code = "VALIDATION_FAILED"),
            body = mapOf(
                "status" to "DRAFT",
                "metrics" to listOf(mapOf("metricId" to metricId, "value" to 9)),
            ),
            bearer = token,
        )

        // несуществующая эмоция — 422
        expectProblem(
            "POST",
            "/api/v1/entries",
            ExpectedProblem(status = 422, code = "VALIDATION_FAILED"),
            body = mapOf(
                "status" to "DRAFT",
                "emotions" to listOf(mapOf("emotionId" to 999999, "intensity" to 3, "influence" to 3)),
            ),
            bearer = token,
        )

        // эмоция другого пользователя — тоже 422 (чужой ресурс не раскрываем)
        expectProblem(
            "POST",
            "/api/v1/entries",
            ExpectedProblem(status = 422, code = "VALIDATION_FAILED"),
            body = mapOf(
                "status" to "DRAFT",
                "emotions" to listOf(mapOf("emotionId" to foreignEmotionId, "intensity" to 3, "influence" to 3)),
            ),
            bearer = token,
        )

        // взаимоисключающие фильтры — 422
        expectProblem(
            "GET",
            "/api/v1/entries?date=2026-10-03&from=2026-10-01&to=2026-10-02",
            ExpectedProblem(status = 422, code = "VALIDATION_FAILED"),
            bearer = token,
        )
    }

    @Test
    fun `entry date follows user timezone`() {
        val token = register(uniqueEmail("tz")).token()
        patchJson("/api/v1/me", mapOf("timezone" to "Asia/Kamchatka"), token)

        val created = postJson("/api/v1/entries", mapOf("status" to "DRAFT"), token, expected = 201)
        val expectedDate = LocalDate.ofInstant(Instant.now(), ZoneId.of("Asia/Kamchatka"))
        assertThatText(created, "entryDate").isEqualTo(expectedDate.toString())

        // календарь текущего месяца по таймзоне пользователя содержит день записи
        val calendar = getJson("/api/v1/entries/calendar", token)
        assertThatText(calendar, "month").isEqualTo(YearMonth.from(expectedDate).toString())
        assertThat(calendar.at("/days").size()).isEqualTo(1)
        assertThatText(calendar, "days[0].date").isEqualTo(expectedDate.toString())
        assertThatText(calendar, "days[0].entriesCount").isEqualTo("1")
        assertThatText(calendar, "days[0].hasCompleted").isEqualTo("false")
    }

    @Test
    fun `calendar empty month has no days and patch replaces collections`() {
        factsCollector.reset()
        val token = register(uniqueEmail("replace")).token()
        val firstEmotion = createPersonalEmotion(token, "Первое")
        val secondEmotion = createPersonalEmotion(token, "Второе")

        // пустой месяц — days пуст (только дни с записями)
        val empty = getJson("/api/v1/entries/calendar?month=2020-01", token)
        assertThat(empty.at("/days").size()).isZero()
        assertThatText(empty, "month").isEqualTo("2020-01")

        val created = postJson(
            "/api/v1/entries",
            mapOf(
                "status" to "COMPLETED",
                "emotions" to listOf(mapOf("emotionId" to firstEmotion, "intensity" to 3, "influence" to 3)),
            ),
            token,
            expected = 201,
        )
        val entryId = created.int("id")

        // переданная коллекция заменяется целиком
        val replaced = patchJson(
            "/api/v1/entries/$entryId",
            mapOf("emotions" to listOf(mapOf("emotionId" to secondEmotion, "intensity" to 5, "influence" to 1))),
            token,
        )
        assertThat(replaced.at("/emotions").size()).isEqualTo(1)
        assertThatText(replaced, "emotions[0].emotionId").isEqualTo(secondEmotion.toString())
        assertThatText(replaced, "emotions[0].name").isEqualTo("Второе")

        // note очищается пустой строкой — в ответе приходит null
        val cleared = patchJson("/api/v1/entries/$entryId", mapOf("note" to ""), token)
        assertThat(cleared.at("/note").isNull).isTrue()

        // чужая запись — 404 и на чтение, и на удаление
        val foreign = register(uniqueEmail("replace-b")).token()
        expectProblem(
            "GET",
            "/api/v1/entries/$entryId",
            ExpectedProblem(status = 404, code = "NOT_FOUND"),
            bearer = foreign,
        )
        expectStatus("DELETE", "/api/v1/entries/$entryId", null, foreign, 404)
    }

    // ---------- helpers ----------

    private fun createPersonalEmotion(token: String, name: String): Long =
        postJson("/api/v1/catalog/emotions", mapOf("name" to name), token, expected = 201).int("id").toLong()

    private fun createPersonalFactor(token: String, name: String): Long =
        postJson("/api/v1/catalog/factors", mapOf("name" to name), token, expected = 201).int("id").toLong()

    private fun createPersonalEvent(token: String, name: String): Long =
        postJson("/api/v1/catalog/events", mapOf("name" to name), token, expected = 201).int("id").toLong()

    private fun createPersonalMetricName(token: String, metricId: Long): String {
        val metrics = getJson("/api/v1/catalog/metrics", token)
        for (i in 0 until metrics.size()) {
            if (metrics[i].int("id") == metricId.toInt()) return metrics[i].text("name")
        }
        error("Метрика $metricId не найдена")
    }

    private fun firstSystemMetricId(token: String): Long {
        val metrics = getJson("/api/v1/catalog/metrics", token)
        check(metrics.size() > 0) { "Системные метрики должны быть в seed" }
        return metrics[0].int("id").toLong()
    }

    private fun register(email: String): JsonNode {
        val body = mapOf("email" to email, "password" to DEFAULT_PASSWORD)
        val response = restTestClient.post().uri("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus().isCreated
            .expectBody(ByteArray::class.java)
            .returnResult()
            .responseBody
        return objectMapper.readTree(response)
    }

    private fun getJson(uri: String, bearer: String?): JsonNode {
        val response = exchange("GET", uri, null, bearer, 200)
        return objectMapper.readTree(response)
    }

    private fun postJson(uri: String, body: Map<String, *>?, bearer: String? = null, expected: Int = 200): JsonNode {
        val response = exchange("POST", uri, body, bearer, expected)
        return objectMapper.readTree(response)
    }

    private fun patchJson(uri: String, body: Map<String, *>, bearer: String): JsonNode {
        val response = exchange("PATCH", uri, body, bearer, 200)
        return objectMapper.readTree(response)
    }

    private fun expectStatus(method: String, uri: String, body: Map<String, *>?, bearer: String?, expected: Int) {
        exchange(method, uri, body, bearer, expected)
    }

    private data class ExpectedProblem(
        val status: Int,
        val code: String,
    )

    private fun expectProblem(
        method: String,
        uri: String,
        expected: ExpectedProblem,
        body: Map<String, *>? = null,
        bearer: String? = null,
    ) {
        val responseBody = request(method, uri, body, bearer)
            .expectStatus().isEqualTo(expected.status)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody(ByteArray::class.java)
            .returnResult()
            .responseBody
        val problem = objectMapper.readTree(requireNotNull(responseBody) { "Пустой problem: $method $uri" })
        check(problem.text("code") == expected.code) {
            "Ожидался code=${expected.code}, получен: $problem"
        }
    }

    private fun request(
        method: String,
        uri: String,
        body: Map<String, *>?,
        bearer: String?,
    ): RestTestClient.ResponseSpec {
        if (method == "GET" || method == "DELETE") {
            val spec: RestTestClient.RequestHeadersSpec<*> = when (method) {
                "GET" -> restTestClient.get().uri(uri)
                else -> restTestClient.delete().uri(uri)
            }
            val authed = bearer?.let { spec.header(HttpHeaders.AUTHORIZATION, "Bearer $it") } ?: spec
            return authed.exchange()
        }
        val bodySpec: RestTestClient.RequestBodySpec = when (method) {
            "POST" -> restTestClient.post().uri(uri)
            "PATCH" -> restTestClient.patch().uri(uri)
            else -> error("Неизвестный метод: $method")
        }
        bearer?.let { bodySpec.header(HttpHeaders.AUTHORIZATION, "Bearer $it") }
        return if (body != null) {
            bodySpec.contentType(MediaType.APPLICATION_JSON).body(body).exchange()
        } else {
            bodySpec.exchange()
        }
    }

    private fun exchange(method: String, uri: String, body: Map<String, *>?, bearer: String?, expected: Int): ByteArray {
        return request(method, uri, body, bearer)
            .expectStatus().isEqualTo(expected)
            .expectBody(ByteArray::class.java)
            .returnResult()
            .responseBody
            ?: ByteArray(0)
    }

    private fun JsonNode.token(): String = text("accessToken")

    private fun JsonNode.text(field: String): String {
        val value = pointer(field)
        check(!value.isMissingNode && !value.isNull) { "Поле '$field' отсутствует в ответе: $this" }
        return value.asText()
    }

    /** JSON-указатель из пути с точками и индексами: "emotions[0].name" -> "/emotions/0/name". */
    private fun JsonNode.pointer(field: String) = at(
        "/" + field.replace('.', '/').replace('[', '/').replace("]", ""),
    )

    private fun JsonNode.int(field: String): Int = pointer(field).asInt()

    private fun assertThatText(node: JsonNode, field: String) = org.assertj.core.api.Assertions
        .assertThat(node.pointer(field).asText())

    private fun uniqueEmail(prefix: String): String = "$prefix-${System.nanoTime()}-${(0..999).random()}@integration.local"

    companion object {
        private const val DEFAULT_PASSWORD = "secret-password-1"
    }
}

package com.aura.integration

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

/**
 * Интеграционный сценарий этапа 2.1 (см. docs/roadmap.md «Готово, когда»):
 * персональный CRUD по всем четырём справочникам, уникальность имён, изоляция
 * пользователей (чужое — 404), валидации и RFC 9457 ошибки.
 * Настоящий Postgres в Testcontainers; Liquibase накатывает master-чейнджлог.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(CatalogFlowIntegrationTest.PostgresConfig::class)
@TestMethodOrder(MethodOrderer.Random::class)
class CatalogFlowIntegrationTest(
    @Autowired private val restTestClient: RestTestClient,
    @Autowired private val objectMapper: ObjectMapper,
) {

    @TestConfiguration(proxyBeanMethods = false)
    class PostgresConfig {

        @Bean
        @ServiceConnection
        fun postgres(): PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17-alpine")
            .withInitScript("init-test.sql")
    }

    @Test
    fun `emotions crud with per-user isolation`() {
        val token = register(uniqueEmail("emo-a")).token()

        // пустой список у нового пользователя (системных пресетов в 2.1 ещё нет)
        val empty = getJson("/api/v1/catalog/emotions", token)
        assertThat(empty.size()).isZero()

        // создание: sortOrder присваивается сервером, новые элементы активны
        val first = postJson(
            "/api/v1/catalog/emotions",
            mapOf("name" to "Спокойствие", "color" to 65280, "icon" to "calm"),
            token,
            expected = 201,
        )
        val firstId = first.int("id")
        assertThatText(first, "name").isEqualTo("Спокойствие")
        assertThatText(first, "system").isEqualTo("false")
        assertThat(first.bool("isActive")).isTrue()
        assertThat(first.int("sortOrder")).isZero()

        // дубль имени у того же пользователя — 409
        expectProblem(
            "POST",
            "/api/v1/catalog/emotions",
            ExpectedProblem(status = 409, code = "NAME_ALREADY_EXISTS"),
            body = mapOf("name" to "Спокойствие"),
            bearer = token,
        )

        // вторая эмоция получает следующий sortOrder
        val second = postJson(
            "/api/v1/catalog/emotions",
            mapOf("name" to "Тревога"),
            token,
            expected = 201,
        )
        val secondId = second.int("id")
        assertThat(second.int("sortOrder")).isEqualTo(1)

        // PATCH-семантика: переименование + деактивация, остальные поля на месте
        val patched = patchJson(
            "/api/v1/catalog/emotions/$secondId",
            mapOf("name" to "Тревожность", "isActive" to false),
            token,
        )
        assertThatText(patched, "name").isEqualTo("Тревожность")
        assertThat(patched.bool("isActive")).isFalse()

        // по умолчанию деактивированные скрыты; includeInactive=true возвращает обе
        assertThat(getJson("/api/v1/catalog/emotions", token).size()).isEqualTo(1)
        val withInactive = getJson("/api/v1/catalog/emotions?includeInactive=true", token)
        assertThat(withInactive.size()).isEqualTo(2)

        // удаление -> 204, после чего элемент недоступен (404, существование не раскрываем)
        expectStatus("DELETE", "/api/v1/catalog/emotions/$firstId", null, token, 204)
        expectProblem(
            "PATCH",
            "/api/v1/catalog/emotions/$firstId",
            ExpectedProblem(status = 404, code = "NOT_FOUND"),
            body = mapOf("name" to "Призрак"),
            bearer = token,
        )

        // изоляция пользователей: чужая эмоция не видна и не правится (404)
        val otherToken = register(uniqueEmail("emo-b")).token()
        assertThat(getJson("/api/v1/catalog/emotions", otherToken).size()).isZero()
        expectProblem(
            "PATCH",
            "/api/v1/catalog/emotions/$secondId",
            ExpectedProblem(status = 404, code = "NOT_FOUND"),
            body = mapOf("name" to "Чужое"),
            bearer = otherToken,
        )
        expectStatus("DELETE", "/api/v1/catalog/emotions/$secondId", null, otherToken, 404)

        // без токена — 401 problem+json
        expectProblem(
            "GET",
            "/api/v1/catalog/emotions",
            ExpectedProblem(status = 401, code = "UNAUTHORIZED"),
        )
    }

    @Test
    fun `metrics defaults scale validation and crud`() {
        val token = register(uniqueEmail("met")).token()

        // без границ шкалы — значения по умолчанию 1 и 5
        val metric = postJson("/api/v1/catalog/metrics", mapOf("name" to "Энергия"), token, expected = 201)
        val metricId = metric.int("id")
        assertThatText(metric, "minValue").isEqualTo("1")
        assertThatText(metric, "maxValue").isEqualTo("5")

        // своя шкала
        val custom = postJson(
            "/api/v1/catalog/metrics",
            mapOf("name" to "Тревожность", "minValue" to 0, "maxValue" to 10, "unit" to "балл"),
            token,
            expected = 201,
        )
        assertThatText(custom, "minValue").isEqualTo("0")
        assertThatText(custom, "unit").isEqualTo("балл")

        // некорректная шкала при создании — 422 с деталью по полю
        expectProblem(
            "POST",
            "/api/v1/catalog/metrics",
            ExpectedProblem(status = 422, code = "VALIDATION_FAILED"),
            body = mapOf("name" to "Либидо", "minValue" to 5, "maxValue" to 1),
            bearer = token,
        )

        // при обновлении пришла только одна граница — сверяем с текущей второй
        expectProblem(
            "PATCH",
            "/api/v1/catalog/metrics/$metricId",
            ExpectedProblem(status = 422, code = "VALIDATION_FAILED"),
            body = mapOf("minValue" to 7),
            bearer = token,
        )

        expectStatus("DELETE", "/api/v1/catalog/metrics/$metricId", null, token, 204)
        assertThat(getJson("/api/v1/catalog/metrics", token).size()).isEqualTo(1)
    }

    @Test
    fun `factors and events crud`() {
        val token = register(uniqueEmail("fe")).token()

        // факторов и событий у нового пользователя нет (системных пресетов в 2.1 нет)
        assertThat(getJson("/api/v1/catalog/factors", token).size()).isZero()
        assertThat(getJson("/api/v1/catalog/events", token).size()).isZero()

        val factor = postJson(
            "/api/v1/catalog/factors",
            mapOf("name" to "Сон", "icon" to "moon"),
            token,
            expected = 201,
        )
        assertThatText(factor, "name").isEqualTo("Сон")
        assertThatText(factor, "system").isEqualTo("false")

        // события — строго персональный справочник
        val event = postJson("/api/v1/catalog/events", mapOf("name" to "Ссора с коллегой"), token, expected = 201)
        val eventId = event.int("id")
        assertThatText(event, "system").isEqualTo("false")

        // список событий и факторов
        assertThat(getJson("/api/v1/catalog/events", token).size()).isEqualTo(1)
        assertThat(getJson("/api/v1/catalog/factors", token).size()).isEqualTo(1)

        // деактивация события скрывает его из списка по умолчанию
        patchJson("/api/v1/catalog/events/$eventId", mapOf("isActive" to false), token)
        assertThat(getJson("/api/v1/catalog/events", token).size()).isZero()
        assertThat(getJson("/api/v1/catalog/events?includeInactive=true", token).size()).isEqualTo(1)

        // дубль имени фактора — 409
        expectProblem(
            "POST",
            "/api/v1/catalog/factors",
            ExpectedProblem(status = 409, code = "NAME_ALREADY_EXISTS"),
            body = mapOf("name" to "Сон"),
            bearer = token,
        )
    }

    // ---------- helpers ----------

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
        // 204 и прочие ответы без тела приходят с responseBody == null — это норма
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

    /** JSON-указатель из пути с точками: "user.email" -> "/user/email". */
    private fun JsonNode.pointer(field: String) = at("/" + field.replace('.', '/'))

    private fun JsonNode.int(field: String): Int = pointer(field).asInt()

    private fun JsonNode.bool(field: String): Boolean = pointer(field).asBoolean()

    private fun assertThatText(node: JsonNode, field: String) = org.assertj.core.api.Assertions
        .assertThat(node.pointer(field).asText())

    private fun uniqueEmail(prefix: String): String = "$prefix-${System.nanoTime()}-${(0..999).random()}@integration.local"

    companion object {
        private const val DEFAULT_PASSWORD = "secret-password-1"
    }
}

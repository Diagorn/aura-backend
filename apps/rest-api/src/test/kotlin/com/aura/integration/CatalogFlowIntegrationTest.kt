package com.aura.integration

import liquibase.integration.spring.SpringLiquibase
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
import javax.sql.DataSource

/**
 * Интеграционные сценарии этапов 2.1–2.2 (см. docs/roadmap.md «Готово, когда»):
 * персональный CRUD по всем четырём справочникам, системные пресеты (seed),
 * пер-пользовательское скрытие системных, права USER/ADMIN, идемпотентность seed.
 * Настоящий Postgres в Testcontainers; Liquibase накатывает master-чейнджлог с seed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(CatalogFlowIntegrationTest.PostgresConfig::class)
@TestMethodOrder(MethodOrderer.Random::class)
class CatalogFlowIntegrationTest(
    @Autowired private val restTestClient: RestTestClient,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val dataSource: DataSource,
    @Autowired private val liquibase: SpringLiquibase,
) {

    @TestConfiguration(proxyBeanMethods = false)
    class PostgresConfig {

        @Bean
        @ServiceConnection
        fun postgres(): PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17-alpine")
            .withInitScript("init-test.sql")
    }

    @Test
    fun `system presets are visible to a new user`() {
        val token = register(uniqueEmail("fresh")).token()

        val emotions = getJson("/api/v1/catalog/emotions", token)
        assertThat(emotions.size()).isEqualTo(SYSTEM_EMOTIONS)
        for (i in 0 until emotions.size()) {
            assertThat(emotions[i].bool("system")).isTrue()
            assertThat(emotions[i].bool("isActive")).isTrue()
        }

        val factors = getJson("/api/v1/catalog/factors", token)
        assertThat(factors.size()).isEqualTo(SYSTEM_FACTORS)

        val metrics = getJson("/api/v1/catalog/metrics", token)
        assertThat(metrics.size()).isEqualTo(SYSTEM_METRICS)
        assertThatText(metrics[0], "minValue").isEqualTo("1")
        assertThatText(metrics[0], "maxValue").isEqualTo("5")

        // событий в seed нет — справочник строго персональный
        assertThat(getJson("/api/v1/catalog/events", token).size()).isZero()
    }

    @Test
    fun `seed is idempotent on rerun`() {
        // удаляем запись о seed-чейнджсетах: Liquibase переоценит их preConditions
        jdbcUpdate(
            "DELETE FROM liquibase.databasechangelog WHERE id IN " +
                "('catalog-900-seed-emotions', 'catalog-901-seed-factors', 'catalog-902-seed-tracked-metrics')",
        )
        liquibase.afterPropertiesSet()

        assertThat(jdbcCount("SELECT COUNT(*) FROM catalog.emotions WHERE owner_user_id IS NULL"))
            .isEqualTo(SYSTEM_EMOTIONS)
        assertThat(jdbcCount("SELECT COUNT(*) FROM catalog.factors WHERE owner_user_id IS NULL"))
            .isEqualTo(SYSTEM_FACTORS)
        assertThat(jdbcCount("SELECT COUNT(*) FROM catalog.tracked_metrics WHERE owner_user_id IS NULL"))
            .isEqualTo(SYSTEM_METRICS)
    }

    @Test
    fun `emotions crud with per-user isolation`() {
        val token = register(uniqueEmail("emo-a")).token()

        // новый пользователь видит только системные пресеты
        assertThat(getJson("/api/v1/catalog/emotions", token).size()).isEqualTo(SYSTEM_EMOTIONS)

        // создание: sortOrder присваивается сервером, новые элементы активны
        val first = postJson(
            "/api/v1/catalog/emotions",
            mapOf("name" to "Спокойствие личное", "color" to 65280, "icon" to "calm"),
            token,
            expected = 201,
        )
        val firstId = first.int("id")
        assertThatText(first, "name").isEqualTo("Спокойствие личное")
        assertThatText(first, "system").isEqualTo("false")
        assertThat(first.bool("isActive")).isTrue()
        assertThat(first.int("sortOrder")).isZero()

        // дубль имени у того же пользователя — 409
        expectProblem(
            "POST",
            "/api/v1/catalog/emotions",
            ExpectedProblem(status = 409, code = "NAME_ALREADY_EXISTS"),
            body = mapOf("name" to "Спокойствие личное"),
            bearer = token,
        )

        // вторая эмоция получает следующий персональный sortOrder
        val second = postJson(
            "/api/v1/catalog/emotions",
            mapOf("name" to "Тревога личная"),
            token,
            expected = 201,
        )
        val secondId = second.int("id")
        assertThat(second.int("sortOrder")).isEqualTo(1)

        // PATCH-семантика: переименование + деактивация, остальные поля на месте
        val patched = patchJson(
            "/api/v1/catalog/emotions/$secondId",
            mapOf("name" to "Тревожность личная", "isActive" to false),
            token,
        )
        assertThatText(patched, "name").isEqualTo("Тревожность личная")
        assertThat(patched.bool("isActive")).isFalse()

        // по умолчанию деактивированные персональные скрыты; includeInactive=true возвращает их
        assertThat(getJson("/api/v1/catalog/emotions", token).size()).isEqualTo(SYSTEM_EMOTIONS + 1)
        val withInactive = getJson("/api/v1/catalog/emotions?includeInactive=true", token)
        assertThat(withInactive.size()).isEqualTo(SYSTEM_EMOTIONS + 2)

        // удаление -> 204, после чего элемент недоступен (404, существование не раскрываем)
        expectStatus("DELETE", "/api/v1/catalog/emotions/$firstId", null, token, 204)
        expectProblem(
            "PATCH",
            "/api/v1/catalog/emotions/$firstId",
            ExpectedProblem(status = 404, code = "NOT_FOUND"),
            body = mapOf("name" to "Призрак"),
            bearer = token,
        )

        // изоляция пользователей: чужая эмоция не видна в общем списке (там одни системные) и не правится
        val otherToken = register(uniqueEmail("emo-b")).token()
        assertThat(getJson("/api/v1/catalog/emotions", otherToken).size()).isEqualTo(SYSTEM_EMOTIONS)
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
    fun `system emotion can be hidden and restored per user`() {
        val tokenA = register(uniqueEmail("hide-a")).token()
        val tokenB = register(uniqueEmail("hide-b")).token()

        val anxietyId = findByName(getJson("/api/v1/catalog/emotions", tokenA), "Тревога").int("id")

        // скрытие системной эмоции для себя: {isActive:false} по системному — легально
        val hidden = patchJson("/api/v1/catalog/emotions/$anxietyId", mapOf("isActive" to false), tokenA)
        assertThatText(hidden, "system").isEqualTo("true")
        assertThat(hidden.bool("isActive")).isFalse()

        // по умолчанию скрытой нет; с includeInactive — есть, помечена неактивной
        val defaultList = getJson("/api/v1/catalog/emotions", tokenA)
        assertThat(findByNameOrNull(defaultList, "Тревога")).isNull()
        assertThat(defaultList.size()).isEqualTo(SYSTEM_EMOTIONS - 1)
        val withInactive = getJson("/api/v1/catalog/emotions?includeInactive=true", tokenA)
        assertThat(withInactive.size()).isEqualTo(SYSTEM_EMOTIONS)
        assertThat(findByName(withInactive, "Тревога").bool("isActive")).isFalse()

        // другой пользователь по-прежнему видит системную
        val listB = getJson("/api/v1/catalog/emotions", tokenB)
        assertThat(findByName(listB, "Тревога").bool("isActive")).isTrue()

        // правка полей системной не админом — 403
        expectProblem(
            "PATCH",
            "/api/v1/catalog/emotions/$anxietyId",
            ExpectedProblem(status = 403, code = "SYSTEM_ITEM_FORBIDDEN"),
            body = mapOf("name" to "Хакнутая тревога"),
            bearer = tokenA,
        )
        // удаление системной — 403 для любого пользователя
        expectProblem(
            "DELETE",
            "/api/v1/catalog/emotions/$anxietyId",
            ExpectedProblem(status = 403, code = "SYSTEM_ITEM_FORBIDDEN"),
            bearer = tokenA,
        )

        // возврат: {isActive:true}, идемпотентно
        val restored = patchJson("/api/v1/catalog/emotions/$anxietyId", mapOf("isActive" to true), tokenA)
        assertThat(restored.bool("isActive")).isTrue()
        assertThat(findByNameOrNull(getJson("/api/v1/catalog/emotions", tokenA), "Тревога")).isNotNull()

        // пустой PATCH системного не админом — тоже 403 (нет разрешённой операции)
        expectProblem(
            "PATCH",
            "/api/v1/catalog/emotions/$anxietyId",
            ExpectedProblem(status = 403, code = "SYSTEM_ITEM_FORBIDDEN"),
            body = emptyMap<String, Any>(),
            bearer = tokenA,
        )
    }

    @Test
    fun `admin edits system preset through the same endpoint`() {
        val adminToken = registerAdmin(uniqueEmail("admin")).token()
        val userToken = register(uniqueEmail("admin-check")).token()

        val calmId = findByName(getJson("/api/v1/catalog/emotions", adminToken), "Спокойствие").int("id")

        // админ правит поля системной строки — эффект глобальный
        val updated = patchJson(
            "/api/v1/catalog/emotions/$calmId",
            mapOf("name" to "Спокойствие 2.0", "color" to 10494192),
            adminToken,
        )
        assertThatText(updated, "name").isEqualTo("Спокойствие 2.0")
        assertThatText(updated, "system").isEqualTo("true")
        assertThat(findByName(getJson("/api/v1/catalog/emotions", userToken), "Спокойствие 2.0")).isNotNull()

        // админская деактивация системной убирает её у всех (includeInactive не возвращает)
        val joyId = findByName(getJson("/api/v1/catalog/emotions", adminToken), "Радость").int("id")
        patchJson("/api/v1/catalog/emotions/$joyId", mapOf("isActive" to false), adminToken)
        assertThat(findByNameOrNull(getJson("/api/v1/catalog/emotions", userToken), "Радость")).isNull()
        assertThat(findByNameOrNull(getJson("/api/v1/catalog/emotions?includeInactive=true", userToken), "Радость"))
            .isNull()

        // удалять системные нельзя даже админу
        expectProblem(
            "DELETE",
            "/api/v1/catalog/emotions/$joyId",
            ExpectedProblem(status = 403, code = "SYSTEM_ITEM_FORBIDDEN"),
            bearer = adminToken,
        )

        // тесты разделяют одну БД (порядок случайный) — возвращаем системный набор как был
        patchJson("/api/v1/catalog/emotions/$joyId", mapOf("isActive" to true), adminToken)
        patchJson("/api/v1/catalog/emotions/$calmId", mapOf("name" to "Спокойствие"), adminToken)
        assertThat(findByName(getJson("/api/v1/catalog/emotions", userToken), "Спокойствие")).isNotNull()
    }

    @Test
    fun `metrics defaults scale validation and crud`() {
        val token = register(uniqueEmail("met")).token()

        // персональная метрика может называться как системная — дубль легален
        val metric = postJson("/api/v1/catalog/metrics", mapOf("name" to "Энергия"), token, expected = 201)
        val metricId = metric.int("id")
        assertThatText(metric, "minValue").isEqualTo("1")
        assertThatText(metric, "maxValue").isEqualTo("5")
        assertThatText(metric, "system").isEqualTo("false")

        // своя шкала
        val custom = postJson(
            "/api/v1/catalog/metrics",
            mapOf("name" to "Тревожность личная", "minValue" to 0, "maxValue" to 10, "unit" to "балл"),
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
            body = mapOf("name" to "Либидо личное", "minValue" to 5, "maxValue" to 1),
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
        // 4 системные пресеты + персональная "Тревожность личная"
        assertThat(getJson("/api/v1/catalog/metrics", token).size()).isEqualTo(SYSTEM_METRICS + 1)
    }

    @Test
    fun `factors and events crud`() {
        val token = register(uniqueEmail("fe")).token()

        // у нового пользователя уже есть системные факторы; событий нет (справочник персональный)
        assertThat(getJson("/api/v1/catalog/factors", token).size()).isEqualTo(SYSTEM_FACTORS)
        assertThat(getJson("/api/v1/catalog/events", token).size()).isZero()

        val factor = postJson(
            "/api/v1/catalog/factors",
            mapOf("name" to "Сон личный", "icon" to "moon"),
            token,
            expected = 201,
        )
        assertThatText(factor, "name").isEqualTo("Сон личный")
        assertThatText(factor, "system").isEqualTo("false")

        val event = postJson("/api/v1/catalog/events", mapOf("name" to "Ссора с коллегой"), token, expected = 201)
        val eventId = event.int("id")
        assertThatText(event, "system").isEqualTo("false")

        assertThat(getJson("/api/v1/catalog/events", token).size()).isEqualTo(1)
        assertThat(getJson("/api/v1/catalog/factors", token).size()).isEqualTo(SYSTEM_FACTORS + 1)

        // деактивация персонального события скрывает его из списка по умолчанию
        patchJson("/api/v1/catalog/events/$eventId", mapOf("isActive" to false), token)
        assertThat(getJson("/api/v1/catalog/events", token).size()).isZero()
        assertThat(getJson("/api/v1/catalog/events?includeInactive=true", token).size()).isEqualTo(1)

        // дубль имени персонального фактора — 409 (имя системного "Сон" занять можно)
        expectProblem(
            "POST",
            "/api/v1/catalog/factors",
            ExpectedProblem(status = 409, code = "NAME_ALREADY_EXISTS"),
            body = mapOf("name" to "Сон личный"),
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

    /** Регистрирует пользователя, повышает роль до ADMIN в БД и перелогинивается: токен получает roles=[ADMIN]. */
    private fun registerAdmin(email: String): JsonNode {
        register(email)
        jdbcUpdate("UPDATE auth.users SET role = 'ADMIN' WHERE email = ?", email)
        return postJson("/api/v1/auth/login", mapOf("email" to email, "password" to DEFAULT_PASSWORD))
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

    private fun findByName(array: JsonNode, name: String): JsonNode =
        requireNotNull(findByNameOrNull(array, name)) { "Элемент '$name' не найден в: $array" }

    private fun findByNameOrNull(array: JsonNode, name: String): JsonNode? {
        for (i in 0 until array.size()) {
            val item = array[i]
            if (!item.at("/name").isMissingNode && item.text("name") == name) {
                return item
            }
        }
        return null
    }

    private fun jdbcUpdate(sql: String, vararg params: Any) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                params.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeUpdate()
            }
        }
    }

    private fun jdbcCount(sql: String): Int =
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.executeQuery().use { resultSet ->
                    resultSet.next()
                    resultSet.getInt(1)
                }
            }
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

        /** Размеры системных наборов из seed (docs/domain-model.md «Системные пресеты»). */
        private const val SYSTEM_EMOTIONS = 13
        private const val SYSTEM_FACTORS = 8
        private const val SYSTEM_METRICS = 4
    }
}

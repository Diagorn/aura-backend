package com.aura.integration

import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.assertj.core.api.Assertions.assertThat
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
 * Интеграционный сценарий этапа 1 (см. docs/roadmap.md «Готово, когда»):
 * register -> login -> refresh (с ротацией и reuse detection) -> logout,
 * /me с telegram после связки, внутренний сервисный токен и обмен токена ботом.
 * Настоящий Postgres в Testcontainers; Liquibase накатывает master-чейнджлог с seed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(AuthFlowIntegrationTest.PostgresConfig::class)
@TestMethodOrder(MethodOrderer.Random::class)
class AuthFlowIntegrationTest(
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
    fun `register login refresh logout happy path`() {
        val email = uniqueEmail("full")
        val registered = register(email)

        // /me сразу после регистрации: telegram ещё null
        val me = getJson("/api/v1/me", registered.token())
        assertThatText(me, "email").isEqualTo(email)
        assertThatText(me, "timezone").isEqualTo("Europe/Moscow")
        assertThat(me.at("/telegram").isNull).isTrue()

        // обновление профиля
        val patched = patchJson(
            "/api/v1/me",
            mapOf("timezone" to "Europe/Moscow"),
            registered.token(),
        )
        assertThatText(patched, "timezone").isEqualTo("Europe/Moscow")

        // повторный вход
        val login = postJson(
            "/api/v1/auth/login",
            mapOf("email" to email, "password" to DEFAULT_PASSWORD),
        )
        assertThatText(login, "user.email").isEqualTo(email)

        // смена пароля: неверный текущий -> 422
        expectProblem(
            "PUT",
            "/api/v1/me/password",
            ExpectedProblem(status = 422, code = "VALIDATION_FAILED"),
            body = mapOf("currentPassword" to "wrong-password", "newPassword" to "changed-password"),
            bearer = registered.token(),
        )
        // верный текущий -> 204, после чего старый пароль больше не работает
        expectStatus(
            "PUT",
            "/api/v1/me/password",
            mapOf("currentPassword" to DEFAULT_PASSWORD, "newPassword" to "changed-password"),
            registered.token(),
            204,
        )
        expectProblem(
            "POST",
            "/api/v1/auth/login",
            ExpectedProblem(status = 401, code = "INVALID_CREDENTIALS"),
            body = mapOf("email" to email, "password" to DEFAULT_PASSWORD),
        )
        postJson("/api/v1/auth/login", mapOf("email" to email, "password" to "changed-password"))

        // выход отзывает refresh-токен; попытка им воспользоваться — reuse detection
        expectStatus("POST", "/api/v1/auth/logout", mapOf("refreshToken" to registered.refreshToken()), null, 204)
        expectProblem(
            "POST",
            "/api/v1/auth/refresh",
            ExpectedProblem(status = 401, code = "REFRESH_TOKEN_REUSE"),
            body = mapOf("refreshToken" to registered.refreshToken()),
        )
    }

    @Test
    fun `refresh rotates tokens and reuse detection revokes the chain`() {
        val registered = register(uniqueEmail("rotation"))

        val first = postJson("/api/v1/auth/refresh", mapOf("refreshToken" to registered.refreshToken()))
        val secondRefreshToken = first.text("refreshToken")

        // старый токен больше не работает... и его повторное использование отзывает всю цепочку
        expectProblem(
            "POST",
            "/api/v1/auth/refresh",
            ExpectedProblem(status = 401, code = "REFRESH_TOKEN_REUSE"),
            body = mapOf("refreshToken" to registered.refreshToken()),
        )
        // ...свежий токен цепочки тоже отозван: предъявленный повторно — reuse (найден, но отозван)
        expectProblem(
            "POST",
            "/api/v1/auth/refresh",
            ExpectedProblem(status = 401, code = "REFRESH_TOKEN_REUSE"),
            body = mapOf("refreshToken" to secondRefreshToken),
        )
        // неизвестный же токен — просто INVALID_REFRESH_TOKEN
        expectProblem(
            "POST",
            "/api/v1/auth/refresh",
            ExpectedProblem(status = 401, code = "INVALID_REFRESH_TOKEN"),
            body = mapOf("refreshToken" to "totally-unknown-token"),
        )
    }

    @Test
    fun `service token links telegram and exchange issues user token`() {
        // сервисный токен бота по seed-кредам
        val serviceToken = postJson(
            "/internal/v1/auth/service-token",
            mapOf("clientId" to BOT_CLIENT_ID, "clientSecret" to BOT_CLIENT_SECRET),
        ).text("accessToken")

        // неверные креды отклоняются
        expectProblem(
            "POST",
            "/internal/v1/auth/service-token",
            ExpectedProblem(status = 401, code = "INVALID_CLIENT_CREDENTIALS"),
            body = mapOf("clientId" to BOT_CLIENT_ID, "clientSecret" to "wrong-secret"),
        )

        val registered = register(uniqueEmail("tg"))

        // код связки: аккаунт ещё не привязан
        val linkCode = postJson("/api/v1/auth/telegram/link-code", null, registered.token(), expected = 201)
        val code = linkCode.text("code")
        assertThatText(linkCode, "expiresAt").isNotBlank

        // бота без сервисного токена не пускают
        expectProblem(
            "POST",
            "/internal/v1/auth/telegram/link",
            ExpectedProblem(status = 401, code = "UNAUTHORIZED"),
            body = mapOf("code" to code, "telegramUserId" to TG_USER_ID, "username" to TG_USERNAME),
        )

        // привязка по сервисному токену; /me показывает telegram
        expectStatus(
            "POST",
            "/internal/v1/auth/telegram/link",
            mapOf("code" to code, "telegramUserId" to TG_USER_ID, "username" to TG_USERNAME),
            serviceToken,
            204,
        )
        val me = getJson("/api/v1/me", registered.token())
        assertThatText(me, "telegram.userId").isEqualTo(TG_USER_ID)
        assertThatText(me, "telegram.username").isEqualTo(TG_USERNAME)

        // код одноразовый; пользователь больше не может получить новый код
        expectProblem(
            "POST",
            "/internal/v1/auth/telegram/link",
            ExpectedProblem(status = 404, code = "TELEGRAM_LINK_CODE_NOT_FOUND"),
            body = mapOf("code" to code, "telegramUserId" to TG_USER_ID, "username" to TG_USERNAME),
            bearer = serviceToken,
        )
        expectProblem(
            "POST",
            "/api/v1/auth/telegram/link-code",
            ExpectedProblem(status = 409, code = "TELEGRAM_ALREADY_LINKED"),
            bearer = registered.token(),
        )

        // exchange отдаёт пользовательский access-токен и обновляет username
        val exchanged = postJson(
            "/internal/v1/auth/telegram/exchange",
            mapOf("telegramUserId" to TG_USER_ID, "username" to TG_USERNAME_NEW),
            serviceToken,
        )
        val exchangedAccessToken = exchanged.text("accessToken")
        assertThat(exchanged.int("expiresIn")).isGreaterThan(0)
        assertThatText(getJson("/api/v1/me", exchangedAccessToken), "telegram.username").isEqualTo(TG_USERNAME_NEW)

        // пользовательский токен бота даёт доступ к /api/v1, но не к внутренним маршрутам
        expectStatus("GET", "/api/v1/me", null, exchangedAccessToken, 200)
        expectProblem(
            "POST",
            "/internal/v1/auth/telegram/exchange",
            ExpectedProblem(status = 403, code = "FORBIDDEN"),
            body = mapOf("telegramUserId" to TG_USER_ID, "username" to null),
            bearer = exchangedAccessToken,
        )

        // сервисный токен не открывает пользовательские маршруты
        expectStatus("GET", "/api/v1/me", null, serviceToken, 403)

        // повторная связка того же telegram-аккаунта с другим пользователем — 409
        val other = register(uniqueEmail("tg2"))
        val otherCode = postJson("/api/v1/auth/telegram/link-code", null, other.token(), expected = 201).text("code")
        expectProblem(
            "POST",
            "/internal/v1/auth/telegram/link",
            ExpectedProblem(status = 409, code = "TELEGRAM_ALREADY_LINKED"),
            body = mapOf("code" to otherCode, "telegramUserId" to TG_USER_ID, "username" to null),
            bearer = serviceToken,
        )
    }

    // ---------- helpers ----------

    private fun register(email: String, password: String = DEFAULT_PASSWORD, timezone: String? = null): JsonNode {
        val body = buildMap {
            put("email", email)
            put("password", password)
            timezone?.let { put("timezone", it) }
        }
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
        if (method == "GET") {
            val spec: RestTestClient.RequestHeadersSpec<*> = restTestClient.get().uri(uri)
            val authed = bearer?.let { spec.header(HttpHeaders.AUTHORIZATION, "Bearer $it") } ?: spec
            return authed.exchange()
        }
        val bodySpec: RestTestClient.RequestBodySpec = when (method) {
            "POST" -> restTestClient.post().uri(uri)
            "PATCH" -> restTestClient.patch().uri(uri)
            "PUT" -> restTestClient.put().uri(uri)
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

    private fun JsonNode.refreshToken(): String = text("refreshToken")

    private fun JsonNode.text(field: String): String {
        val value = pointer(field)
        check(!value.isMissingNode && !value.isNull) { "Поле '$field' отсутствует в ответе: $this" }
        return value.asText()
    }

    /** JSON-указатель из пути с точками: "user.email" -> "/user/email". */
    private fun JsonNode.pointer(field: String) = at("/" + field.replace('.', '/'))

    private fun JsonNode.int(field: String): Int = pointer(field).asInt()

    private fun assertThatText(node: JsonNode, field: String) = org.assertj.core.api.Assertions
        .assertThat(node.pointer(field).asText())

    private fun uniqueEmail(prefix: String): String = "$prefix-${System.nanoTime()}@integration.local"

    companion object {
        private const val DEFAULT_PASSWORD = "secret-password-1"
        private const val BOT_CLIENT_ID = "aura-telegram-bot"
        private const val BOT_CLIENT_SECRET = "aura-bot-dev-secret"
        private const val TG_USER_ID = "900168"
        private const val TG_USERNAME = "integration_bot_user"
        private const val TG_USERNAME_NEW = "integration_bot_user_renamed"
    }
}

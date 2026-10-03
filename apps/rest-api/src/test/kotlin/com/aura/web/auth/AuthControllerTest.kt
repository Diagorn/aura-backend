package com.aura.web.auth

import com.aura.api.model.RegisterRequest
import com.aura.auth.api.AccessTokenVerifier
import com.aura.auth.api.AccountView
import com.aura.auth.api.AuthService
import com.aura.auth.api.AuthSession
import com.aura.auth.api.EmailAlreadyExistsException
import com.aura.auth.api.InvalidCredentialsException
import com.aura.auth.api.TelegramLinkPort
import com.aura.api.model.LoginRequest
import com.aura.security.SecurityConfig
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper

/**
 * Slice-тест контроллера аутентификации: контракт ответов, статус-коды и problem+json.
 * Логика auth замокана — она покрыта unit- и интеграционными тестами.
 */
@WebMvcTest(AuthController::class)
@Import(SecurityConfig::class)
class AuthControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
) {

    @MockkBean
    lateinit var authService: AuthService

    @MockkBean
    lateinit var telegramLink: TelegramLinkPort

    @MockkBean(relaxed = true)
    lateinit var accessTokenVerifier: AccessTokenVerifier

    @Test
    fun register_withValidRequest_returns201WithTokensAndUser() {
        every { authService.register(any()) } returns AuthSession(
            user = AccountView(id = 1, email = "anna@example.com", timezone = "Europe/Moscow", locale = "ru", telegram = null),
            accessToken = "access-token",
            refreshToken = "refresh-token",
        )

        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                RegisterRequest(email = "anna@example.com", password = "secret-password", timezone = "Europe/Moscow"),
            )
        }.andExpect {
            status { isCreated() }
            content { contentType(MediaType.APPLICATION_JSON) }
            jsonPath("$.user.email") { value("anna@example.com") }
            jsonPath("$.user.timezone") { value("Europe/Moscow") }
            jsonPath("$.user.telegram") { value(nullValue()) }
            jsonPath("$.accessToken") { value("access-token") }
            jsonPath("$.refreshToken") { value("refresh-token") }
        }

        verify {
            authService.register(
                withArg {
                    assertThat(it.email).isEqualTo("anna@example.com")
                    assertThat(it.timezone).isEqualTo("Europe/Moscow")
                },
            )
        }
    }

    @Test
    fun register_withInvalidBody_returns422WithFieldErrors() {
        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                RegisterRequest(email = "not-an-email", password = "123", timezone = null),
            )
        }.andExpect {
            status { isUnprocessableEntity() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors") { isNotEmpty() }
        }
    }

    @Test
    fun register_withTakenEmail_returns409Problem() {
        every { authService.register(any()) } throws EmailAlreadyExistsException()

        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                RegisterRequest(email = "anna@example.com", password = "secret-password", timezone = null),
            )
        }.andExpect {
            status { isConflict() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("EMAIL_ALREADY_EXISTS") }
        }
    }

    @Test
    fun login_withWrongCredentials_returns401Problem() {
        every { authService.login(any()) } throws com.aura.auth.api.InvalidCredentialsException()

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(LoginRequest(email = "anna@example.com", password = "wrong-pass"))
        }.andExpect {
            status { isUnauthorized() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("INVALID_CREDENTIALS") }
        }
    }
}

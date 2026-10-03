package com.aura.web.internal

import com.aura.api.model.ServiceTokenRequest
import com.aura.api.model.TelegramExchangeRequest
import com.aura.api.model.TelegramLinkRequest
import com.aura.auth.api.AccessTokenIssued
import com.aura.auth.api.AccessTokenVerifier
import com.aura.auth.api.ClientCredentials
import com.aura.auth.api.InvalidClientCredentialsException
import com.aura.auth.api.ServiceTokenIssued
import com.aura.auth.api.ServiceTokenPort
import com.aura.auth.api.TelegramLinkPort
import com.aura.security.SecurityConfig
import com.aura.security.WithMockAuraUser
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper

/**
 * Slice-тест внутреннего контура бота: service-token доступен анонимно
 * (креды в теле), остальные маршруты — только с scope=internal.
 */
@WebMvcTest(InternalAuthController::class)
@Import(SecurityConfig::class)
class InternalAuthControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
) {

    @MockkBean
    lateinit var serviceTokens: ServiceTokenPort

    @MockkBean
    lateinit var telegramLink: TelegramLinkPort

    @MockkBean(relaxed = true)
    lateinit var accessTokenVerifier: AccessTokenVerifier

    @Test
    fun createServiceToken_withValidCredentials_returnsToken() {
        every {
            serviceTokens.issue(ClientCredentials(clientId = "aura-telegram-bot", clientSecret = "aura-bot-dev-secret"))
        } returns ServiceTokenIssued(accessToken = "service-jwt", expiresIn = 86_400)

        mockMvc.post("/internal/v1/auth/service-token") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                ServiceTokenRequest(clientId = "aura-telegram-bot", clientSecret = "aura-bot-dev-secret"),
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { value("service-jwt") }
            jsonPath("$.expiresIn") { value(86_400) }
        }
    }

    @Test
    fun createServiceToken_withWrongCredentials_returns401Problem() {
        every { serviceTokens.issue(any()) } throws InvalidClientCredentialsException()

        mockMvc.post("/internal/v1/auth/service-token") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                ServiceTokenRequest(clientId = "aura-telegram-bot", clientSecret = "wrong"),
            )
        }.andExpect {
            status { isUnauthorized() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("INVALID_CLIENT_CREDENTIALS") }
        }
    }

    @Test
    fun linkTelegramAccount_withoutToken_returns401Problem() {
        mockMvc.post("/internal/v1/auth/telegram/link") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                TelegramLinkRequest(code = "ABCD2345", telegramUserId = "900168", username = "anna"),
            )
        }.andExpect {
            status { isUnauthorized() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
    }

    @Test
    @WithMockAuraUser(userId = 1, roles = [], scopes = ["internal"])
    fun linkTelegramAccount_withInternalScope_returns204() {
        every { telegramLink.link(any()) } returns Unit

        mockMvc.post("/internal/v1/auth/telegram/link") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                TelegramLinkRequest(code = "ABCD2345", telegramUserId = "900168", username = "anna"),
            )
        }.andExpect { status { isNoContent() } }
    }

    @Test
    @WithMockAuraUser(userId = 1, roles = ["USER"])
    fun exchangeTelegramToken_withUserRole_onlyInternalScope_returns403() {
        mockMvc.post("/internal/v1/auth/telegram/exchange") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                TelegramExchangeRequest(telegramUserId = "900168", username = null),
            )
        }.andExpect { status { isForbidden() } }
    }

    @Test
    @WithMockAuraUser(userId = 1, roles = [], scopes = ["internal"])
    fun exchangeTelegramToken_withInternalScope_returnsToken() {
        every { telegramLink.exchange(any()) } returns AccessTokenIssued(accessToken = "user-jwt", expiresIn = 900)

        mockMvc.post("/internal/v1/auth/telegram/exchange") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                TelegramExchangeRequest(telegramUserId = "900168", username = "anna"),
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { value("user-jwt") }
            jsonPath("$.expiresIn") { value(900) }
        }
    }
}

package com.aura.web.me

import com.aura.api.model.ChangePasswordRequest
import com.aura.api.model.UpdateMeRequest
import com.aura.auth.api.AccessTokenVerifier
import com.aura.security.SecurityConfig
import com.aura.security.WithMockAuraUser
import com.aura.shared.ValidationFailedException
import com.aura.user.api.TelegramAccount
import com.aura.user.api.UserProfile
import com.aura.user.api.UserProfilePort
import com.ninjasquad.springmockk.MockkBean
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.put
import tools.jackson.databind.ObjectMapper

/** Slice-тест профиля /api/v1/me: маппинг контракта, 401/422. */
@WebMvcTest(MeController::class)
@Import(SecurityConfig::class)
class MeControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
) {

    @MockkBean
    lateinit var profiles: UserProfilePort

    @MockkBean(relaxed = true)
    lateinit var accessTokenVerifier: AccessTokenVerifier

    private val profile = UserProfile(
        id = 7,
        email = "anna@example.com",
        timezone = "UTC",
        locale = "ru",
        telegram = TelegramAccount(userId = "900168", username = "anna"),
    )

    @Test
    @WithMockAuraUser(userId = 7)
    fun getMe_returnsProfileWithTelegram() {
        every { profiles.get(7) } returns profile

        mockMvc.get("/api/v1/me").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(7) }
            jsonPath("$.email") { value("anna@example.com") }
            jsonPath("$.telegram.userId") { value("900168") }
            jsonPath("$.telegram.username") { value("anna") }
        }
    }

    @Test
    fun getMe_withoutToken_returns401Problem() {
        mockMvc.get("/api/v1/me").andExpect {
            status { isUnauthorized() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
    }

    @Test
    @WithMockAuraUser(userId = 7)
    fun patchMe_updatesOnlyProvidedFields() {
        every { profiles.update(7, any()) } returns profile.copy(timezone = "Europe/Moscow")

        mockMvc.patch("/api/v1/me") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(UpdateMeRequest(timezone = "Europe/Moscow", locale = null))
        }.andExpect {
            status { isOk() }
            jsonPath("$.timezone") { value("Europe/Moscow") }
        }

        verify {
            profiles.update(
                7,
                withArg {
                    assertThat(it.timezone).isEqualTo("Europe/Moscow")
                    assertThat(it.locale).isNull()
                },
            )
        }
        confirmVerified(profiles)
    }

    @Test
    @WithMockAuraUser(userId = 7)
    fun patchMe_withBadTimezone_returns422() {
        every { profiles.update(7, any()) } throws ValidationFailedException("timezone", "Неизвестная IANA-таймзона")

        mockMvc.patch("/api/v1/me") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(UpdateMeRequest(timezone = "Mars/Olympus", locale = null))
        }.andExpect {
            status { isUnprocessableEntity() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors[0].field") { value("timezone") }
        }
    }

    @Test
    @WithMockAuraUser(userId = 7)
    fun changePassword_returns204() {
        every { profiles.changePassword(7, any()) } returns Unit

        mockMvc.put("/api/v1/me/password") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                ChangePasswordRequest(currentPassword = "old-password", newPassword = "new-password"),
            )
        }.andExpect { status { isNoContent() } }

        verify(exactly = 1) { profiles.changePassword(7, any()) }
    }
}

package com.aura.web.auth

import com.aura.api.AuthApi
import com.aura.api.model.AuthResponse
import com.aura.api.model.LoginRequest
import com.aura.api.model.LogoutRequest
import com.aura.api.model.RefreshRequest
import com.aura.api.model.RegisterRequest
import com.aura.api.model.TelegramLinkCodeResponse
import com.aura.auth.api.AuthService
import com.aura.auth.api.LoginCommand
import com.aura.auth.api.RegisterCommand
import com.aura.auth.api.TelegramLinkPort
import com.aura.security.CurrentUser
import com.aura.web.toMeResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Аутентификация: регистрация, вход, ротация refresh, выход, код связки Telegram.
 * Контракт — сгенерированный [AuthApi]; вся логика — в модуле auth.
 */
@RestController
class AuthController(
    private val authService: AuthService,
    private val telegramLink: TelegramLinkPort,
) : AuthApi {

    override fun register(registerRequest: RegisterRequest): ResponseEntity<AuthResponse> {
        val session = authService.register(
            RegisterCommand(
                email = registerRequest.email,
                password = registerRequest.password,
                timezone = registerRequest.timezone,
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(session.toResponse())
    }

    override fun login(loginRequest: LoginRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.ok(
            authService.login(LoginCommand(email = loginRequest.email, password = loginRequest.password))
                .toResponse(),
        )

    override fun refresh(refreshRequest: RefreshRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.ok(authService.refresh(refreshRequest.refreshToken).toResponse())

    override fun logout(logoutRequest: LogoutRequest): ResponseEntity<Unit> {
        authService.logout(logoutRequest.refreshToken)
        return ResponseEntity.noContent().build()
    }

    override fun createTelegramLinkCode(): ResponseEntity<TelegramLinkCodeResponse> {
        val issued = telegramLink.createLinkCode(CurrentUser.requireUserId())
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(
                TelegramLinkCodeResponse(
                    code = issued.code,
                    expiresAt = OffsetDateTime.ofInstant(issued.expiresAt, ZoneOffset.UTC),
                ),
            )
    }
}

private fun com.aura.auth.api.AuthSession.toResponse(): AuthResponse = AuthResponse(
    user = user.toMeResponse(),
    accessToken = accessToken,
    refreshToken = refreshToken,
)

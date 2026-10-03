package com.aura.web.internal

import com.aura.api.InternalApi
import com.aura.api.model.ServiceTokenRequest
import com.aura.api.model.ServiceTokenResponse
import com.aura.api.model.TelegramExchangeRequest
import com.aura.api.model.TelegramExchangeResponse
import com.aura.api.model.TelegramLinkRequest
import com.aura.auth.api.ClientCredentials
import com.aura.auth.api.ServiceTokenPort
import com.aura.auth.api.TelegramExchangeCommand
import com.aura.auth.api.TelegramLinkCommand
import com.aura.auth.api.TelegramLinkPort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

/**
 * Внутренний контур Telegram-бота (маршруты /internal/v1, сервисный JWT).
 * Контракт — сгенерированный [InternalApi]; в публичную документацию не публикуется.
 */
@RestController
class InternalAuthController(
    private val serviceTokens: ServiceTokenPort,
    private val telegramLink: TelegramLinkPort,
) : InternalApi {

    override fun createServiceToken(serviceTokenRequest: ServiceTokenRequest): ResponseEntity<ServiceTokenResponse> {
        val issued = serviceTokens.issue(
            ClientCredentials(
                clientId = serviceTokenRequest.clientId,
                clientSecret = serviceTokenRequest.clientSecret,
            ),
        )
        return ResponseEntity.ok(ServiceTokenResponse(accessToken = issued.accessToken, expiresIn = issued.expiresIn))
    }

    override fun linkTelegramAccount(telegramLinkRequest: TelegramLinkRequest): ResponseEntity<Unit> {
        telegramLink.link(
            TelegramLinkCommand(
                code = telegramLinkRequest.code,
                telegramUserId = telegramLinkRequest.telegramUserId,
                username = telegramLinkRequest.username,
            ),
        )
        return ResponseEntity.noContent().build()
    }

    override fun exchangeTelegramToken(
        telegramExchangeRequest: TelegramExchangeRequest,
    ): ResponseEntity<TelegramExchangeResponse> {
        val issued = telegramLink.exchange(
            TelegramExchangeCommand(
                telegramUserId = telegramExchangeRequest.telegramUserId,
                username = telegramExchangeRequest.username,
            ),
        )
        return ResponseEntity.ok(
            TelegramExchangeResponse(accessToken = issued.accessToken, expiresIn = issued.expiresIn),
        )
    }
}

package com.aura.auth.internal.service

import com.aura.auth.api.ClientCredentials
import com.aura.auth.api.InvalidClientCredentialsException
import com.aura.auth.api.ServiceTokenIssued
import com.aura.auth.api.ServiceTokenPort
import com.aura.auth.internal.repository.ServiceClientRepository
import com.aura.auth.internal.security.Hashing
import com.aura.auth.internal.security.TokenService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest

/** Обмен clientId/clientSecret на сервисный JWT для внутренних маршрутов. */
@Service
@Transactional(readOnly = true)
class ServiceTokenService(
    private val serviceClientRepository: ServiceClientRepository,
    private val tokenService: TokenService,
) : ServiceTokenPort {

    override fun issue(credentials: ClientCredentials): ServiceTokenIssued {
        val client = serviceClientRepository.findByClientId(credentials.clientId)
            ?: throw InvalidClientCredentialsException()
        val providedHash = Hashing.sha256Hex(credentials.clientSecret).toByteArray(Charsets.UTF_8)
        val expectedHash = client.clientSecretHash.toByteArray(Charsets.UTF_8)
        if (!MessageDigest.isEqual(providedHash, expectedHash)) {
            throw InvalidClientCredentialsException()
        }
        val token = tokenService.issueServiceToken(client.clientId)
        return ServiceTokenIssued(accessToken = token.token, expiresIn = token.expiresIn)
    }
}

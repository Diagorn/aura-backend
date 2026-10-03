package com.aura.auth.internal.security

import com.aura.auth.api.AccessTokenVerifier
import com.aura.auth.api.TokenPrincipal
import org.springframework.stereotype.Component

/** Адаптер api-контракта проверки токенов поверх [TokenService]. */
@Component
class AccessTokenVerifierAdapter(
    private val tokenService: TokenService,
) : AccessTokenVerifier {

    override fun verify(token: String): TokenPrincipal? = tokenService.verifyBearer(token)
}

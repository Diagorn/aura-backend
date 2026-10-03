package com.aura.auth.internal.security

import com.aura.auth.api.InvalidRefreshTokenException
import com.aura.auth.api.TokenType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration

/** Юнит-тесты выпуска/проверки JWT (HS256) без Spring-контекста. */
class TokenServiceTest {

    private val secret = "unit-test-secret-0123456789abcdef0123456789"
    private val service = TokenService(JwtProperties(secret = secret))

    @Test
    fun `access token roundtrip keeps subject and roles`() {
        val issued = service.issueAccessToken(42L, setOf("USER", "ADMIN"))

        val principal = service.verifyBearer(issued.token)

        assertThat(principal).isNotNull
        assertThat(principal?.type).isEqualTo(TokenType.ACCESS)
        assertThat(principal?.userId).isEqualTo(42L)
        assertThat(principal?.roles).containsExactlyInAnyOrder("USER", "ADMIN")
        assertThat(principal?.scopes).isEmpty()
        assertThat(issued.expiresIn).isGreaterThan(0)
    }

    @Test
    fun `service token carries internal scope and no user id`() {
        val issued = service.issueServiceToken("aura-telegram-bot")

        val principal = service.verifyBearer(issued.token)

        assertThat(principal?.type).isEqualTo(TokenType.SERVICE)
        assertThat(principal?.userId).isNull()
        assertThat(principal?.scopes).containsExactly("internal")
    }

    @Test
    fun `refresh token is not an authentication credential`() {
        val issued = service.issueRefreshToken(42L)

        assertThat(service.verifyBearer(issued.token)).isNull()

        val claims = service.parseRefreshToken(issued.token)
        assertThat(claims.userId).isEqualTo(42L)
        assertThat(claims.jti).isNotBlank()
    }

    @Test
    fun `token signed by another secret is rejected`() {
        val otherService = TokenService(JwtProperties(secret = "another-secret-0123456789abcdef012345678"))
        val foreign = otherService.issueAccessToken(42L, setOf("USER"))

        assertThat(service.verifyBearer(foreign.token)).isNull()
    }

    @Test
    fun `garbage token is rejected without exception`() {
        assertThat(service.verifyBearer("not-a-jwt")).isNull()
    }

    @Test
    fun `access token passed as refresh token is rejected`() {
        val access = service.issueAccessToken(42L, setOf("USER"))

        assertThatThrownBy { service.parseRefreshToken(access.token) }
            .isInstanceOf(InvalidRefreshTokenException::class.java)
    }

    @Test
    fun `too short secret fails fast`() {
        assertThatThrownBy { JwtProperties(secret = "short") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `ttl defaults follow api design`() {
        val properties = JwtProperties(secret = secret)
        assertThat(properties.accessTokenTtl).isEqualTo(Duration.ofMinutes(15))
        assertThat(properties.refreshTokenTtl).isEqualTo(Duration.ofDays(30))
        assertThat(properties.serviceTokenTtl).isEqualTo(Duration.ofHours(24))
    }
}

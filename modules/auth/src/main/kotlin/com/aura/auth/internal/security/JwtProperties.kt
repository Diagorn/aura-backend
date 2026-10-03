package com.aura.auth.internal.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Параметры JWT (см. docs/api-design.md «Аутентификация»).
 * Секрет — из env (JWT_SECRET); HS256 в dev, асимметричные ключи + JWKS — этап hardening.
 */
@ConfigurationProperties(prefix = "aura.security.jwt")
data class JwtProperties(
    val secret: String,
    val accessTokenTtl: Duration = DEFAULT_ACCESS_TTL,
    val refreshTokenTtl: Duration = DEFAULT_REFRESH_TTL,
    val serviceTokenTtl: Duration = DEFAULT_SERVICE_TTL,
) {

    init {
        require(secret.toByteArray(Charsets.UTF_8).size >= MIN_SECRET_BYTES) {
            "aura.security.jwt.secret должен быть не короче $MIN_SECRET_BYTES байт (HS256)"
        }
    }

    val secretKey: SecretKey
        get() = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")

    companion object {
        /** HS256 требует ключ минимум 256 бит. */
        const val MIN_SECRET_BYTES = 32

        val DEFAULT_ACCESS_TTL: Duration = Duration.ofMinutes(15)
        val DEFAULT_REFRESH_TTL: Duration = Duration.ofDays(30)
        val DEFAULT_SERVICE_TTL: Duration = Duration.ofHours(24)
    }
}

package com.aura.auth.internal.security

import com.aura.auth.api.InvalidRefreshTokenException
import com.aura.auth.api.TokenPrincipal
import com.aura.auth.api.TokenType
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.OctetSequenceKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Выданный токен и его срок. */
data class IssuedToken(
    val token: String,
    val jti: String,
    val expiresAt: Instant,
    val expiresIn: Long,
)

/** Разобранный refresh-токен (подпись, срок и тип проверены). */
data class RefreshTokenClaims(
    val jti: String,
    val userId: Long,
)

/**
 * Выпуск и проверка JWT (HS256, секрет из конфигурации).
 * Claims (см. docs/api-design.md): sub, typ (access|refresh|service), iat, exp, jti,
 * roles (USER/ADMIN) для пользовательских токенов, scope=internal — для сервисных.
 */
@Component
class TokenService(properties: JwtProperties) {

    private val encoder: JwtEncoder =
        NimbusJwtEncoder(ImmutableJWKSet<SecurityContext>(JWKSet(hmacJwk(properties))))

    private val decoder: JwtDecoder = NimbusJwtDecoder.withSecretKey(properties.secretKey).build()

    private val accessTtl: Duration = properties.accessTokenTtl
    private val refreshTtl: Duration = properties.refreshTokenTtl
    private val serviceTtl: Duration = properties.serviceTokenTtl

    /** Пользовательский access-токен: sub=id, roles из профиля. */
    fun issueAccessToken(userId: Long, roles: Set<String>): IssuedToken {
        val claims = claimsBuilder(userId.toString(), accessTtl)
            .claim(CLAIM_TYPE, TYPE_ACCESS)
            .claim(CLAIM_ROLES, roles.toList())
            .build()
        return encode(claims)
    }

    /** Refresh-токен: sub=id; хэш сохраняется вызывающим сервисом в auth.refresh_tokens. */
    fun issueRefreshToken(userId: Long): IssuedToken {
        val claims = claimsBuilder(userId.toString(), refreshTtl)
            .claim(CLAIM_TYPE, TYPE_REFRESH)
            .build()
        return encode(claims)
    }

    /** Сервисный токен бота: sub=clientId, scope=internal. */
    fun issueServiceToken(clientId: String): IssuedToken {
        val claims = claimsBuilder(clientId, serviceTtl)
            .claim(CLAIM_TYPE, TYPE_SERVICE)
            .claim(CLAIM_SCOPE, SCOPE_INTERNAL)
            .build()
        return encode(claims)
    }

    /** Проверяет предъявленный Bearer-токен; refresh и мусор аутентификацией не считаются. */
    fun verifyBearer(token: String): TokenPrincipal? {
        val jwt = decodeOrNull(token) ?: return null
        return when (jwt.getClaimAsString(CLAIM_TYPE)) {
            TYPE_ACCESS -> {
                val userId = jwt.subject?.toLongOrNull() ?: return null
                TokenPrincipal(
                    type = TokenType.ACCESS,
                    userId = userId,
                    roles = jwt.getClaimAsStringList(CLAIM_ROLES)?.toSet().orEmpty(),
                    scopes = emptySet(),
                )
            }
            TYPE_SERVICE -> {
                val scope = jwt.getClaimAsString(CLAIM_SCOPE) ?: return null
                TokenPrincipal(
                    type = TokenType.SERVICE,
                    userId = null,
                    roles = emptySet(),
                    scopes = setOf(scope),
                )
            }
            else -> null
        }
    }

    /** Разбирает refresh-токен; неподходящий — [InvalidRefreshTokenException]. */
    fun parseRefreshToken(token: String): RefreshTokenClaims {
        val jwt = decodeOrNull(token)
            ?.takeIf { it.getClaimAsString(CLAIM_TYPE) == TYPE_REFRESH }
            ?: throw InvalidRefreshTokenException()

        val userId = jwt.subject?.toLongOrNull()
        val jti = jwt.id
        if (userId == null || jti == null) {
            throw InvalidRefreshTokenException()
        }
        return RefreshTokenClaims(jti = jti, userId = userId)
    }

    private fun claimsBuilder(subject: String, ttl: Duration) =
        JwtClaimsSet.builder()
            .subject(subject)
            .id(UUID.randomUUID().toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plus(ttl))

    private fun encode(claims: JwtClaimsSet): IssuedToken {
        val header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build()
        val jwt = encoder.encode(JwtEncoderParameters.from(header, claims))
        val expiresAt = requireNotNull(jwt.expiresAt) { "exp обязан быть задан" }
        return IssuedToken(
            token = jwt.tokenValue,
            jti = requireNotNull(jwt.id) { "jti обязан быть задан" },
            expiresAt = expiresAt,
            expiresIn = Duration.between(Instant.now(), expiresAt).seconds,
        )
    }

    private fun decodeOrNull(token: String) =
        try {
            decoder.decode(token)
        } catch (_: JwtException) {
            // подпись/срок/структура — всё это «недействительный токен», подробности клиенту не раскрываем
            null
        }

    companion object {
        const val CLAIM_TYPE = "typ"
        const val CLAIM_ROLES = "roles"
        const val CLAIM_SCOPE = "scope"
        const val TYPE_ACCESS = "access"
        const val TYPE_REFRESH = "refresh"
        const val TYPE_SERVICE = "service"
        const val SCOPE_INTERNAL = "internal"

        private fun hmacJwk(properties: JwtProperties) =
            OctetSequenceKey.Builder(properties.secretKey)
                .keyID("aura-hs256")
                .build()
    }
}

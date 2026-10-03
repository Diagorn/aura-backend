package com.aura.auth.internal.service

import com.aura.auth.api.AuthSession
import com.aura.auth.api.AuthService
import com.aura.auth.api.EmailAlreadyExistsException
import com.aura.auth.api.InvalidCredentialsException
import com.aura.auth.api.InvalidRefreshTokenException
import com.aura.auth.api.LoginCommand
import com.aura.auth.api.RefreshTokenReuseException
import com.aura.auth.api.RegisterCommand
import com.aura.auth.api.UserRegistered
import com.aura.auth.internal.entity.RefreshTokenEntity
import com.aura.auth.internal.entity.UserEntity
import com.aura.auth.internal.event.AuthEventsDispatcher
import com.aura.auth.internal.mapping.toAccountView
import com.aura.auth.internal.repository.RefreshTokenRepository
import com.aura.auth.internal.repository.UserRepository
import com.aura.auth.internal.security.Hashing
import com.aura.auth.internal.security.TokenService
import com.aura.auth.internal.validation.ProfileValidation.requireValidTimezone
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Реализация аутентификации: регистрация, вход, ротация refresh-токенов с reuse detection.
 * Границы транзакций задаются на каждом методе (классовая @Transactional не допускает
 * переопределения noRollbackFor у refresh).
 */
@Service
class AuthServiceImpl(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val tokenService: TokenService,
    private val passwordEncoder: PasswordEncoder,
    private val events: AuthEventsDispatcher,
) : AuthService {

    @Transactional
    override fun register(command: RegisterCommand): AuthSession {
        val timezone = requireValidTimezone(command.timezone ?: DEFAULT_TIMEZONE)
        val user = UserEntity.register(
            email = command.email.trim().lowercase(),
            passwordHash = requireNotNull(passwordEncoder.encode(command.password)) { "BCrypt вернул null" },
            timezone = timezone,
            locale = DEFAULT_LOCALE,
        )
        try {
            // Уникальный индекс auth.users(email) — единственный арбитр гонки параллельных
            // регистраций: нарушение констрейнта при flush внутри транзакции -> 409, а не 500
            userRepository.saveAndFlush(user)
        } catch (_: DataIntegrityViolationException) {
            throw EmailAlreadyExistsException()
        }
        events.userRegistered(UserRegistered(user.id))
        return issueSession(user)
    }

    @Transactional
    override fun login(command: LoginCommand): AuthSession {
        val user = userRepository.findByEmail(command.email.trim().lowercase())
        val matches = user != null && passwordEncoder.matches(command.password, user.passwordHash)
        if (!matches) {
            throw InvalidCredentialsException()
        }
        return issueSession(user)
    }

    /**
     * noRollbackFor обязателен: при reuse метод завершается исключением, но отзыв цепочки
     * (revokeAllActiveForUser) должен сохраниться — обычный rollback отменил бы его.
     */
    @Transactional(noRollbackFor = [RefreshTokenReuseException::class])
    override fun refresh(refreshToken: String): AuthSession {
        tokenService.parseRefreshToken(refreshToken)
        val stored = refreshTokenRepository.getByTokenHash(Hashing.sha256Hex(refreshToken))
        if (!stored.isActive) {
            // reuse detection: предъявлен отозванный токен — отзываем всю цепочку пользователя
            refreshTokenRepository.revokeAllActiveForUser(stored.user.id, Instant.now())
            throw RefreshTokenReuseException()
        }
        stored.revoke(Instant.now())
        return issueSession(stored.user)
    }

    @Transactional
    override fun logout(refreshToken: String) {
        // Идемпотентно: неизвестный или уже отозванный токен — просто 204 без ошибки
        refreshTokenRepository.findByTokenHash(Hashing.sha256Hex(refreshToken))?.revoke(Instant.now())
    }

    private fun issueSession(user: UserEntity): AuthSession {
        val access = tokenService.issueAccessToken(user.id, setOf(user.role.name))
        val refresh = tokenService.issueRefreshToken(user.id)
        refreshTokenRepository.save(
            RefreshTokenEntity(
                user = user,
                tokenHash = Hashing.sha256Hex(refresh.token),
                expiresAt = refresh.expiresAt,
            ),
        )
        return AuthSession(
            user = user.toAccountView(),
            accessToken = access.token,
            refreshToken = refresh.token,
        )
    }

    companion object {
        /** UTC+3 — дефолтная таймзона профиля (IANA-имя). */
        const val DEFAULT_TIMEZONE = "Europe/Moscow"
        const val DEFAULT_LOCALE = "ru"
    }
}

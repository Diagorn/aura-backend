package com.aura.auth.internal.service

import com.aura.auth.api.AccessTokenIssued
import com.aura.auth.api.LinkCodeIssued
import com.aura.auth.api.TelegramAccountLinked
import com.aura.auth.api.TelegramAlreadyLinkedException
import com.aura.auth.api.TelegramExchangeCommand
import com.aura.auth.api.TelegramLinkCodeNotFoundException
import com.aura.auth.api.TelegramLinkCommand
import com.aura.auth.api.TelegramLinkPort
import com.aura.auth.internal.entity.TelegramLinkCodeEntity
import com.aura.auth.internal.event.AuthEventsDispatcher
import com.aura.auth.internal.repository.TelegramLinkCodeRepository
import com.aura.auth.internal.repository.UserRepository
import com.aura.auth.internal.security.TokenService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant

/**
 * Связка аккаунтов с Telegram и обмен «telegram → пользовательский токен»
 * (см. docs/telegram-bot.md «Аутентификация»).
 */
@Service
@Transactional
class TelegramLinkService(
    private val userRepository: UserRepository,
    private val linkCodeRepository: TelegramLinkCodeRepository,
    private val tokenService: TokenService,
    private val events: AuthEventsDispatcher,
) : TelegramLinkPort {

    override fun createLinkCode(userId: Long): LinkCodeIssued {
        val user = userRepository.getUserById(userId)
        if (user.telegramId != null) {
            throw TelegramAlreadyLinkedException()
        }
        // Один активный код на пользователя: выдача нового отзывает предыдущие
        linkCodeRepository.deleteAllByUserId(userId)
        val code = generateCode()
        val expiresAt = Instant.now().plus(Duration.ofMinutes(CODE_TTL_MINUTES))
        linkCodeRepository.save(TelegramLinkCodeEntity(user = user, code = code, expiresAt = expiresAt))
        return LinkCodeIssued(code = code, expiresAt = expiresAt)
    }

    override fun link(command: TelegramLinkCommand) {
        val username = command.username?.takeIf { it.isNotBlank() }
        val stored = linkCodeRepository.findByCode(command.code) ?: throw TelegramLinkCodeNotFoundException()
        if (stored.isExpired) {
            linkCodeRepository.delete(stored)
            throw TelegramLinkCodeNotFoundException()
        }
        val user = stored.user
        if (user.telegramId != null || userRepository.findByTelegramId(command.telegramUserId) != null) {
            throw TelegramAlreadyLinkedException()
        }
        user.linkTelegram(command.telegramUserId, username)
        try {
            // saveAndFlush: уникальный констрейнт uq_users_telegram_id обязан выстрелить здесь,
            // внутри try/catch (гонка параллельной привязки) -> 409, а не 500 при коммите
            userRepository.saveAndFlush(user)
        } catch (_: DataIntegrityViolationException) {
            // гонка: тот же telegram_id параллельно привязали к другому пользователю
            throw TelegramAlreadyLinkedException()
        }
        linkCodeRepository.delete(stored)
        events.telegramAccountLinked(TelegramAccountLinked(userId = user.id, telegramUserId = command.telegramUserId))
    }

    override fun exchange(command: TelegramExchangeCommand): AccessTokenIssued {
        val username = command.username?.takeIf { it.isNotBlank() }
        val user = userRepository.getByTelegramId(command.telegramUserId)
        if (username != null) {
            user.updateTelegramUsername(username)
        }
        val access = tokenService.issueAccessToken(user.id, setOf(user.role.name))
        return AccessTokenIssued(accessToken = access.token, expiresIn = access.expiresIn)
    }

    /** 8 символов без похожих знаков (0/O, 1/I/l) — код человек вводит в боте. */
    private fun generateCode(): String {
        val random = SecureRandom()
        val sb = StringBuilder(CODE_LENGTH)
        repeat(CODE_LENGTH) {
            sb.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)])
        }
        return sb.toString()
    }

    companion object {
        const val CODE_TTL_MINUTES = 10L
        const val CODE_LENGTH = 8
        const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}

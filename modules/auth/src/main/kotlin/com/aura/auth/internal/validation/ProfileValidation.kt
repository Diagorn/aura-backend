package com.aura.auth.internal.validation

import com.aura.shared.ValidationFailedException
import java.time.ZoneId

/** Проверки значений профиля (таймзона/локаль) — единые для регистрации и обновления. */
internal object ProfileValidation {

    private val localePattern = Regex("^[a-zA-Z]{2}(-[a-zA-Z]{2})?$")

    fun requireValidTimezone(timezone: String): String {
        try {
            ZoneId.of(timezone)
        } catch (_: Exception) {
            throw ValidationFailedException("timezone", "Неизвестная IANA-таймзона: $timezone")
        }
        return timezone
    }

    fun requireValidLocale(locale: String): String {
        if (!localePattern.matches(locale)) {
            throw ValidationFailedException("locale", "Локаль должна быть вида ru или en-US")
        }
        return locale.lowercase()
    }
}

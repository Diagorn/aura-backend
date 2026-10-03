package com.aura.shared

/**
 * Базовая API-ошибка домена: несёт HTTP-статус, машинный код и детали,
 * из которых web-слой собирает RFC 9457 problem+json (см. docs/api-design.md).
 *
 * Живёт в shared, чтобы модули не зависели от web-слоя; конкретные подклассы
 * объявляют сами модули в своих `*.api`-пакетах.
 *
 * @param status HTTP-статус ответа
 * @param code машинный код ошибки, например EMAIL_ALREADY_EXISTS
 * @param detail человекочитаемое описание (problem detail)
 * @param typeSlug хвост type-URI (https://aura.example.com/problems/<slug>);
 *   по умолчанию выводится из кода (EMAIL_ALREADY_EXISTS -> email-already-exists)
 * @param fieldErrors построчные детали валидации для расширения `errors`
 */
open class ApiException(
    val status: Int,
    val code: String,
    override val message: String,
    val typeSlug: String = code.lowercase().replace('_', '-'),
    val fieldErrors: List<FieldError> = emptyList(),
) : RuntimeException(message)

/** Деталь валидации отдельного поля (расширение `errors` в problem+json). */
data class FieldError(
    val field: String,
    val message: String,
)

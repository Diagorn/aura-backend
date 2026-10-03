package com.aura.shared

/** Бизнес-валидация не пройдена — 422 VALIDATION_FAILED. */
class ValidationFailedException(
    field: String,
    errorMessage: String,
) : ApiException(
    status = 422,
    code = "VALIDATION_FAILED",
    message = "Некоторые поля некорректны",
    fieldErrors = listOf(FieldError(field, errorMessage)),
)

/** Ресурс не найден (в том числе чужой — существование не раскрывается) — 404 NOT_FOUND. */
class NotFoundException(
    detail: String = "Ресурс не найден",
) : ApiException(
    status = 404,
    code = "NOT_FOUND",
    message = detail,
)

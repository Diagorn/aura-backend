package com.aura.entry.api

import com.aura.shared.ApiException
import com.aura.shared.FieldError

/** Превышен суточный лимит записей (включая черновики) — 409. */
class DailyEntriesLimitExceededException(limit: Int) : ApiException(
    status = 409,
    code = "ENTRY_DAILY_LIMIT_EXCEEDED",
    message = "В сутки можно создать не больше $limit записей (включая черновики)",
)

/** Запрещённый переход статуса (COMPLETED -> DRAFT) — 422 VALIDATION_FAILED. */
class InvalidEntryStatusTransitionException : ApiException(
    status = 422,
    code = "VALIDATION_FAILED",
    message = "Некоторые поля некорректны",
    fieldErrors = listOf(FieldError("status", "переход COMPLETED -> DRAFT запрещён")),
)

/** Некорректные параметры выборки записей (date вместе с from/to и т.п.) — 422. */
class EntryFilterValidationException(errors: List<FieldError>) : ApiException(
    status = 422,
    code = "VALIDATION_FAILED",
    message = "Некоторые поля некорректны",
    fieldErrors = errors,
)

/** Нарушены бизнес-правила состава записи — 422 со списком ошибок по полям. */
class EntryValidationException(errors: List<FieldError>) : ApiException(
    status = 422,
    code = "VALIDATION_FAILED",
    message = "Некоторые поля некорректны",
    fieldErrors = errors,
)

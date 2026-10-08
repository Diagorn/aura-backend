package com.aura.catalog.api

import com.aura.shared.ApiException
import com.aura.shared.FieldError

/** Элемент справочника с таким названием уже есть у пользователя — 409. */
class CatalogItemNameAlreadyExistsException :
    ApiException(
        status = 409,
        code = "NAME_ALREADY_EXISTS",
        message = "Элемент справочника с таким названием уже существует",
    )

/**
 * Действие над системным элементом справочника не разрешено — 403.
 * По умолчанию — правка полей не админом; удаление системных запрещено никем
 * (см. message в точке выброса).
 */
class SystemItemForbiddenException(
    message: String = "Системные элементы справочника может править только администратор",
) : ApiException(
    status = 403,
    code = "SYSTEM_ITEM_FORBIDDEN",
    message = message,
)

/** Некорректная шкала метрики: minValue не меньше maxValue — 422 VALIDATION_FAILED. */
class InvalidMetricScaleException :
    ApiException(
        status = 422,
        code = "VALIDATION_FAILED",
        message = "Некоторые поля некорректны",
        fieldErrors = listOf(FieldError("maxValue", "должно быть больше minValue")),
    )

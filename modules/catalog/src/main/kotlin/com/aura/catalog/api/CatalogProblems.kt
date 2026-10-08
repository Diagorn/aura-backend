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

/** Некорректная шкала метрики: minValue не меньше maxValue — 422 VALIDATION_FAILED. */
class InvalidMetricScaleException :
    ApiException(
        status = 422,
        code = "VALIDATION_FAILED",
        message = "Некоторые поля некорректны",
        fieldErrors = listOf(FieldError("maxValue", "должно быть больше minValue")),
    )

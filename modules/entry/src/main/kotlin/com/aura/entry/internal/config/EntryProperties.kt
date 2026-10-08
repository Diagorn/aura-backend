package com.aura.entry.internal.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Параметры модуля entry. Суточный лимит — защита от абьюза
 * (см. docs/domain-model.md «Ограничение: не более 5 записей на пользователя в сутки»).
 */
@ConfigurationProperties(prefix = "aura.entry")
data class EntryProperties(
    /** Максимум записей в сутки на пользователя, включая черновики. */
    val maxPerDay: Int = 5,
)

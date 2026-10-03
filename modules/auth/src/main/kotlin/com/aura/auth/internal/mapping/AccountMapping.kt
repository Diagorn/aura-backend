package com.aura.auth.internal.mapping

import com.aura.auth.api.AccountView
import com.aura.auth.api.TelegramAccountView
import com.aura.auth.internal.entity.UserEntity

internal fun UserEntity.toAccountView(): AccountView = AccountView(
    id = id,
    email = email,
    timezone = timezone,
    locale = locale,
    telegram = telegramId?.let { TelegramAccountView(userId = it, username = telegramUsername) },
)

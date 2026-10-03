package com.aura.web

import com.aura.api.model.MeResponse
import com.aura.api.model.TelegramProfile
import com.aura.auth.api.AccountView
import com.aura.user.api.UserProfile

/** Маппинг профиля в контракт спеки (общий для AuthApi и MeApi). */
internal fun UserProfile.toMeResponse(): MeResponse = MeResponse(
    id = id,
    email = email,
    timezone = timezone,
    locale = locale,
    telegram = telegram?.let { TelegramProfile(userId = it.userId, username = it.username) },
)

internal fun AccountView.toMeResponse(): MeResponse = MeResponse(
    id = id,
    email = email,
    timezone = timezone,
    locale = locale,
    telegram = telegram?.let { TelegramProfile(userId = it.userId, username = it.username) },
)

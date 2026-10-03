package com.aura.web.me

import com.aura.api.MeApi
import com.aura.api.model.ChangePasswordRequest
import com.aura.api.model.MeResponse
import com.aura.api.model.UpdateMeRequest
import com.aura.security.CurrentUser
import com.aura.user.api.ChangePasswordCommand
import com.aura.user.api.UpdateProfileCommand
import com.aura.user.api.UserProfilePort
import com.aura.web.toMeResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

/**
 * Профиль текущего пользователя (/api/v1/me).
 * Контракт — сгенерированный [MeApi]; данные — через публичный порт модуля user.
 */
@RestController
class MeController(
    private val profiles: UserProfilePort,
) : MeApi {

    override fun getMe(): ResponseEntity<MeResponse> =
        ResponseEntity.ok(profiles.get(CurrentUser.requireUserId()).toMeResponse())

    override fun patchMe(updateMeRequest: UpdateMeRequest): ResponseEntity<MeResponse> =
        ResponseEntity.ok(
            profiles.update(
                CurrentUser.requireUserId(),
                UpdateProfileCommand(timezone = updateMeRequest.timezone, locale = updateMeRequest.locale),
            ).toMeResponse(),
        )

    override fun changePassword(changePasswordRequest: ChangePasswordRequest): ResponseEntity<Unit> {
        profiles.changePassword(
            CurrentUser.requireUserId(),
            ChangePasswordCommand(
                currentPassword = changePasswordRequest.currentPassword,
                newPassword = changePasswordRequest.newPassword,
            ),
        )
        return ResponseEntity.noContent().build()
    }
}

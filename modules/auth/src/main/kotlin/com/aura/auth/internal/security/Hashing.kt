package com.aura.auth.internal.security

import java.security.MessageDigest

/** SHA-256 hex — хэширование refresh-токенов и секретов сервисных клиентов. */
internal object Hashing {

    fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

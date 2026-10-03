package com.aura.auth.api

/**
 * Публичный контракт аутентификации: регистрация, вход, ротация refresh-токенов, выход.
 * Реализация — внутренний сервис модуля auth.
 */
interface AuthService {

    /** Создаёт аккаунт и возвращает первую пару токенов. */
    fun register(command: RegisterCommand): AuthSession

    /** Проверяет креды и выдаёт новую пару токенов. */
    fun login(command: LoginCommand): AuthSession

    /**
     * Ротация: старый refresh-токен отзывается, выдаётся новая пара.
     * Повторное использование отозванного токена отзывает всю цепочку пользователя.
     */
    fun refresh(refreshToken: String): AuthSession

    /** Отзывает refresh-токен. Идемпотентно: неизвестный токен не ошибка. */
    fun logout(refreshToken: String)
}

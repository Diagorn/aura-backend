package com.aura.catalog.api

/**
 * Публичный контракт событий: список и CRUD персональных событий.
 * Системных пресетов для событий нет — все события персональны.
 * Реализация — внутренний сервис модуля catalog.
 */
interface EventsPort {

    /** События пользователя, отсортированные по sortOrder. */
    fun list(userId: Long, includeInactive: Boolean): List<CatalogEvent>

    /** Создаёт персональное событие; дубль имени — [CatalogItemNameAlreadyExistsException]. */
    fun create(userId: Long, command: CreateCatalogEvent): CatalogEvent

    /** Правит своё событие (PATCH: null поле не менять); чужое/несуществующее — [com.aura.shared.NotFoundException]. */
    fun update(userId: Long, id: Long, command: UpdateCatalogEvent): CatalogEvent

    /** Удаляет своё событие; чужое/несуществующее — [com.aura.shared.NotFoundException]. */
    fun delete(userId: Long, id: Long)
}

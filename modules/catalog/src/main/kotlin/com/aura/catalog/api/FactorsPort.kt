package com.aura.catalog.api

/**
 * Публичный контракт факторов: список (системные + персональные) и CRUD персональных.
 * Реализация — внутренний сервис модуля catalog.
 */
interface FactorsPort {

    /** Видимые пользователю факторы, отсортированные по sortOrder. */
    fun list(userId: Long, includeInactive: Boolean): List<Factor>

    /** Создаёт персональный фактор; дубль имени — [CatalogItemNameAlreadyExistsException]. */
    fun create(userId: Long, command: CreateFactor): Factor

    /** Правит свой фактор (PATCH: null поле не менять); чужой/несуществующий — [com.aura.shared.NotFoundException].
     *  Системный: [isAdmin] — правка полей системной строки; иначе только {isActive} — скрыть/вернуть для себя,
     *  любые другие поля — [SystemItemForbiddenException]. */
    fun update(userId: Long, id: Long, command: UpdateFactor, isAdmin: Boolean): Factor

    /** Удаляет свой фактор; чужой/несуществующий — [com.aura.shared.NotFoundException]. */
    fun delete(userId: Long, id: Long)
}

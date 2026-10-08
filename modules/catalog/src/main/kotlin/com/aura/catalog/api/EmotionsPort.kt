package com.aura.catalog.api

/**
 * Публичный контракт эмоций: список (системные + персональные) и CRUD персональных.
 * Реализация — внутренний сервис модуля catalog.
 */
interface EmotionsPort {

    /** Видимые пользователю эмоции, отсортированные по sortOrder. */
    fun list(userId: Long, includeInactive: Boolean): List<Emotion>

    /** Создаёт персональную эмоцию; дубль имени — [CatalogItemNameAlreadyExistsException]. */
    fun create(userId: Long, command: CreateEmotion): Emotion

    /** Правит свою эмоцию (PATCH: null поле не менять); чужая/несуществующая — [com.aura.shared.NotFoundException].
     *  Системная: [isAdmin] — правка полей системной строки; иначе только {isActive} — скрыть/вернуть для себя,
     *  любые другие поля — [SystemItemForbiddenException]. */
    fun update(userId: Long, id: Long, command: UpdateEmotion, isAdmin: Boolean): Emotion

    /** Удаляет свою эмоцию; чужая/несуществующая — [com.aura.shared.NotFoundException]. */
    fun delete(userId: Long, id: Long)
}

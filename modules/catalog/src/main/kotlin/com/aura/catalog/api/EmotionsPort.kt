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

    /**
     * Эмоции по id для использования в записях: только системные и личные данного
     * пользователя; отсутствующий в результате id не существует или чужой — вызывающий
     * сам превращает это в 404/422 своего контекста. Активность ([Emotion.isActive])
     * тоже проверяет вызывающий: модуль catalog не знает контекста использования,
     * пер-пользовательское скрытие системных на запись не влияет.
     */
    fun findVisibleByIds(userId: Long, ids: Collection<Long>): List<Emotion>
}

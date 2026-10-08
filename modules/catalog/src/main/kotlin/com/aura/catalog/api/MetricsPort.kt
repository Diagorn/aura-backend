package com.aura.catalog.api

/**
 * Публичный контракт отслеживаемых метрик: список (системные + персональные) и CRUD персональных.
 * Реализация — внутренний сервис модуля catalog.
 */
interface MetricsPort {

    /** Видимые пользователю метрики, отсортированные по sortOrder. */
    fun list(userId: Long, includeInactive: Boolean): List<TrackedMetric>

    /** Создаёт персональную метрику; дубль имени — [CatalogItemNameAlreadyExistsException],
     *  некорректная шкала — [InvalidMetricScaleException]. */
    fun create(userId: Long, command: CreateTrackedMetric): TrackedMetric

    /** Правит свою метрику (PATCH: null поле не менять); чужая/несуществующая — [com.aura.shared.NotFoundException].
     *  Системная: [isAdmin] — правка полей системной строки; иначе только {isActive} — скрыть/вернуть для себя,
     *  любые другие поля — [SystemItemForbiddenException]. */
    fun update(userId: Long, id: Long, command: UpdateTrackedMetric, isAdmin: Boolean): TrackedMetric

    /** Удаляет свою метрику; чужая/несуществующая — [com.aura.shared.NotFoundException]. */
    fun delete(userId: Long, id: Long)
}

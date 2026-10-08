package com.aura.entry.internal.repository

import com.aura.entry.api.EntrySource
import com.aura.entry.api.EntryStatus
import com.aura.entry.internal.entity.EntryEntity
import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDate

/**
 * Динамические предикаты списка записей: владелец + опциональные границы дат
 * (включительно) и фильтры status/source. Спецификации вместо nullable-параметров
 * в @Query: PostgreSQL не выводит тип параметра из `? is null` (could not determine
 * data type), а здесь все переданные значения строго типизированы, null просто
 * не добавляет предикат.
 */
internal object EntrySpecifications {

    fun listPage(
        userId: Long,
        from: LocalDate?,
        to: LocalDate?,
        status: EntryStatus?,
        source: EntrySource?,
    ): Specification<EntryEntity> = Specification { root, query, cb ->
        val predicates = mutableListOf<Predicate>(
            cb.equal(root.get<Long>("userId"), userId),
        )
        from?.let { predicates += cb.greaterThanOrEqualTo(root.get("entryDate"), it) }
        to?.let { predicates += cb.lessThanOrEqualTo(root.get("entryDate"), it) }
        status?.let { predicates += cb.equal(root.get<EntryStatus>("status"), it) }
        source?.let { predicates += cb.equal(root.get<EntrySource>("source"), it) }
        cb.and(*predicates.toTypedArray())
    }
}

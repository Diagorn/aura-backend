package com.aura.entry.internal.repository

import com.aura.entry.api.EntryStatus
import com.aura.entry.internal.entity.EntryEntity
import com.aura.shared.NotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface EntryRepository : JpaRepository<EntryEntity, Long>, JpaSpecificationExecutor<EntryEntity> {

    /** Сколько записей (включая черновики) уже создано за дату — для суточного лимита. */
    fun countByUserIdAndEntryDate(userId: Long, entryDate: LocalDate): Long

    /**
     * Агрегаты календаря: на каждую дату диапазона — число записей и число завершённых.
     * Один запрос по индексу (user_id, entry_date, status).
     */
    @Query(
        """
        select e.entryDate as entryDate, count(e.id) as total,
               sum(case when e.status = :completed then 1 else 0 end) as completed
        from EntryEntity e
        where e.userId = :userId and e.entryDate between :from and :to
        group by e.entryDate
        order by e.entryDate asc
        """,
    )
    fun aggregateByDate(
        @Param("userId") userId: Long,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
        @Param("completed") completed: EntryStatus = EntryStatus.COMPLETED,
    ): List<EntryDateAggregate>

    /** Забирает запись по id и проверяет владельца; чужая/несуществующая — [NotFoundException]. */
    fun requireOwned(userId: Long, id: Long): EntryEntity =
        findById(id).orElseThrow { NotFoundException("Запись не найдена") }
            .takeIf { it.userId == userId }
            ?: throw NotFoundException("Запись не найдена")
}

/** Строка агрегата календаря (interface projection). */
interface EntryDateAggregate {
    val entryDate: LocalDate
    val total: Long
    val completed: Long
}

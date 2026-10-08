package com.aura.catalog.internal.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Фактор — таблица catalog.factors.
 * Паттерн «системное + персональное»: ownerUserId == null — системный пресет,
 * общий для всех пользователей; иначе — персональный элемент владельца.
 */
@Entity
@Table(name = "factors", schema = "catalog")
class FactorEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    /** null — системный пресет; иначе id пользователя-владельца. */
    @Column(name = "owner_user_id")
    val ownerUserId: Long?,
    @Column(nullable = false, length = 100)
    var name: String,
    @Column(length = 128)
    var icon: String?,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean,
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int,
)

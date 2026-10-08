package com.aura.catalog.internal.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Персональное скрытие системного элемента справочника — таблица catalog.user_hidden_items.
 * Системные строки общие, поэтому «деактивировать» их в самой строке нельзя: факт скрытия
 * хранится отдельно (user_id + item_type + item_id) и влияет только на листинг этого пользователя.
 * Ссылки на справочники — по id без FK внутри схемы (правило границ), целостность — в сервисе.
 */
@Entity
@Table(name = "user_hidden_items", schema = "catalog")
class UserHiddenItemEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 16)
    val itemType: CatalogItemType,
    @Column(name = "item_id", nullable = false)
    val itemId: Long,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)

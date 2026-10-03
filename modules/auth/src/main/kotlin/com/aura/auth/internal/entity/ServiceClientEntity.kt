package com.aura.auth.internal.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

/**
 * Доверенный клиент внутреннего контура (сегодня — Telegram-бот).
 * Креды инициализируются seed-чейнджлогом; секрет хранится как SHA-256 hex.
 */
@Entity
@Table(name = "service_clients", schema = "auth")
class ServiceClientEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "client_id", nullable = false, unique = true, length = 64)
    val clientId: String,
    /** SHA-256 hex клиентского секрета. */
    @Column(name = "client_secret_hash", nullable = false, length = 64)
    val clientSecretHash: String,
    @Column(nullable = false, length = 255)
    val description: String,
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)

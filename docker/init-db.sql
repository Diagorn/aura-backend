-- Схемы для модульного монолита (см. docs/liquibase-migrations.md).
-- Таблицы создаёт Liquibase; здесь — только схемы.
CREATE SCHEMA IF NOT EXISTS liquibase;
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS catalog;
CREATE SCHEMA IF NOT EXISTS entry;
CREATE SCHEMA IF NOT EXISTS note;
CREATE SCHEMA IF NOT EXISTS notification;

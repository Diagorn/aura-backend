-- Схемы для Testcontainers: liquibase хранит DATABASECHANGELOG в своей схеме,
-- дальше -- по схеме на модуль (см. docker/init-db.sql и docs/liquibase-migrations.md).
CREATE SCHEMA IF NOT EXISTS liquibase;
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS catalog;
CREATE SCHEMA IF NOT EXISTS entry;
CREATE SCHEMA IF NOT EXISTS note;
CREATE SCHEMA IF NOT EXISTS notification;

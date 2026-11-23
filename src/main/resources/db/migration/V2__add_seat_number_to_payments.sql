-- =============================================================================
-- Migration: V2__add_seat_number_to_payments.sql
-- Description: Añade el campo seat_number a la tabla payments para la
--              integración de liberación de asientos con Grupo 3 (Johan Almeida).
-- Dialect: Microsoft SQL Server (Transact-SQL)
-- =============================================================================

ALTER TABLE payments ADD seat_number INT NULL;

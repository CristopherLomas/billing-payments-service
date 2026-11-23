-- =============================================================================
-- Migration: V1__init_payments_schema.sql
-- Description: Schema initialization for billing-payments-service (Grupo 5)
-- Dialect: Microsoft SQL Server (Transact-SQL)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Table: payments
-- -----------------------------------------------------------------------------
CREATE TABLE payments (
    id UNIQUEIDENTIFIER NOT NULL,
    reservation_id NVARCHAR(100) NOT NULL,
    trip_id NVARCHAR(100) NOT NULL,
    user_id NVARCHAR(100) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    payment_method NVARCHAR(30) NOT NULL,
    reference_number NVARCHAR(100) NULL,
    status NVARCHAR(30) NOT NULL,
    reviewed_by NVARCHAR(100) NULL,
    reviewed_at DATETIMEOFFSET NULL,
    rejection_reason NVARCHAR(500) NULL,
    created_at DATETIMEOFFSET NOT NULL,
    updated_at DATETIMEOFFSET NOT NULL,

    CONSTRAINT PK_payments PRIMARY KEY (id),
    CONSTRAINT CK_payments_amount CHECK (amount > 0),
    CONSTRAINT CK_payments_method CHECK (payment_method IN ('BANK_TRANSFER', 'DEUNA', 'CASH')),
    CONSTRAINT CK_payments_status CHECK (status IN ('PENDING_VERIFICATION', 'PENDING_CASH', 'PAID', 'REJECTED'))
);

-- Unique filtered index for reference_number in SQL Server:
-- Avoids duplicate payment slips for transfers/Deuna, allows multiple NULLs for cash
CREATE UNIQUE INDEX UX_payments_reference_number
ON payments(reference_number)
WHERE reference_number IS NOT NULL;

-- Query performance indexes
CREATE INDEX IX_payments_status ON payments(status);
CREATE INDEX IX_payments_user_id ON payments(user_id);
CREATE INDEX IX_payments_reservation_id ON payments(reservation_id);
CREATE INDEX IX_payments_created_at ON payments(created_at);

-- -----------------------------------------------------------------------------
-- 2. Table: payment_audit_logs
-- -----------------------------------------------------------------------------
CREATE TABLE payment_audit_logs (
    id UNIQUEIDENTIFIER NOT NULL,
    payment_id UNIQUEIDENTIFIER NOT NULL,
    previous_status NVARCHAR(30) NULL,
    new_status NVARCHAR(30) NOT NULL,
    changed_by NVARCHAR(100) NOT NULL,
    notes NVARCHAR(500) NULL,
    changed_at DATETIMEOFFSET NOT NULL,

    CONSTRAINT PK_payment_audit_logs PRIMARY KEY (id),
    CONSTRAINT FK_audit_payments FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE CASCADE
);

-- Audit log indexes
CREATE INDEX IX_payment_audit_logs_payment_id ON payment_audit_logs(payment_id);
CREATE INDEX IX_payment_audit_logs_changed_at ON payment_audit_logs(changed_at);

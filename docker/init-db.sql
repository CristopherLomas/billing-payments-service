IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = N'Payments_DB')
BEGIN
    CREATE DATABASE [Payments_DB];
    PRINT 'Database Payments_DB created successfully.';
END
ELSE
BEGIN
    PRINT 'Database Payments_DB already exists.';
END
GO

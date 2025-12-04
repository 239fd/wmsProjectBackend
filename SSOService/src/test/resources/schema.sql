-- Schema for H2 database (integration tests)
-- Эмуляция PostgreSQL типов для H2

-- Создаем таблицу user_read_model
CREATE TABLE IF NOT EXISTS user_read_model (
    user_id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255),
    provider VARCHAR(50) NOT NULL,
    photo BLOB,
    organization_id UUID,
    warehouse_id UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Создаем таблицу user_event (Event Sourcing)
CREATE TABLE IF NOT EXISTS user_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload CLOB,
    created_at TIMESTAMP NOT NULL
);

-- Создаем таблицу login_audit
CREATE TABLE IF NOT EXISTS login_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id UUID NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL
);

-- Создаем таблицу oauth_pending_registration
CREATE TABLE IF NOT EXISTS oauth_pending_registration (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    provider VARCHAR(50) NOT NULL,
    provider_user_id VARCHAR(255),
    photo_url VARCHAR(500),
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- Индексы
CREATE INDEX IF NOT EXISTS idx_user_email ON user_read_model(email);
CREATE INDEX IF NOT EXISTS idx_user_event_user_id ON user_event(user_id);
CREATE INDEX IF NOT EXISTS idx_login_audit_user_id ON login_audit(user_id);


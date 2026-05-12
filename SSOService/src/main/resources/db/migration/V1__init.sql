CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

DO $$ BEGIN
    CREATE TYPE user_role AS ENUM ('WORKER', 'ACCOUNTANT', 'DIRECTOR');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE auth_provider AS ENUM ('LOCAL', 'GOOGLE', 'YANDEX');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

CREATE TABLE IF NOT EXISTS user_events (
    event_id        SERIAL PRIMARY KEY,
    user_id         UUID NOT NULL,
    event_type      VARCHAR(50) NOT NULL,
    event_data      JSONB NOT NULL,
    event_version   INT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_user_events_user_id ON user_events(user_id);

CREATE TABLE IF NOT EXISTS user_read_model (
    user_id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    full_name       VARCHAR(255) NOT NULL,
    role            user_role NOT NULL,
    password_hash   VARCHAR(255),
    provider        auth_provider NOT NULL,
    photo           BYTEA,
    organization_id UUID,
    warehouse_id    UUID,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS login_audit (
    id                  SERIAL PRIMARY KEY,
    user_id             UUID NOT NULL,
    login_at            TIMESTAMP NOT NULL DEFAULT now(),
    ip_address          INET,
    user_agent          VARCHAR(512),
    provider            auth_provider NOT NULL,
    refresh_token_hash  VARCHAR(60) UNIQUE,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    logout_at           TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_login_audit_user_id ON login_audit(user_id);
CREATE INDEX IF NOT EXISTS idx_login_audit_refresh_token_hash ON login_audit(refresh_token_hash);

CREATE TABLE IF NOT EXISTS oauth_pending_registrations (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    temporary_token     VARCHAR(255) NOT NULL UNIQUE,
    email               VARCHAR(255) NOT NULL,
    full_name           VARCHAR(255),
    provider            VARCHAR(20) NOT NULL,
    provider_uid        VARCHAR(255) NOT NULL,
    photo               BYTEA,
    state_token         VARCHAR(255),
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    expires_at          TIMESTAMP NOT NULL,
    completed           BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_oauth_pending_temporary_token ON oauth_pending_registrations(temporary_token);

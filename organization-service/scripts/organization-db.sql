CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TYPE org_status AS ENUM ('ACTIVE', 'BLOCKED', 'ARCHIVED');

CREATE TABLE organization_read_model (
    org_id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(255) NOT NULL UNIQUE,
    short_name      VARCHAR(100),
    unp             VARCHAR(20) NOT NULL UNIQUE,
    address         VARCHAR(512),
    status          org_status NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE organization_events (
    event_id        BIGSERIAL PRIMARY KEY,
    org_id          UUID NOT NULL,
    event_type      VARCHAR(50) NOT NULL,
    event_data      JSONB NOT NULL,
    event_version   INT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE organization_invitation_codes (
    code_id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    org_id          UUID NOT NULL REFERENCES organization_read_model(org_id) ON DELETE CASCADE,
    warehouse_id    UUID,
    invitation_code VARCHAR(64) NOT NULL UNIQUE,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    expires_at      TIMESTAMP NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_invitation_code ON organization_invitation_codes(invitation_code) WHERE is_active = TRUE;

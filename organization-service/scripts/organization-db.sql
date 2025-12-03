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

CREATE TABLE organization_employees (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            user_id UUID NOT NULL,
            org_id UUID NOT NULL,
            role VARCHAR(50) NOT NULL,
            joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            removed_at TIMESTAMP,
            is_active BOOLEAN NOT NULL DEFAULT true,

            CONSTRAINT fk_org_employee_org FOREIGN KEY (org_id)
                REFERENCES organization_read_model(org_id) ON DELETE CASCADE,
            CONSTRAINT uk_user_org UNIQUE (user_id, org_id)
);

CREATE INDEX idx_org_employees_org_id ON organization_employees(org_id);
CREATE INDEX idx_org_employees_user_id ON organization_employees(user_id);
CREATE INDEX idx_org_employees_is_active ON organization_employees(is_active);

CREATE INDEX idx_invitation_code ON organization_invitation_codes(invitation_code) WHERE is_active = TRUE;

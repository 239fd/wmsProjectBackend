CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS product_read_model (
    product_id      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(255) NOT NULL,
    sku             VARCHAR(100) UNIQUE,
    barcode         VARCHAR(100),
    category        VARCHAR(100),
    description     TEXT,
    unit_of_measure VARCHAR(50),
    weight_kg       NUMERIC(10, 2),
    volume_m3       NUMERIC(10, 3),
    price           NUMERIC(12, 2),
    abc_class       CHAR(1),
    organization_id UUID,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_product_org_id ON product_read_model(organization_id);
CREATE INDEX IF NOT EXISTS idx_product_read_model_sku ON product_read_model(sku);
CREATE INDEX IF NOT EXISTS idx_product_read_model_barcode ON product_read_model(barcode);
CREATE INDEX IF NOT EXISTS idx_product_read_model_category ON product_read_model(category);

CREATE TABLE IF NOT EXISTS product_events (
    event_id        BIGSERIAL PRIMARY KEY,
    product_id      UUID NOT NULL,
    event_type      VARCHAR(50) NOT NULL,
    event_data      JSONB NOT NULL,
    event_version   INT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_product_events_product_id ON product_events(product_id);

CREATE TABLE IF NOT EXISTS product_batch (
    batch_id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id       UUID NOT NULL,
    batch_number     VARCHAR(100),
    manufacture_date DATE,
    expiry_date      DATE,
    supplier         VARCHAR(255),
    purchase_price   NUMERIC(12, 2),
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_product_batch_product_id ON product_batch(product_id);
CREATE INDEX IF NOT EXISTS idx_product_batch_expiry_date ON product_batch(expiry_date);

CREATE TABLE IF NOT EXISTS inventory (
    inventory_id      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id        UUID NOT NULL,
    batch_id          UUID,
    warehouse_id      UUID NOT NULL,
    cell_id           UUID,
    quantity          NUMERIC(12, 3) NOT NULL,
    reserved_quantity NUMERIC(12, 3) NOT NULL DEFAULT 0,
    status            VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE',
    last_updated      TIMESTAMP NOT NULL DEFAULT now(),
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_inventory_product_batch_warehouse_cell UNIQUE (product_id, batch_id, warehouse_id, cell_id)
);

CREATE INDEX IF NOT EXISTS idx_inventory_product_id ON inventory(product_id);
CREATE INDEX IF NOT EXISTS idx_inventory_warehouse_id ON inventory(warehouse_id);
CREATE INDEX IF NOT EXISTS idx_inventory_cell_id ON inventory(cell_id);

CREATE TABLE IF NOT EXISTS inventory_session (
    session_id      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    warehouse_id    UUID NOT NULL,
    started_by      UUID NOT NULL,
    started_at      TIMESTAMP NOT NULL DEFAULT now(),
    completed_at    TIMESTAMP,
    status          VARCHAR(50) NOT NULL,
    notes           VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_inventory_session_warehouse_id ON inventory_session(warehouse_id);
CREATE INDEX IF NOT EXISTS idx_inventory_session_status ON inventory_session(status);

CREATE TABLE IF NOT EXISTS inventory_count (
    count_id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id        UUID NOT NULL REFERENCES inventory_session(session_id) ON DELETE CASCADE,
    product_id        UUID NOT NULL,
    batch_id          UUID,
    cell_id           UUID,
    warehouse_id      UUID NOT NULL,
    expected_quantity NUMERIC(12, 3) NOT NULL,
    actual_quantity   NUMERIC(12, 3),
    discrepancy       NUMERIC(12, 3),
    notes             VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_inventory_count_session_id ON inventory_count(session_id);

CREATE TABLE IF NOT EXISTS product_operation (
    operation_id    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    operation_type  VARCHAR(50) NOT NULL,
    product_id      UUID NOT NULL,
    batch_id        UUID,
    warehouse_id    UUID NOT NULL,
    from_cell_id    UUID,
    to_cell_id      UUID,
    quantity        NUMERIC(12, 3) NOT NULL,
    user_id         UUID NOT NULL,
    document_id     UUID,
    supply_id       UUID,
    operation_date  TIMESTAMP NOT NULL DEFAULT now(),
    notes           TEXT
);

CREATE INDEX IF NOT EXISTS idx_product_operation_product_id ON product_operation(product_id);
CREATE INDEX IF NOT EXISTS idx_product_operation_warehouse_id ON product_operation(warehouse_id);
CREATE INDEX IF NOT EXISTS idx_product_operation_operation_date ON product_operation(operation_date);

CREATE TABLE IF NOT EXISTS suppliers (
    supplier_id     UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(255) NOT NULL,
    unp             VARCHAR(20),
    contact_person  VARCHAR(255),
    phone           VARCHAR(50),
    email           VARCHAR(255),
    address         VARCHAR(512),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_suppliers_name ON suppliers(name);
CREATE INDEX IF NOT EXISTS idx_suppliers_unp ON suppliers(unp);

DO $$ BEGIN
    CREATE TYPE supply_status AS ENUM ('PLANNED', 'IN_PROGRESS', 'ACCEPTED', 'REJECTED', 'CANCELLED');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

CREATE TABLE IF NOT EXISTS supplies (
    supply_id       UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID,
    supplier_id     UUID REFERENCES suppliers(supplier_id),
    warehouse_id    UUID NOT NULL,
    status          supply_status NOT NULL DEFAULT 'PLANNED',
    expected_date   DATE,
    actual_date     DATE,
    total_items     INT NOT NULL DEFAULT 0,
    notes           TEXT,
    created_by      UUID NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_supplies_organization_id ON supplies(organization_id);
CREATE INDEX IF NOT EXISTS idx_supplies_supplier_id ON supplies(supplier_id);
CREATE INDEX IF NOT EXISTS idx_supplies_warehouse_id ON supplies(warehouse_id);
CREATE INDEX IF NOT EXISTS idx_supplies_status ON supplies(status);
CREATE INDEX IF NOT EXISTS idx_supplies_expected_date ON supplies(expected_date);

CREATE TABLE IF NOT EXISTS supply_items (
    item_id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    supply_id       UUID NOT NULL REFERENCES supplies(supply_id) ON DELETE CASCADE,
    product_id      UUID NOT NULL,
    expected_qty    NUMERIC(12, 3) NOT NULL,
    actual_qty      NUMERIC(12, 3),
    unit_price      NUMERIC(12, 2),
    notes           VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_supply_items_supply_id ON supply_items(supply_id);
CREATE INDEX IF NOT EXISTS idx_supply_items_product_id ON supply_items(product_id);

DO $$ BEGIN
    CREATE TYPE saga_type AS ENUM ('RECEIVE', 'SHIP');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE saga_status AS ENUM ('PENDING', 'COMPLETED', 'FAILED', 'COMPENSATING', 'COMPENSATED', 'COMPENSATION_FAILED');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

CREATE TABLE IF NOT EXISTS saga_state (
    saga_id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    saga_type       saga_type NOT NULL,
    status          saga_status NOT NULL DEFAULT 'PENDING',
    current_step    VARCHAR(50) NOT NULL,
    payload         JSONB NOT NULL,
    failure_reason  TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_saga_state_type ON saga_state(saga_type);
CREATE INDEX IF NOT EXISTS idx_saga_state_status ON saga_state(status);
CREATE INDEX IF NOT EXISTS idx_saga_state_created_at ON saga_state(created_at);

CREATE TABLE IF NOT EXISTS planned_deliveries (
    delivery_id       UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    external_id       VARCHAR(100) NOT NULL UNIQUE,
    supplier_name     VARCHAR(255),
    product_name      VARCHAR(255),
    expected_quantity INT,
    expected_date     DATE,
    warehouse_id      UUID,
    source            VARCHAR(50),
    extracted_at      TIMESTAMP NOT NULL DEFAULT now(),
    processed         BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_planned_deliveries_external_id ON planned_deliveries(external_id);
CREATE INDEX IF NOT EXISTS idx_planned_deliveries_expected_date ON planned_deliveries(expected_date);

CREATE TABLE IF NOT EXISTS extraction_log (
    log_id          BIGSERIAL PRIMARY KEY,
    source          VARCHAR(50) NOT NULL,
    extracted_at    TIMESTAMP NOT NULL DEFAULT now(),
    records_found   INT,
    records_new     INT,
    success         BOOLEAN NOT NULL,
    error_message   TEXT
);

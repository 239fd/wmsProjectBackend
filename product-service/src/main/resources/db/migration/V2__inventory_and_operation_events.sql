CREATE TABLE IF NOT EXISTS inventory_events (
    event_id        BIGSERIAL PRIMARY KEY,
    inventory_id    UUID NOT NULL,
    event_type      VARCHAR(50) NOT NULL,
    event_data      JSONB NOT NULL,
    event_version   INT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_inventory_events_inventory_id ON inventory_events(inventory_id);
CREATE INDEX IF NOT EXISTS idx_inventory_events_created_at ON inventory_events(created_at);

CREATE TABLE IF NOT EXISTS product_operation_events (
    event_id        BIGSERIAL PRIMARY KEY,
    operation_id    UUID NOT NULL,
    event_type      VARCHAR(50) NOT NULL,
    event_data      JSONB NOT NULL,
    event_version   INT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_operation_events_operation_id ON product_operation_events(operation_id);
CREATE INDEX IF NOT EXISTS idx_operation_events_created_at ON product_operation_events(created_at);

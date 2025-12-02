CREATE EXTENSION IF NOT EXISTS "uuid-ossp";


CREATE TYPE inventory_status AS ENUM ('AVAILABLE', 'RESERVED', 'DAMAGED', 'EXPIRED', 'IN_TRANSIT');
CREATE TYPE operation_type AS ENUM ('RECEIPT', 'SHIPMENT', 'TRANSFER', 'WRITE_OFF', 'REVALUATION', 'INVENTORY');


CREATE TABLE product_read_model (
    product_id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name                VARCHAR(255) NOT NULL,
    sku                 VARCHAR(100) UNIQUE,
    barcode             VARCHAR(100),
    category            VARCHAR(100),
    description         TEXT,
    unit_of_measure     VARCHAR(50),
    weight_kg           NUMERIC(10,2),
    volume_m3           NUMERIC(10,3),
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_product_sku ON product_read_model(sku);
CREATE INDEX idx_product_barcode ON product_read_model(barcode);
CREATE INDEX idx_product_category ON product_read_model(category);


CREATE TABLE product_events (
    event_id        BIGSERIAL PRIMARY KEY,
    product_id      UUID NOT NULL,
    event_type      VARCHAR(50) NOT NULL,
    event_data      JSONB NOT NULL,
    event_version   INT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_product_events_product_id ON product_events(product_id);
CREATE INDEX idx_product_events_created_at ON product_events(created_at);


CREATE TABLE product_batch (
    batch_id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id          UUID NOT NULL REFERENCES product_read_model(product_id) ON DELETE CASCADE,
    batch_number        VARCHAR(100),
    manufacture_date    DATE,
    expiry_date         DATE,
    supplier            VARCHAR(255),
    purchase_price      NUMERIC(12,2),
    created_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_batch_product_id ON product_batch(product_id);
CREATE INDEX idx_batch_expiry_date ON product_batch(expiry_date);


CREATE TABLE inventory (
    inventory_id        UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id          UUID NOT NULL REFERENCES product_read_model(product_id) ON DELETE CASCADE,
    batch_id            UUID REFERENCES product_batch(batch_id) ON DELETE SET NULL,
    warehouse_id        UUID NOT NULL,
    cell_id             UUID,
    quantity            NUMERIC(12,3) NOT NULL DEFAULT 0,
    reserved_quantity   NUMERIC(12,3) NOT NULL DEFAULT 0,
    status              VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE',
    last_updated        TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_inventory_product_id ON inventory(product_id);
CREATE INDEX idx_inventory_warehouse_id ON inventory(warehouse_id);
CREATE INDEX idx_inventory_cell_id ON inventory(cell_id);
CREATE INDEX idx_inventory_status ON inventory(status);


CREATE TABLE product_operation (
    operation_id        UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    operation_type      VARCHAR(50) NOT NULL,
    product_id          UUID NOT NULL REFERENCES product_read_model(product_id) ON DELETE CASCADE,
    batch_id            UUID REFERENCES product_batch(batch_id) ON DELETE SET NULL,
    warehouse_id        UUID NOT NULL,
    from_cell_id        UUID,
    to_cell_id          UUID,
    quantity            NUMERIC(12,3) NOT NULL,
    user_id             UUID NOT NULL,
    document_id         UUID,
    operation_date      TIMESTAMP NOT NULL DEFAULT now(),
    notes               TEXT
);

CREATE INDEX idx_operation_product_id ON product_operation(product_id);
CREATE INDEX idx_operation_warehouse_id ON product_operation(warehouse_id);
CREATE INDEX idx_operation_type ON product_operation(operation_type);
CREATE INDEX idx_operation_date ON product_operation(operation_date);
CREATE INDEX idx_operation_user_id ON product_operation(user_id);

CREATE TYPE session_status AS ENUM ('IN_PROGRESS', 'COMPLETED', 'CANCELLED');

CREATE TABLE inventory_session (
    session_id      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    warehouse_id    UUID NOT NULL,
    started_by      UUID NOT NULL,
    started_at      TIMESTAMP NOT NULL DEFAULT now(),
    completed_at    TIMESTAMP,
    status          session_status NOT NULL DEFAULT 'IN_PROGRESS',
    notes           TEXT
);

CREATE INDEX idx_inventory_session_warehouse ON inventory_session(warehouse_id);
CREATE INDEX idx_inventory_session_status ON inventory_session(status);
CREATE INDEX idx_inventory_session_started_at ON inventory_session(started_at);

CREATE TABLE inventory_count (
    count_id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id          UUID NOT NULL REFERENCES inventory_session(session_id) ON DELETE CASCADE,
    product_id          UUID NOT NULL,
    batch_id            UUID,
    cell_id             UUID,
    expected_quantity   NUMERIC(12, 3) NOT NULL,
    actual_quantity     NUMERIC(12, 3),
    discrepancy         NUMERIC(12, 3) DEFAULT 0,
    notes               TEXT
);

CREATE INDEX idx_inventory_count_session ON inventory_count(session_id);
CREATE INDEX idx_inventory_count_product ON inventory_count(product_id);
CREATE INDEX idx_inventory_count_batch ON inventory_count(batch_id);
CREATE INDEX idx_inventory_count_cell ON inventory_count(cell_id);
CREATE INDEX idx_inventory_count_discrepancy ON inventory_count(discrepancy) WHERE discrepancy != 0;


COMMENT ON TABLE inventory_session IS 'Сессии инвентаризации складских запасов';
COMMENT ON TABLE inventory_count IS 'Записи подсчёта товаров при инвентаризации';
COMMENT ON COLUMN inventory_session.session_id IS 'Уникальный идентификатор сессии';
COMMENT ON COLUMN inventory_session.warehouse_id IS 'ID склада на котором проводится инвентаризация';
COMMENT ON COLUMN inventory_session.started_by IS 'ID пользователя который начал инвентаризацию';
COMMENT ON COLUMN inventory_session.status IS 'Статус сессии: IN_PROGRESS, COMPLETED, CANCELLED';
COMMENT ON COLUMN inventory_count.expected_quantity IS 'Ожидаемое количество (из системы)';
COMMENT ON COLUMN inventory_count.actual_quantity IS 'Фактическое количество (подсчитано)';
COMMENT ON COLUMN inventory_count.discrepancy IS 'Расхождение (actual - expected)';

SELECT 'Схема базы данных product_db успешно создана!' as result;

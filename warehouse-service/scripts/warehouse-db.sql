CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TYPE rack_kind AS ENUM ('SHELF', 'CELL', 'FRIDGE', 'PALLET');

CREATE TABLE warehouse_read_model (
    warehouse_id    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    org_id          UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    address         VARCHAR(512),
    responsible_user_id UUID,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE warehouse_events (
    event_id        BIGSERIAL PRIMARY KEY,
    warehouse_id    UUID NOT NULL,
    event_type      VARCHAR(50) NOT NULL,
    event_data      JSONB NOT NULL,
    event_version   INT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE rack_read_model (
    rack_id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    warehouse_id    UUID NOT NULL REFERENCES warehouse_read_model(warehouse_id) ON DELETE CASCADE,
    kind            rack_kind NOT NULL,
    name            VARCHAR(255) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE rack_events (
    event_id        BIGSERIAL PRIMARY KEY,
    rack_id         UUID NOT NULL,
    event_type      VARCHAR(50) NOT NULL,
    event_data      JSONB NOT NULL,
    event_version   INT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE shelf (
    shelf_id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    rack_id             UUID NOT NULL REFERENCES rack_read_model(rack_id) ON DELETE CASCADE,
    shelf_capacity_kg   NUMERIC(8, 2) NOT NULL,
    length_cm           NUMERIC(8,2) NOT NULL,
    width_cm            NUMERIC(8,2) NOT NULL,
    height_cm           NUMERIC(8,2) NOT NULL
);

CREATE TABLE cell (
    cell_id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    rack_id         UUID NOT NULL REFERENCES rack_read_model(rack_id) ON DELETE CASCADE,
    max_weight_kg   NUMERIC(8, 2),
    length_cm       NUMERIC(8,2) NOT NULL,
    width_cm        NUMERIC(8,2) NOT NULL,
    height_cm       NUMERIC(8,2) NOT NULL
);

CREATE TABLE fridge (
    rack_id             UUID PRIMARY KEY REFERENCES rack_read_model(rack_id) ON DELETE CASCADE,
    temperature_c       NUMERIC(5,2),
    length_cm           NUMERIC(8,2) NOT NULL,
    width_cm            NUMERIC(8,2) NOT NULL,
    height_cm           NUMERIC(8,2) NOT NULL
);

CREATE TABLE pallet (
    rack_id             UUID PRIMARY KEY REFERENCES rack_read_model(rack_id) ON DELETE CASCADE,
    pallet_place_count  INT NOT NULL,
    max_weight_kg       NUMERIC(8,2) NOT NULL
);

CREATE TABLE pallet_place (
    place_id        UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    rack_id         UUID NOT NULL REFERENCES pallet(rack_id) ON DELETE CASCADE,
    length_cm       NUMERIC(8,2) NOT NULL,
    width_cm        NUMERIC(8,2) NOT NULL,
    height_cm       NUMERIC(8,2) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_warehouse_org_id ON warehouse_read_model(org_id);
CREATE INDEX IF NOT EXISTS idx_warehouse_active ON warehouse_read_model(is_active);
CREATE INDEX IF NOT EXISTS idx_warehouse_events_warehouse_id ON warehouse_events(warehouse_id);
CREATE INDEX IF NOT EXISTS idx_warehouse_events_created_at ON warehouse_events(created_at);

CREATE INDEX IF NOT EXISTS idx_rack_warehouse_id ON rack_read_model(warehouse_id);
CREATE INDEX IF NOT EXISTS idx_rack_active ON rack_read_model(is_active);
CREATE INDEX IF NOT EXISTS idx_rack_kind ON rack_read_model(kind);
CREATE INDEX IF NOT EXISTS idx_rack_events_rack_id ON rack_events(rack_id);
CREATE INDEX IF NOT EXISTS idx_rack_events_created_at ON rack_events(created_at);

CREATE INDEX IF NOT EXISTS idx_shelf_rack_id ON shelf(rack_id);
CREATE INDEX IF NOT EXISTS idx_cell_rack_id ON cell(rack_id);
CREATE INDEX IF NOT EXISTS idx_pallet_place_rack_id ON pallet_place(rack_id);

COMMENT ON TABLE warehouse_read_model IS 'Read Model для складов (CQRS)';
COMMENT ON TABLE warehouse_events IS 'Event Store для складов (Event Sourcing)';
COMMENT ON TABLE rack_read_model IS 'Read Model для стеллажей';
COMMENT ON TABLE rack_events IS 'Event Store для стеллажей';
COMMENT ON TABLE shelf IS 'Полочные стеллажи';
COMMENT ON TABLE cell IS 'Ячеистые стеллажи';
COMMENT ON TABLE fridge IS 'Холодильники';
COMMENT ON TABLE pallet IS 'Паллетные стеллажи';
COMMENT ON TABLE pallet_place IS 'Места на паллетных стеллажах';

COMMENT ON COLUMN warehouse_read_model.org_id IS 'ID организации-владельца';
COMMENT ON COLUMN warehouse_read_model.responsible_user_id IS 'Материально ответственное лицо';
COMMENT ON COLUMN rack_read_model.kind IS 'Тип стеллажа: SHELF, CELL, FRIDGE, PALLET';
COMMENT ON COLUMN shelf.shelf_capacity_kg IS 'Грузоподъёмность полки в кг';
COMMENT ON COLUMN cell.max_weight_kg IS 'Максимальный вес для ячейки в кг';
COMMENT ON COLUMN fridge.temperature_c IS 'Температура хранения в °C';
COMMENT ON COLUMN pallet.pallet_place_count IS 'Количество паллетомест';

SELECT 'Схема базы данных warehouse_db успешно создана!' as result;

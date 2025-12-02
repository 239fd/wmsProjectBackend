DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'session_status') THEN
        CREATE TYPE session_status AS ENUM ('IN_PROGRESS', 'COMPLETED', 'CANCELLED');
    END IF;
END $$;


CREATE TABLE IF NOT EXISTS inventory_session (
    session_id UUID PRIMARY KEY,
    warehouse_id UUID NOT NULL,
    started_by UUID NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    status session_status NOT NULL DEFAULT 'IN_PROGRESS',
    notes TEXT
);


CREATE INDEX IF NOT EXISTS idx_inventory_session_warehouse ON inventory_session(warehouse_id);
CREATE INDEX IF NOT EXISTS idx_inventory_session_status ON inventory_session(status);
CREATE INDEX IF NOT EXISTS idx_inventory_session_started_at ON inventory_session(started_at);


CREATE TABLE IF NOT EXISTS inventory_count (
    count_id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    product_id UUID NOT NULL,
    batch_id UUID,
    cell_id UUID,
    expected_quantity NUMERIC(12, 3) NOT NULL,
    actual_quantity NUMERIC(12, 3),
    discrepancy NUMERIC(12, 3) DEFAULT 0,
    notes TEXT,
    CONSTRAINT fk_inventory_count_session FOREIGN KEY (session_id)
        REFERENCES inventory_session(session_id) ON DELETE CASCADE
);


CREATE INDEX IF NOT EXISTS idx_inventory_count_session ON inventory_count(session_id);
CREATE INDEX IF NOT EXISTS idx_inventory_count_product ON inventory_count(product_id);
CREATE INDEX IF NOT EXISTS idx_inventory_count_batch ON inventory_count(batch_id);
CREATE INDEX IF NOT EXISTS idx_inventory_count_cell ON inventory_count(cell_id);
CREATE INDEX IF NOT EXISTS idx_inventory_count_discrepancy ON inventory_count(discrepancy)
    WHERE discrepancy != 0;


COMMENT ON TABLE inventory_session IS 'Сессии инвентаризации складских запасов';
COMMENT ON TABLE inventory_count IS 'Записи подсчёта товаров при инвентаризации';
COMMENT ON COLUMN inventory_session.session_id IS 'Уникальный идентификатор сессии';
COMMENT ON COLUMN inventory_session.warehouse_id IS 'ID склада на котором проводится инвентаризация';
COMMENT ON COLUMN inventory_session.started_by IS 'ID пользователя который начал инвентаризацию';
COMMENT ON COLUMN inventory_session.status IS 'Статус сессии: IN_PROGRESS, COMPLETED, CANCELLED';
COMMENT ON COLUMN inventory_count.expected_quantity IS 'Ожидаемое количество (из системы)';
COMMENT ON COLUMN inventory_count.actual_quantity IS 'Фактическое количество (подсчитано)';
COMMENT ON COLUMN inventory_count.discrepancy IS 'Расхождение (actual - expected)';


SELECT 'Таблицы inventory_session и inventory_count успешно созданы!' as result;

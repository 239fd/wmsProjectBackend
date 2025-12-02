DROP TABLE IF EXISTS product_operation CASCADE;
DROP TABLE IF EXISTS inventory CASCADE;


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

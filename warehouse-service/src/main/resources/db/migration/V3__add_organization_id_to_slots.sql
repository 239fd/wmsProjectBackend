-- D-X-2: денормализуем organization_id в slot-таблицы (shelf/cell/fridge/pallet_place),
-- чтобы Hibernate @Filter в C11.2 мог работать без JOIN'ов на rack_read_model→warehouse_read_model.
-- Существующие записи получают org_id через JOIN; новые записи будут писать его явно из заголовка X-Organization-Id.

ALTER TABLE shelf       ADD COLUMN IF NOT EXISTS organization_id UUID;
ALTER TABLE cell        ADD COLUMN IF NOT EXISTS organization_id UUID;
ALTER TABLE fridge      ADD COLUMN IF NOT EXISTS organization_id UUID;
ALTER TABLE pallet_place ADD COLUMN IF NOT EXISTS organization_id UUID;

-- Бэкфилл: для существующих slot'ов берём org_id из родительского rack→warehouse.
UPDATE shelf s SET organization_id = w.org_id
FROM rack_read_model r, warehouse_read_model w
WHERE s.rack_id = r.rack_id AND r.warehouse_id = w.warehouse_id
  AND s.organization_id IS NULL;

UPDATE cell c SET organization_id = w.org_id
FROM rack_read_model r, warehouse_read_model w
WHERE c.rack_id = r.rack_id AND r.warehouse_id = w.warehouse_id
  AND c.organization_id IS NULL;

UPDATE fridge f SET organization_id = w.org_id
FROM rack_read_model r, warehouse_read_model w
WHERE f.rack_id = r.rack_id AND r.warehouse_id = w.warehouse_id
  AND f.organization_id IS NULL;

UPDATE pallet_place p SET organization_id = w.org_id
FROM rack_read_model r, warehouse_read_model w
WHERE p.rack_id = r.rack_id AND r.warehouse_id = w.warehouse_id
  AND p.organization_id IS NULL;

-- Индексы для будущей фильтрации по tenant'у
CREATE INDEX IF NOT EXISTS idx_shelf_organization_id        ON shelf(organization_id);
CREATE INDEX IF NOT EXISTS idx_cell_organization_id         ON cell(organization_id);
CREATE INDEX IF NOT EXISTS idx_fridge_organization_id       ON fridge(organization_id);
CREATE INDEX IF NOT EXISTS idx_pallet_place_organization_id ON pallet_place(organization_id);

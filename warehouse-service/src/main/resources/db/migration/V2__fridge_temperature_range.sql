ALTER TABLE fridge
    ADD COLUMN IF NOT EXISTS min_temperature_c NUMERIC(5, 2),
    ADD COLUMN IF NOT EXISTS max_temperature_c NUMERIC(5, 2);

UPDATE fridge SET min_temperature_c = temperature_c, max_temperature_c = temperature_c
WHERE temperature_c IS NOT NULL AND min_temperature_c IS NULL;

ALTER TABLE fridge DROP COLUMN IF EXISTS temperature_c;

ALTER TABLE fridge
    DROP CONSTRAINT IF EXISTS chk_fridge_temp_range;

ALTER TABLE fridge
    ADD CONSTRAINT chk_fridge_temp_range
    CHECK (min_temperature_c IS NULL OR max_temperature_c IS NULL OR min_temperature_c <= max_temperature_c);

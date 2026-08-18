-- Opening balance support for importing already-started 1-on-1 packages.

ALTER TABLE enrollment_order_item
    ADD COLUMN opening_used_hours DECIMAL(6,2) NULL
        COMMENT 'Already used one-on-one hours before system tracking'
        AFTER one_on_one_hours,
    ADD CONSTRAINT chk_enrollment_order_item_opening_used_hours
        CHECK (opening_used_hours IS NULL OR opening_used_hours >= 0);

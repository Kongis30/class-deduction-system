-- Course product package pricing and attendance option compatibility fields.
-- Safe to run repeatedly on MySQL 8+.

ALTER TABLE course_price_option
    ADD COLUMN IF NOT EXISTS package_unit VARCHAR(16) NULL COMMENT 'Package unit: MONTH/WEEK/SESSION/HOUR' AFTER detail_text,
    ADD COLUMN IF NOT EXISTS package_quantity DECIMAL(8,2) NULL COMMENT 'Package quantity' AFTER package_unit,
    ADD COLUMN IF NOT EXISTS total_price DECIMAL(12,2) NULL COMMENT 'Package total price used for billing' AFTER package_quantity,
    ADD COLUMN IF NOT EXISTS derived_unit_price DECIMAL(12,2) NULL COMMENT 'Derived display-only unit price' AFTER total_price,
    ADD COLUMN IF NOT EXISTS full_day_price DECIMAL(12,2) NULL COMMENT 'Full-day package price' AFTER derived_unit_price,
    ADD COLUMN IF NOT EXISTS half_day_price DECIMAL(12,2) NULL COMMENT 'Half-day package price' AFTER full_day_price;

UPDATE course_price_option
SET package_unit = CASE WHEN course_type = 'ONE_ON_ONE' THEN 'HOUR' ELSE 'MONTH' END
WHERE package_unit IS NULL;

UPDATE course_price_option
SET package_quantity = CASE
        WHEN package_quantity IS NOT NULL THEN package_quantity
        WHEN package_unit = 'HOUR' AND one_on_one_hours IS NOT NULL THEN one_on_one_hours
        WHEN group_month_count IS NOT NULL THEN group_month_count
        WHEN group_session_count IS NOT NULL THEN group_session_count
        ELSE 1
    END
WHERE package_quantity IS NULL;

UPDATE course_price_option
SET total_price = COALESCE(total_price, group_total_price, price_amount, 0),
    derived_unit_price = CASE
        WHEN COALESCE(package_quantity, 0) > 0
            THEN ROUND(COALESCE(total_price, group_total_price, price_amount, 0) / package_quantity, 2)
        ELSE NULL
    END
WHERE total_price IS NULL OR derived_unit_price IS NULL;

ALTER TABLE class_group_schedule_slot
    ADD COLUMN IF NOT EXISTS morning_end_time TIME NULL COMMENT 'Morning half-day end time' AFTER end_time,
    ADD COLUMN IF NOT EXISTS afternoon_start_time TIME NULL COMMENT 'Afternoon half-day start time' AFTER morning_end_time,
    ADD COLUMN IF NOT EXISTS attendance_options VARCHAR(64) NULL COMMENT 'Available attendance options' AFTER afternoon_start_time;

UPDATE class_group_schedule_slot
SET attendance_options = COALESCE(attendance_options, 'FULL_DAY,MORNING,AFTERNOON')
WHERE attendance_options IS NULL;

ALTER TABLE enrollment_order_item
    ADD COLUMN IF NOT EXISTS package_unit VARCHAR(16) NULL COMMENT 'Package unit snapshot' AFTER time_slot_label,
    ADD COLUMN IF NOT EXISTS package_quantity DECIMAL(8,2) NULL COMMENT 'Package quantity snapshot' AFTER package_unit,
    ADD COLUMN IF NOT EXISTS package_total_price DECIMAL(12,2) NULL COMMENT 'Package total price snapshot' AFTER package_quantity,
    ADD COLUMN IF NOT EXISTS attendance_option VARCHAR(32) NULL COMMENT 'Attendance option snapshot' AFTER package_total_price,
    ADD COLUMN IF NOT EXISTS attendance_schedule_json JSON NULL COMMENT 'Per-day attendance schedule snapshot' AFTER attendance_option;

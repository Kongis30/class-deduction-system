-- Production schema synchronization for class_deduction_system.
-- Scope: additive columns, one index, and one check constraint only.
-- This script does not update or delete business data.
-- Safe to rerun on MySQL 8+.

DROP PROCEDURE IF EXISTS add_column_if_missing;
DELIMITER //
CREATE PROCEDURE add_column_if_missing(
    IN table_name_value VARCHAR(64),
    IN column_name_value VARCHAR(64),
    IN column_definition_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
          AND column_name = column_name_value
    ) THEN
        SET @ddl = CONCAT(
            'ALTER TABLE `', table_name_value, '` ADD COLUMN `',
            column_name_value, '` ', column_definition_value
        );
        PREPARE statement_to_run FROM @ddl;
        EXECUTE statement_to_run;
        DEALLOCATE PREPARE statement_to_run;
    END IF;
END//
DELIMITER ;

CALL add_column_if_missing(
    'package_teacher_assignment',
    'allocated_units',
    'INT NULL COMMENT ''Allocated teaching units; 1 unit = 0.5 hour'''
);
CALL add_column_if_missing(
    'package_teacher_assignment',
    'subject_name',
    'VARCHAR(128) NULL COMMENT ''Subject assigned to this teacher allocation'''
);
CALL add_column_if_missing(
    'package_teacher_assignment',
    'hourly_rate_snapshot',
    'DECIMAL(10,2) NULL COMMENT ''Teacher hourly pay-rate snapshot for this allocation'''
);

CALL add_column_if_missing(
    'course_price_option',
    'package_unit',
    'VARCHAR(16) NULL COMMENT ''Package unit: MONTH/WEEK/SESSION/HOUR'''
);
CALL add_column_if_missing(
    'course_price_option',
    'package_quantity',
    'DECIMAL(8,2) NULL COMMENT ''Package quantity'''
);
CALL add_column_if_missing(
    'course_price_option',
    'total_price',
    'DECIMAL(12,2) NULL COMMENT ''Package total price used for billing'''
);
CALL add_column_if_missing(
    'course_price_option',
    'derived_unit_price',
    'DECIMAL(12,2) NULL COMMENT ''Derived display-only unit price'''
);
CALL add_column_if_missing(
    'course_price_option',
    'full_day_price',
    'DECIMAL(12,2) NULL COMMENT ''Full-day package price'''
);
CALL add_column_if_missing(
    'course_price_option',
    'half_day_price',
    'DECIMAL(12,2) NULL COMMENT ''Half-day package price'''
);

CALL add_column_if_missing(
    'class_group_schedule_slot',
    'morning_end_time',
    'TIME NULL COMMENT ''Morning half-day end time'''
);
CALL add_column_if_missing(
    'class_group_schedule_slot',
    'afternoon_start_time',
    'TIME NULL COMMENT ''Afternoon half-day start time'''
);
CALL add_column_if_missing(
    'class_group_schedule_slot',
    'attendance_options',
    'VARCHAR(64) NULL COMMENT ''Available attendance options'''
);
CALL add_column_if_missing(
    'class_group_schedule_slot',
    'subject_name',
    'VARCHAR(128) NULL COMMENT ''Subject taught in this weekly class slot'''
);
CALL add_column_if_missing(
    'class_group_schedule_slot',
    'hourly_rate_snapshot',
    'DECIMAL(10,2) NULL COMMENT ''Teacher hourly pay-rate snapshot for this weekly class slot'''
);

CALL add_column_if_missing(
    'enrollment_order_item',
    'package_unit',
    'VARCHAR(16) NULL COMMENT ''Package unit snapshot'''
);
CALL add_column_if_missing(
    'enrollment_order_item',
    'package_quantity',
    'DECIMAL(8,2) NULL COMMENT ''Package quantity snapshot'''
);
CALL add_column_if_missing(
    'enrollment_order_item',
    'package_total_price',
    'DECIMAL(12,2) NULL COMMENT ''Package total price snapshot'''
);
CALL add_column_if_missing(
    'enrollment_order_item',
    'attendance_option',
    'VARCHAR(32) NULL COMMENT ''Attendance option snapshot'''
);
CALL add_column_if_missing(
    'enrollment_order_item',
    'attendance_schedule_json',
    'JSON NULL COMMENT ''Per-day attendance schedule snapshot'''
);
CALL add_column_if_missing(
    'enrollment_order_item',
    'opening_used_hours',
    'DECIMAL(6,2) NULL COMMENT ''Already used one-on-one hours before system tracking'''
);

CALL add_column_if_missing(
    'course_product',
    'sort_order',
    'INT NOT NULL DEFAULT 99 COMMENT ''Display order for course list sorting'''
);
CALL add_column_if_missing(
    'class_enrollment',
    'attendance_option',
    'VARCHAR(32) NOT NULL DEFAULT ''FULL_DAY'' COMMENT ''Registration attendance option: FULL_DAY/MORNING/AFTERNOON/NONE'''
);
CALL add_column_if_missing(
    'lesson_occurrence',
    'hourly_rate_snapshot',
    'DECIMAL(10,2) NULL COMMENT ''Teacher hourly pay-rate snapshot copied from schedule/allocation'''
);
CALL add_column_if_missing(
    'recurring_schedule_slot',
    'subject_name',
    'VARCHAR(128) NULL COMMENT ''Course/subject name taught in this recurring weekly slot'''
);
CALL add_column_if_missing(
    'recurring_schedule_slot',
    'allocated_units',
    'INT NULL COMMENT ''Units (1 unit = 0.5h) allocated to this slot; existing schedules must be completed before further generation'''
);

DROP PROCEDURE IF EXISTS add_column_if_missing;

SET @index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'course_product'
      AND index_name = 'idx_course_product_sort_order'
);
SET @index_sql = IF(
    @index_exists = 0,
    'CREATE INDEX idx_course_product_sort_order ON course_product (sort_order, created_at)',
    'SELECT 1'
);
PREPARE index_statement FROM @index_sql;
EXECUTE index_statement;
DEALLOCATE PREPARE index_statement;

SET @constraint_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'enrollment_order_item'
      AND constraint_name = 'chk_enrollment_order_item_opening_used_hours'
);
SET @constraint_sql = IF(
    @constraint_exists = 0,
    'ALTER TABLE enrollment_order_item ADD CONSTRAINT chk_enrollment_order_item_opening_used_hours CHECK (opening_used_hours IS NULL OR opening_used_hours >= 0)',
    'SELECT 1'
);
PREPARE constraint_statement FROM @constraint_sql;
EXECUTE constraint_statement;
DEALLOCATE PREPARE constraint_statement;

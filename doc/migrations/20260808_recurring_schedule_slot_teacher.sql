-- Allow each recurring 1v1 schedule slot to override the rule's default teacher.

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
    'recurring_schedule_slot',
    'teacher_id',
    'bigint DEFAULT NULL COMMENT ''Teacher assigned to this recurring weekly slot'''
);

-- Preserve the current behavior for existing schedules.
UPDATE recurring_schedule_slot slot
JOIN recurring_schedule_rule rule ON rule.id = slot.rule_id
SET slot.teacher_id = rule.primary_teacher_id
WHERE slot.teacher_id IS NULL
  AND rule.primary_teacher_id IS NOT NULL;

DROP PROCEDURE IF EXISTS add_column_if_missing;

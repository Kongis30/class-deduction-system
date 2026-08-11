-- Store the user-entered group total and the derived per-session price.

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
    'course_price_option',
    'group_total_price',
    'decimal(12,2) DEFAULT NULL COMMENT ''User-entered total group class price'''
);
CALL add_column_if_missing(
    'course_price_option',
    'group_session_price',
    'decimal(12,2) DEFAULT NULL COMMENT ''Derived group class price per session'''
);

-- Preserve historical per-session rows where the conversion is unambiguous.
UPDATE course_price_option option_row
JOIN class_group class_group ON class_group.id = option_row.class_id
SET option_row.group_session_price = option_row.price_amount,
    option_row.group_total_price = ROUND(option_row.price_amount * class_group.total_sessions, 2)
WHERE class_group.billing_type = 'PER_SESSION'
  AND class_group.total_sessions > 0
  AND option_row.group_total_price IS NULL;

DROP PROCEDURE IF EXISTS add_column_if_missing;

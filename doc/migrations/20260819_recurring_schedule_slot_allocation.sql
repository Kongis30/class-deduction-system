-- Let each recurring 1v1 schedule slot carry its own subject name and an
-- explicit allocated-units budget so scheduling can fill every slot to its
-- own quota independently instead of splitting the package balance evenly.

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
    'subject_name',
    'varchar(128) DEFAULT NULL COMMENT ''Course/subject name taught in this recurring weekly slot'''
);

CALL add_column_if_missing(
    'recurring_schedule_slot',
    'allocated_units',
    'int DEFAULT NULL COMMENT ''Units (1 unit = 0.5h) allocated to this slot; existing schedules must be completed before further generation'''
);

DROP PROCEDURE IF EXISTS add_column_if_missing;

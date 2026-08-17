-- Store the explicit teaching-hour allocation for each package teacher.

SET @allocated_units_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'package_teacher_assignment'
      AND column_name = 'allocated_units'
);
SET @allocated_units_sql = IF(
    @allocated_units_exists = 0,
    'ALTER TABLE package_teacher_assignment ADD COLUMN allocated_units INT NULL COMMENT ''Allocated teaching units; 1 unit = 0.5 hour'' AFTER teacher_id',
    'SELECT 1'
);
PREPARE allocated_units_statement FROM @allocated_units_sql;
EXECUTE allocated_units_statement;
DEALLOCATE PREPARE allocated_units_statement;

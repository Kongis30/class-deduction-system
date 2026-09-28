-- Allow each group-class weekly slot to use its own classroom.
-- Safe to run against databases that already contain the column/index/foreign key.

SET @classroom_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'class_group_schedule_slot'
      AND column_name = 'classroom_id'
);
SET @classroom_column_sql = IF(
    @classroom_column_exists = 0,
    'ALTER TABLE class_group_schedule_slot ADD COLUMN classroom_id BIGINT NULL COMMENT ''Classroom override for this weekly slot'' AFTER teacher_id',
    'SELECT 1'
);
PREPARE classroom_column_statement FROM @classroom_column_sql;
EXECUTE classroom_column_statement;
DEALLOCATE PREPARE classroom_column_statement;

SET @classroom_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'class_group_schedule_slot'
      AND index_name = 'idx_class_group_schedule_slot_classroom'
);
SET @classroom_index_sql = IF(
    @classroom_index_exists = 0,
    'ALTER TABLE class_group_schedule_slot ADD INDEX idx_class_group_schedule_slot_classroom (classroom_id, weekday, start_time)',
    'SELECT 1'
);
PREPARE classroom_index_statement FROM @classroom_index_sql;
EXECUTE classroom_index_statement;
DEALLOCATE PREPARE classroom_index_statement;

SET @classroom_fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'class_group_schedule_slot'
      AND constraint_name = 'fk_class_group_schedule_slot_classroom'
      AND constraint_type = 'FOREIGN KEY'
);
SET @classroom_fk_sql = IF(
    @classroom_fk_exists = 0,
    'ALTER TABLE class_group_schedule_slot ADD CONSTRAINT fk_class_group_schedule_slot_classroom FOREIGN KEY (classroom_id) REFERENCES classroom(id)',
    'SELECT 1'
);
SET @original_foreign_key_checks = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;
PREPARE classroom_fk_statement FROM @classroom_fk_sql;
EXECUTE classroom_fk_statement;
DEALLOCATE PREPARE classroom_fk_statement;
SET FOREIGN_KEY_CHECKS = @original_foreign_key_checks;

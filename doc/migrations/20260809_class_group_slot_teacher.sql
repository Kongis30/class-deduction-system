-- Allow each grade-class weekly slot to override the class default teacher.

SET @teacher_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'class_group_schedule_slot'
      AND column_name = 'teacher_id'
);
SET @teacher_column_sql = IF(
    @teacher_column_exists = 0,
    'ALTER TABLE class_group_schedule_slot ADD COLUMN teacher_id BIGINT NULL AFTER class_group_id',
    'SELECT 1'
);
PREPARE teacher_column_statement FROM @teacher_column_sql;
EXECUTE teacher_column_statement;
DEALLOCATE PREPARE teacher_column_statement;

SET @teacher_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'class_group_schedule_slot'
      AND index_name = 'idx_class_group_schedule_slot_teacher'
);
SET @teacher_index_sql = IF(
    @teacher_index_exists = 0,
    'ALTER TABLE class_group_schedule_slot ADD INDEX idx_class_group_schedule_slot_teacher (teacher_id, weekday, start_time)',
    'SELECT 1'
);
PREPARE teacher_index_statement FROM @teacher_index_sql;
EXECUTE teacher_index_statement;
DEALLOCATE PREPARE teacher_index_statement;

SET @teacher_fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'class_group_schedule_slot'
      AND constraint_name = 'fk_class_group_schedule_slot_teacher'
      AND constraint_type = 'FOREIGN KEY'
);
SET @teacher_fk_sql = IF(
    @teacher_fk_exists = 0,
    'ALTER TABLE class_group_schedule_slot ADD CONSTRAINT fk_class_group_schedule_slot_teacher FOREIGN KEY (teacher_id) REFERENCES teacher(id)',
    'SELECT 1'
);
SET @original_foreign_key_checks = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;
PREPARE teacher_fk_statement FROM @teacher_fk_sql;
EXECUTE teacher_fk_statement;
DEALLOCATE PREPARE teacher_fk_statement;
SET FOREIGN_KEY_CHECKS = @original_foreign_key_checks;

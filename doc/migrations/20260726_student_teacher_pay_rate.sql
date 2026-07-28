-- Allow a teacher's product rate to be overridden for one specific one-on-one student.
-- student_id NULL represents the default teacher + product rate.
-- This migration is intentionally idempotent so existing local databases can be upgraded safely.
SET @schema_name = DATABASE();

SELECT COUNT(*) INTO @has_student_column
FROM information_schema.columns
WHERE table_schema = @schema_name
  AND table_name = 'teacher_pay_rate'
  AND column_name = 'student_id';
SET @sql = IF(
  @has_student_column = 0,
  'ALTER TABLE `teacher_pay_rate` ADD COLUMN `student_id` bigint DEFAULT NULL COMMENT ''Optional student-specific pay-rate override'' AFTER `course_product_id`',
  'SELECT 1'
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SELECT COUNT(*) INTO @has_old_unique_key
FROM information_schema.statistics
WHERE table_schema = @schema_name
  AND table_name = 'teacher_pay_rate'
  AND index_name = 'uk_teacher_product';
SET @sql = IF(
  @has_old_unique_key > 0,
  'ALTER TABLE `teacher_pay_rate` DROP INDEX `uk_teacher_product`',
  'SELECT 1'
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SELECT COUNT(*) INTO @has_student_unique_key
FROM information_schema.statistics
WHERE table_schema = @schema_name
  AND table_name = 'teacher_pay_rate'
  AND index_name = 'uk_teacher_product_student';
SET @sql = IF(
  @has_student_unique_key = 0,
  'ALTER TABLE `teacher_pay_rate` ADD UNIQUE KEY `uk_teacher_product_student` (`teacher_id`, `course_product_id`, `student_id`, `is_deleted`)',
  'SELECT 1'
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SELECT COUNT(*) INTO @has_student_index
FROM information_schema.statistics
WHERE table_schema = @schema_name
  AND table_name = 'teacher_pay_rate'
  AND index_name = 'idx_pay_rate_student';
SET @sql = IF(
  @has_student_index = 0,
  'ALTER TABLE `teacher_pay_rate` ADD KEY `idx_pay_rate_student` (`student_id`)',
  'SELECT 1'
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SELECT COUNT(*) INTO @has_student_fk
FROM information_schema.table_constraints
WHERE constraint_schema = @schema_name
  AND table_name = 'teacher_pay_rate'
  AND constraint_name = 'fk_pay_rate_student'
  AND constraint_type = 'FOREIGN KEY';
SET @sql = IF(
  @has_student_fk = 0,
  'ALTER TABLE `teacher_pay_rate` ADD CONSTRAINT `fk_pay_rate_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`)',
  'SELECT 1'
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

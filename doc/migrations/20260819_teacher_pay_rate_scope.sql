-- Scope teacher pay rates by grade level and delivery mode.
-- NULL grade_band/delivery_mode means the rate applies to any grade/delivery.
SET @schema_name = DATABASE();

SELECT COUNT(*) INTO @has_grade_band
FROM information_schema.columns
WHERE table_schema = @schema_name
  AND table_name = 'teacher_pay_rate'
  AND column_name = 'grade_band';
SET @sql = IF(
  @has_grade_band = 0,
  'ALTER TABLE `teacher_pay_rate` ADD COLUMN `grade_band` varchar(64) DEFAULT NULL COMMENT ''Optional grade/level scope for this teacher pay rate'' AFTER `student_id`',
  'SELECT 1'
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SELECT COUNT(*) INTO @has_delivery_mode
FROM information_schema.columns
WHERE table_schema = @schema_name
  AND table_name = 'teacher_pay_rate'
  AND column_name = 'delivery_mode';
SET @sql = IF(
  @has_delivery_mode = 0,
  'ALTER TABLE `teacher_pay_rate` ADD COLUMN `delivery_mode` varchar(16) DEFAULT NULL COMMENT ''Optional delivery scope: ONLINE, OFFLINE, or NULL for any'' AFTER `grade_band`',
  'SELECT 1'
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SELECT COUNT(*) INTO @has_old_student_unique_key
FROM information_schema.statistics
WHERE table_schema = @schema_name
  AND table_name = 'teacher_pay_rate'
  AND index_name = 'uk_teacher_product_student';
SET @sql = IF(
  @has_old_student_unique_key > 0,
  'ALTER TABLE `teacher_pay_rate` DROP INDEX `uk_teacher_product_student`',
  'SELECT 1'
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SELECT COUNT(*) INTO @has_scope_unique_key
FROM information_schema.statistics
WHERE table_schema = @schema_name
  AND table_name = 'teacher_pay_rate'
  AND index_name = 'uk_teacher_product_student_scope';
SET @sql = IF(
  @has_scope_unique_key = 0,
  'ALTER TABLE `teacher_pay_rate` ADD UNIQUE KEY `uk_teacher_product_student_scope` (`teacher_id`, `course_product_id`, `student_id`, `grade_band`, `delivery_mode`, `is_deleted`)',
  'SELECT 1'
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

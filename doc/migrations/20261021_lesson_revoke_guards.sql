-- 1) lesson_record 只要求活跃记录唯一；软删除历史通过 NULL 生成列保留且不再互相冲突。
SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lesson_record' AND COLUMN_NAME = 'active_occurrence_id';
SET @sql = IF(@done = 0,
    'ALTER TABLE `lesson_record` ADD COLUMN `active_occurrence_id` bigint GENERATED ALWAYS AS (CASE WHEN `is_deleted` = 0 THEN `occurrence_id` ELSE NULL END) STORED',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lesson_record' AND COLUMN_NAME = 'active_student_id';
SET @sql = IF(@done = 0,
    'ALTER TABLE `lesson_record` ADD COLUMN `active_student_id` bigint GENERATED ALWAYS AS (CASE WHEN `is_deleted` = 0 THEN `student_id` ELSE NULL END) STORED',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 原唯一索引也承担按课次/学生读取的查询索引职责，保留其普通索引版本。
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lesson_record' AND INDEX_NAME = 'idx_lesson_record_occurrence_student_deleted';
SET @sql = IF(@done = 0,
    'ALTER TABLE `lesson_record` ADD KEY `idx_lesson_record_occurrence_student_deleted` (`occurrence_id`, `student_id`, `is_deleted`)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 活跃行唯一约束先建好，再移除错误的 (occurrence_id, student_id, is_deleted) 索引。
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lesson_record' AND INDEX_NAME = 'uk_lesson_record_active_occurrence_student';
SET @sql = IF(@done = 0,
    'ALTER TABLE `lesson_record` ADD UNIQUE KEY `uk_lesson_record_active_occurrence_student` (`active_occurrence_id`, `active_student_id`)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lesson_record' AND INDEX_NAME = 'uk_lesson_record_occurrence_student';
SET @sql = IF(@done > 0, 'ALTER TABLE `lesson_record` DROP INDEX `uk_lesson_record_occurrence_student`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 补课撤销用初始排课字段快照区分排课变更与普通 version 增长。
SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lesson_record_makeup_effect' AND COLUMN_NAME = 'planned_start_at';
SET @sql = IF(@done = 0, 'ALTER TABLE `lesson_record_makeup_effect` ADD COLUMN `planned_start_at` datetime NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lesson_record_makeup_effect' AND COLUMN_NAME = 'planned_end_at';
SET @sql = IF(@done = 0, 'ALTER TABLE `lesson_record_makeup_effect` ADD COLUMN `planned_end_at` datetime NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lesson_record_makeup_effect' AND COLUMN_NAME = 'planned_units';
SET @sql = IF(@done = 0, 'ALTER TABLE `lesson_record_makeup_effect` ADD COLUMN `planned_units` int NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lesson_record_makeup_effect' AND COLUMN_NAME = 'schedule_rule_id';
SET @sql = IF(@done = 0, 'ALTER TABLE `lesson_record_makeup_effect` ADD COLUMN `schedule_rule_id` bigint NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lesson_record_makeup_effect' AND COLUMN_NAME = 'schedule_slot_id';
SET @sql = IF(@done = 0, 'ALTER TABLE `lesson_record_makeup_effect` ADD COLUMN `schedule_slot_id` bigint NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lesson_record_makeup_effect' AND COLUMN_NAME = 'allocation_owner_teacher_id';
SET @sql = IF(@done = 0, 'ALTER TABLE `lesson_record_makeup_effect` ADD COLUMN `allocation_owner_teacher_id` bigint NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lesson_record_makeup_effect' AND COLUMN_NAME = 'allocation_anchor_date';
SET @sql = IF(@done = 0, 'ALTER TABLE `lesson_record_makeup_effect` ADD COLUMN `allocation_anchor_date` date NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lesson_record_makeup_effect' AND COLUMN_NAME = 'planned_teacher_id';
SET @sql = IF(@done = 0, 'ALTER TABLE `lesson_record_makeup_effect` ADD COLUMN `planned_teacher_id` bigint NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lesson_record_makeup_effect' AND COLUMN_NAME = 'subject_name';
SET @sql = IF(@done = 0, 'ALTER TABLE `lesson_record_makeup_effect` ADD COLUMN `subject_name` varchar(100) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lesson_record_makeup_effect' AND COLUMN_NAME = 'delivery_mode';
SET @sql = IF(@done = 0, 'ALTER TABLE `lesson_record_makeup_effect` ADD COLUMN `delivery_mode` varchar(32) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lesson_record_makeup_effect' AND COLUMN_NAME = 'planned_classroom_id';
SET @sql = IF(@done = 0, 'ALTER TABLE `lesson_record_makeup_effect` ADD COLUMN `planned_classroom_id` bigint NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lesson_record_makeup_effect' AND COLUMN_NAME = 'leave_carryover_units';
SET @sql = IF(@done = 0, 'ALTER TABLE `lesson_record_makeup_effect` ADD COLUMN `leave_carryover_units` int NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lesson_record_makeup_effect' AND COLUMN_NAME = 'planning_status';
SET @sql = IF(@done = 0, 'ALTER TABLE `lesson_record_makeup_effect` ADD COLUMN `planning_status` varchar(32) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lesson_record_makeup_effect' AND COLUMN_NAME = 'source_occurrence_id';
SET @sql = IF(@done = 0, 'ALTER TABLE `lesson_record_makeup_effect` ADD COLUMN `source_occurrence_id` bigint NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

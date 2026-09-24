-- 仅对活跃账号施加用户名和档案关联唯一约束。
-- sys_user 的 (业务字段, is_deleted) 复合唯一索引会在同一账号第二次软删除时
-- 与更早的软删除记录冲突；生成列在软删除行上返回 NULL，MySQL 唯一索引允许
-- 多个 NULL，同时继续保证活跃账号唯一。

-- 生成列供唯一索引使用；保留原始字段以供审计和历史查询。
SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'active_username';
SET @sql = IF(@done = 0, 'ALTER TABLE `sys_user` ADD COLUMN `active_username` varchar(64) GENERATED ALWAYS AS (CASE WHEN `is_deleted` = 0 THEN `username` ELSE NULL END) STORED', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'active_teacher_id';
SET @sql = IF(@done = 0, 'ALTER TABLE `sys_user` ADD COLUMN `active_teacher_id` bigint GENERATED ALWAYS AS (CASE WHEN `is_deleted` = 0 THEN `teacher_id` ELSE NULL END) STORED', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'active_student_id';
SET @sql = IF(@done = 0, 'ALTER TABLE `sys_user` ADD COLUMN `active_student_id` bigint GENERATED ALWAYS AS (CASE WHEN `is_deleted` = 0 THEN `student_id` ELSE NULL END) STORED', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 先建立活跃账号唯一索引，再移除旧复合索引，迁移期间始终保护活跃数据。
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_sys_user_active_username';
SET @sql = IF(@done = 0, 'ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_sys_user_active_username` (`active_username`)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_sys_user_active_teacher_id';
SET @sql = IF(@done = 0, 'ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_sys_user_active_teacher_id` (`active_teacher_id`)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_sys_user_active_student_id';
SET @sql = IF(@done = 0, 'ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_sys_user_active_student_id` (`active_student_id`)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 保留普通索引，支持用户名查重和 student_id 外键查找。
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_sys_user_username_deleted';
SET @sql = IF(@done = 0, 'ALTER TABLE `sys_user` ADD KEY `idx_sys_user_username_deleted` (`username`, `is_deleted`)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_sys_user_student_lookup';
SET @sql = IF(@done = 0, 'ALTER TABLE `sys_user` ADD KEY `idx_sys_user_student_lookup` (`student_id`)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 删除旧索引。学生 ID 的普通索引已就位，可继续支撑外键。
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_sys_user_username';
SET @sql = IF(@done > 0, 'ALTER TABLE `sys_user` DROP INDEX `uk_sys_user_username`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_sys_user_teacher_id';
SET @sql = IF(@done > 0, 'ALTER TABLE `sys_user` DROP INDEX `uk_sys_user_teacher_id`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_sys_user_student_id';
SET @sql = IF(@done > 0, 'ALTER TABLE `sys_user` DROP INDEX `idx_sys_user_student_id`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

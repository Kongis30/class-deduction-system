-- 20260926: identify the logged-in user who saved each registration and add the manager role.

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollment_order' AND COLUMN_NAME = 'registration_operator_user_id';
SET @sql = IF(@done = 0,
  'ALTER TABLE `enrollment_order` ADD COLUMN `registration_operator_user_id` bigint DEFAULT NULL COMMENT ''User account that saved this registration'' AFTER `cashier_user_id`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollment_order' AND COLUMN_NAME = 'registration_operator_name';
SET @sql = IF(@done = 0,
  'ALTER TABLE `enrollment_order` ADD COLUMN `registration_operator_name` varchar(64) DEFAULT NULL COMMENT ''Registration operator name snapshot'' AFTER `registration_operator_user_id`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollment_order' AND INDEX_NAME = 'idx_enrollment_registration_created_operator';
SET @sql = IF(@done = 0,
  'ALTER TABLE `enrollment_order` ADD KEY `idx_enrollment_registration_created_operator` (`created_at`, `registration_operator_user_id`)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 旧订单没有稳定的录入人快照；优先使用历史收银账号，姓名回退到旧经手人字段。
UPDATE enrollment_order
SET registration_operator_user_id = COALESCE(registration_operator_user_id, cashier_user_id, created_by),
    registration_operator_name = COALESCE(registration_operator_name, handled_by_name)
WHERE registration_operator_user_id IS NULL OR registration_operator_name IS NULL;

SET @manager_role_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_role);
INSERT INTO sys_role (id, role_code, role_name, status, remark, created_at, updated_at, is_deleted, version)
SELECT @manager_role_id, 'MANAGER', 'Registration manager', 'ACTIVE',
       'Can collect payments but cannot issue refunds or payouts', NOW(3), NOW(3), 0, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'MANAGER' AND is_deleted = 0);

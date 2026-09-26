-- 20260926: course-level refund stopping snapshots.
-- Store the consumed value at the moment a course is stopped so Data Summary and
-- receipts can retain the decision after schedule data changes.

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollment_order_item' AND COLUMN_NAME = 'stopped_consumed_amount';
SET @sql = IF(@done = 0,
  'ALTER TABLE `enrollment_order_item` ADD COLUMN `stopped_consumed_amount` decimal(12,2) DEFAULT NULL COMMENT ''Consumed tuition amount when course stopped'' AFTER `item_status`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollment_order_item' AND COLUMN_NAME = 'stopped_session_count';
SET @sql = IF(@done = 0,
  'ALTER TABLE `enrollment_order_item` ADD COLUMN `stopped_session_count` int DEFAULT NULL COMMENT ''Confirmed chargeable sessions when course stopped'' AFTER `stopped_consumed_amount`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollment_order_item' AND COLUMN_NAME = 'stopped_period_start_date';
SET @sql = IF(@done = 0,
  'ALTER TABLE `enrollment_order_item` ADD COLUMN `stopped_period_start_date` date DEFAULT NULL COMMENT ''First confirmed lesson date when course stopped'' AFTER `stopped_session_count`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollment_order_item' AND COLUMN_NAME = 'stopped_period_end_date';
SET @sql = IF(@done = 0,
  'ALTER TABLE `enrollment_order_item` ADD COLUMN `stopped_period_end_date` date DEFAULT NULL COMMENT ''Last confirmed lesson date when course stopped'' AFTER `stopped_period_start_date`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollment_order_item' AND COLUMN_NAME = 'stopped_at';
SET @sql = IF(@done = 0,
  'ALTER TABLE `enrollment_order_item` ADD COLUMN `stopped_at` datetime(3) DEFAULT NULL COMMENT ''Course stop timestamp'' AFTER `stopped_period_end_date`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

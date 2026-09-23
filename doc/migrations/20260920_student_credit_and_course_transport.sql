-- Student Credit ledger and course-level transportation snapshots.
-- This migration is additive and keeps the existing order-level fields for legacy data.

CREATE TABLE IF NOT EXISTS `student_credit_ledger` (
  `id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `entry_type` varchar(32) NOT NULL COMMENT 'TOP_UP_CREDIT/REFUND_TO_CREDIT/CREDIT_USED/MANUAL_ADJUSTMENT',
  `amount` decimal(12,2) NOT NULL COMMENT 'Positive adds Credit, negative consumes Credit',
  `source_enrollment_order_id` bigint DEFAULT NULL,
  `source_payment_record_id` bigint DEFAULT NULL,
  `reference_no` varchar(64) DEFAULT NULL,
  `handled_by_name` varchar(64) DEFAULT NULL,
  `handled_by_user_id` bigint DEFAULT NULL,
  `occurred_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `remark` varchar(500) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_student_credit_student_time` (`student_id`, `occurred_at` DESC),
  KEY `idx_student_credit_order` (`source_enrollment_order_id`),
  CONSTRAINT `fk_student_credit_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`),
  CONSTRAINT `fk_student_credit_order` FOREIGN KEY (`source_enrollment_order_id`) REFERENCES `enrollment_order` (`id`),
  CONSTRAINT `fk_student_credit_payment` FOREIGN KEY (`source_payment_record_id`) REFERENCES `payment_record` (`id`),
  CONSTRAINT `fk_student_credit_user` FOREIGN KEY (`handled_by_user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Student Credit ledger';

-- MySQL does not support ADD COLUMN IF NOT EXISTS consistently across the
-- versions used by local and deployed environments, so each column is added
-- through an information_schema guarded statement.
SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollment_order_item' AND COLUMN_NAME = 'pickup_required_snapshot';
SET @sql = IF(@done = 0, 'ALTER TABLE `enrollment_order_item` ADD COLUMN `pickup_required_snapshot` tinyint(1) NOT NULL DEFAULT ''0'' AFTER `teacher_id`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollment_order_item' AND COLUMN_NAME = 'pickup_address_snapshot';
SET @sql = IF(@done = 0, 'ALTER TABLE `enrollment_order_item` ADD COLUMN `pickup_address_snapshot` varchar(255) DEFAULT NULL AFTER `pickup_required_snapshot`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollment_order_item' AND COLUMN_NAME = 'dropoff_required_snapshot';
SET @sql = IF(@done = 0, 'ALTER TABLE `enrollment_order_item` ADD COLUMN `dropoff_required_snapshot` tinyint(1) NOT NULL DEFAULT ''0'' AFTER `pickup_address_snapshot`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollment_order_item' AND COLUMN_NAME = 'dropoff_address_snapshot';
SET @sql = IF(@done = 0, 'ALTER TABLE `enrollment_order_item` ADD COLUMN `dropoff_address_snapshot` varchar(255) DEFAULT NULL AFTER `dropoff_required_snapshot`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollment_order_item' AND COLUMN_NAME = 'pickup_fee_amount';
SET @sql = IF(@done = 0, 'ALTER TABLE `enrollment_order_item` ADD COLUMN `pickup_fee_amount` decimal(12,2) NOT NULL DEFAULT ''0.00'' AFTER `dropoff_address_snapshot`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollment_order_item' AND COLUMN_NAME = 'dropoff_fee_amount';
SET @sql = IF(@done = 0, 'ALTER TABLE `enrollment_order_item` ADD COLUMN `dropoff_fee_amount` decimal(12,2) NOT NULL DEFAULT ''0.00'' AFTER `pickup_fee_amount`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollment_order_item' AND COLUMN_NAME = 'extra_fee_amount';
SET @sql = IF(@done = 0, 'ALTER TABLE `enrollment_order_item` ADD COLUMN `extra_fee_amount` decimal(12,2) NOT NULL DEFAULT ''0.00'' AFTER `dropoff_fee_amount`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollment_order_item' AND COLUMN_NAME = 'extra_fee_detail';
SET @sql = IF(@done = 0, 'ALTER TABLE `enrollment_order_item` ADD COLUMN `extra_fee_detail` json DEFAULT NULL AFTER `extra_fee_amount`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

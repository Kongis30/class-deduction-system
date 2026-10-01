-- 班课 V2 专用增量结构。只能连接 class_deduction_system_v2 后执行。
-- One-time DBv2 bootstrap: run only against a fresh local class_deduction_system_v2 clone.
-- This schema initializer is intentionally not replay-safe. If it stops partway, restore the
-- pre-migration DBv2 snapshot and restart from this file; do not rerun on a partial schema.
USE `class_deduction_system_v2`;
-- Read-only preflight: invalid targets deliberately fail PREPARE before any DDL or data change.
SET @group_course_v2_guard_sql = IF(
  COALESCE(DATABASE(), '') = 'class_deduction_system_v2'
  AND SUBSTRING_INDEX(USER(), '@', -1) IN ('localhost','127.0.0.1','::1'),
  'SELECT 1',
  'THIS IS NOT A VALID GROUP COURSE V2 MIGRATION TARGET'
);
PREPARE group_course_v2_target_guard FROM @group_course_v2_guard_sql;
EXECUTE group_course_v2_target_guard;
DEALLOCATE PREPARE group_course_v2_target_guard;

-- 旧库由运行时兼容层补过该列；DBv2 的初始化迁移必须先建立它，才能加索引和回填历史课次。
DROP PROCEDURE IF EXISTS `ensure_group_course_v2_lesson_entitlement_column`;

DELIMITER $$
CREATE PROCEDURE `ensure_group_course_v2_lesson_entitlement_column`()
BEGIN
  DECLARE column_count bigint DEFAULT 0;
  DECLARE index_count bigint DEFAULT 0;
  SELECT COUNT(*) INTO column_count
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lesson_record' AND COLUMN_NAME='class_enrollment_id';
  IF column_count=0 THEN
    ALTER TABLE `lesson_record`
      ADD COLUMN `class_enrollment_id` bigint DEFAULT NULL COMMENT 'Student class enrollment settled by this record';
  END IF;
  SELECT COUNT(*) INTO index_count
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lesson_record' AND INDEX_NAME='idx_lesson_record_class_entitlement';
  IF index_count=0 THEN
    ALTER TABLE `lesson_record`
      ADD KEY `idx_lesson_record_class_entitlement` (`class_enrollment_id`,`is_deleted`);
  END IF;
END$$
DELIMITER ;
CALL `ensure_group_course_v2_lesson_entitlement_column`();
DROP PROCEDURE `ensure_group_course_v2_lesson_entitlement_column`;


CREATE TABLE IF NOT EXISTS `group_course_project` (
  `id` bigint NOT NULL,
  `course_product_id` bigint NOT NULL,
  `project_code` varchar(64) NOT NULL,
  `project_name` varchar(128) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `display_order` int NOT NULL DEFAULT 0,
  `status` varchar(24) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `version` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_project_product_code` (`course_product_id`,`project_code`,`is_deleted`),
  KEY `idx_group_project_product_status` (`course_product_id`,`status`,`display_order`),
  CONSTRAINT `fk_group_project_product` FOREIGN KEY (`course_product_id`) REFERENCES `course_product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='教学项目：班课产品内的课程内容/学科分类';

CREATE TABLE IF NOT EXISTS `group_registration_plan` (
  `id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `plan_code` varchar(64) NOT NULL,
  `plan_name` varchar(128) NOT NULL,
  `coverage_type` varchar(32) NOT NULL COMMENT 'FIXED_DATES / ROLLING_MONTHS / SESSION_COUNT',
  `term_code` varchar(32) DEFAULT NULL COMMENT '例如 FALL / SPRING / FULL_YEAR',
  `coverage_start_date` date DEFAULT NULL,
  `coverage_end_date` date DEFAULT NULL,
  `package_unit` varchar(16) NOT NULL COMMENT 'SESSION / MONTH',
  `package_quantity` decimal(8,2) NOT NULL,
  `full_day_price` decimal(12,2) NOT NULL DEFAULT 0,
  `half_day_price` decimal(12,2) DEFAULT NULL,
  `currency_code` varchar(8) NOT NULL DEFAULT 'USD',
  `display_order` int NOT NULL DEFAULT 0,
  `status` varchar(24) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `version` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_plan_project_code` (`project_id`,`plan_code`,`is_deleted`),
  KEY `idx_group_plan_project_status` (`project_id`,`status`,`display_order`),
  CONSTRAINT `fk_group_plan_project` FOREIGN KEY (`project_id`) REFERENCES `group_course_project` (`id`),
  CONSTRAINT `chk_group_plan_quantity_positive` CHECK (`package_quantity` > 0),
  CONSTRAINT `chk_group_plan_price_nonnegative` CHECK (`full_day_price` >= 0 AND (`half_day_price` IS NULL OR `half_day_price` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='班课报名方案与收费';

ALTER TABLE `class_group`
  ADD COLUMN `group_project_id` bigint DEFAULT NULL AFTER `product_id`;
ALTER TABLE `class_group`
  ADD KEY `idx_class_group_project_campus_actual` (`group_project_id`,`campus_id`,`actual_class`,`is_deleted`);
ALTER TABLE `class_group`
  ADD CONSTRAINT `fk_class_group_project` FOREIGN KEY (`group_project_id`) REFERENCES `group_course_project` (`id`);

ALTER TABLE `course_price_option`
  ADD COLUMN `group_project_id` bigint DEFAULT NULL,
  ADD COLUMN `group_registration_plan_id` bigint DEFAULT NULL;
ALTER TABLE `course_price_option`
  ADD KEY `idx_price_option_group_project` (`group_project_id`),
  ADD KEY `idx_price_option_group_plan` (`group_registration_plan_id`),
  ADD CONSTRAINT `fk_price_option_group_project` FOREIGN KEY (`group_project_id`) REFERENCES `group_course_project` (`id`),
  ADD CONSTRAINT `fk_price_option_group_plan` FOREIGN KEY (`group_registration_plan_id`) REFERENCES `group_registration_plan` (`id`);

ALTER TABLE `course_holiday`
  ADD COLUMN `group_project_id` bigint DEFAULT NULL;
ALTER TABLE `course_holiday`
  ADD KEY `idx_course_holiday_project_dates` (`group_project_id`,`is_deleted`,`start_date`,`end_date`),
  ADD CONSTRAINT `fk_course_holiday_group_project` FOREIGN KEY (`group_project_id`) REFERENCES `group_course_project` (`id`);

ALTER TABLE `enrollment_order_item`
  ADD COLUMN `group_project_id` bigint DEFAULT NULL,
  ADD COLUMN `group_registration_plan_id` bigint DEFAULT NULL;
ALTER TABLE `enrollment_order_item`
  ADD KEY `idx_order_item_group_project` (`group_project_id`),
  ADD KEY `idx_order_item_group_plan` (`group_registration_plan_id`),
  ADD CONSTRAINT `fk_order_item_group_project` FOREIGN KEY (`group_project_id`) REFERENCES `group_course_project` (`id`),
  ADD CONSTRAINT `fk_order_item_group_plan` FOREIGN KEY (`group_registration_plan_id`) REFERENCES `group_registration_plan` (`id`);

ALTER TABLE `class_enrollment`
  ADD COLUMN `group_project_id` bigint DEFAULT NULL,
  ADD COLUMN `group_registration_plan_id` bigint DEFAULT NULL;
ALTER TABLE `class_enrollment`
  DROP INDEX `uk_enrollment_class_student`;
ALTER TABLE `class_enrollment`
  ADD KEY `idx_enrollment_class_student_status` (`class_id`,`student_id`,`status`,`is_deleted`),
  ADD KEY `idx_enrollment_order_item_student` (`enrollment_order_item_id`,`student_id`,`is_deleted`),
  ADD KEY `idx_enrollment_group_plan` (`group_registration_plan_id`),
  ADD CONSTRAINT `fk_class_enrollment_group_project` FOREIGN KEY (`group_project_id`) REFERENCES `group_course_project` (`id`),
  ADD CONSTRAINT `fk_class_enrollment_group_plan` FOREIGN KEY (`group_registration_plan_id`) REFERENCES `group_registration_plan` (`id`);

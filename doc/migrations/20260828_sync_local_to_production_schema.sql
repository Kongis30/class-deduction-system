-- =============================================================================
-- 20260828 本地库对齐线上生产 schema（local → production sync）
-- -----------------------------------------------------------------------------
-- 目标：class.jinbesttutoring.com 线上库（46 表 / 846 列）为最新权威，本地同步到该状态。
-- 数据来源：线上 mysqldump --no-data（2026-08-28 导出）。
-- 本地库本次变更：
--   ① 新增表 course_category（13 列，含唯一键与展示顺序索引）
--   ② 新增 11 列（class_enrollment 3 列 / course_price_option 5 列 /
--                   hour_ledger 2 列 / lesson_record 1 列），NOT NULL 列带默认值
--   ③ 删除本地相对线上多出的外键/索引（recurring_schedule_slot.teacher_id）
--      —— 线上无此 FK/索引（doc 规范超前于线上，上一份迁移误加）
--   ④ 删除 course_product.expiry_duration_days（线上已删除，代码无引用）
-- 数据保全：存量业务数据不受影响；course_product 仅清除已被线上废弃的遗留列。
-- 验证：应用后重跑列/索引/FK 全量比对应为空；重启后端确认启动成功。
-- =============================================================================

-- 1) 新增表 course_category（与线上定义一致）
CREATE TABLE `course_category` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `type_code` varchar(32) NOT NULL COMMENT 'Fixed category code: AFTER_SCHOOL/SATURDAY/SUMMER/SPRINT/ONE_ON_ONE',
  `display_name` varchar(64) NOT NULL COMMENT 'Editable display name',
  `display_order` int NOT NULL DEFAULT '99' COMMENT 'Display order for sorting',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/INACTIVE',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` bigint DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` bigint DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` bigint DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_category_type_code` (`type_code`),
  KEY `idx_course_category_display_order` (`display_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Top-level course categories (display name + order editable)';

-- 2) 新增 11 列
ALTER TABLE `class_enrollment` ADD COLUMN `total_units` int DEFAULT NULL COMMENT 'Purchased units for a HOURLY enrollment; 1 unit = 0.5 hour';
ALTER TABLE `class_enrollment` ADD COLUMN `consumed_units` int NOT NULL DEFAULT '0' COMMENT 'Consumed units for a HOURLY enrollment';
ALTER TABLE `class_enrollment` ADD COLUMN `remaining_units` int DEFAULT NULL COMMENT 'Remaining units for a HOURLY enrollment';
ALTER TABLE `course_price_option` ADD COLUMN `group_monthly_price` decimal(12,2) DEFAULT NULL COMMENT 'Price for one rolling month of group enrollment';
ALTER TABLE `course_price_option` ADD COLUMN `group_hourly_rate` decimal(12,2) DEFAULT NULL COMMENT 'Price for one hour of group enrollment';
ALTER TABLE `course_price_option` ADD COLUMN `group_hourly_validity_months` int NOT NULL DEFAULT '12' COMMENT 'Validity of purchased group hours in rolling months';
ALTER TABLE `course_price_option` ADD COLUMN `group_billing_type` varchar(32) DEFAULT NULL COMMENT 'Group enrollment billing option: MONTHLY/HOURLY';
ALTER TABLE `course_price_option` ADD COLUMN `group_purchased_hours` decimal(10,2) DEFAULT NULL COMMENT 'Purchased hours for a HOURLY group enrollment';
ALTER TABLE `hour_ledger` ADD COLUMN `class_enrollment_id` bigint DEFAULT NULL COMMENT 'HOURLY class enrollment affected by this ledger row';
ALTER TABLE `hour_ledger` ADD COLUMN `source_order_item_id` bigint DEFAULT NULL COMMENT 'Enrollment order item that purchased or topped up hours';
ALTER TABLE `lesson_record` ADD COLUMN `class_enrollment_id` bigint DEFAULT NULL COMMENT 'Student class enrollment settled by this record';

-- 3) 删除本地相对线上多出的 FK/索引（对齐线上；先删 FK 再删索引）
ALTER TABLE `recurring_schedule_slot` DROP FOREIGN KEY `fk_schedule_slot_teacher`;
ALTER TABLE `recurring_schedule_slot` DROP INDEX `idx_schedule_slot_teacher`;

-- 4) 删除 course_product.expiry_duration_days（线上已删除，代码无引用）
ALTER TABLE `course_product` DROP COLUMN `expiry_duration_days`;

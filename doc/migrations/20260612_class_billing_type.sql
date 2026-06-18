-- ============================================================================
-- Migration: 班课产品计费模式改造（课次收费 / 月收费），移除 expiry_duration_days
-- Date: 2026-06-12
-- 适用表: course_product, class_enrollment
--
-- 说明:
--   本项目无 Flyway/Liquibase，schema 变更需手工执行本脚本，并同步
--   doc/mysql_schema_from_db.sql。请在执行前备份数据库。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. course_product: 新增计费类型相关列
-- ---------------------------------------------------------------------------
ALTER TABLE `course_product`
  ADD COLUMN `billing_type`   varchar(32) DEFAULT NULL COMMENT 'GROUP billing type: PER_SESSION/MONTHLY' AFTER `default_lesson_units`,
  ADD COLUMN `start_date`     date        DEFAULT NULL COMMENT 'GROUP PER_SESSION start date'             AFTER `billing_type`,
  ADD COLUMN `total_sessions` int         DEFAULT NULL COMMENT 'GROUP PER_SESSION total session count'    AFTER `start_date`,
  ADD COLUMN `start_month`    date        DEFAULT NULL COMMENT 'GROUP MONTHLY start month (1st of month)' AFTER `total_sessions`,
  ADD COLUMN `end_month`      date        DEFAULT NULL COMMENT 'GROUP MONTHLY end month (1st of month)'   AFTER `start_month`;

-- ---------------------------------------------------------------------------
-- 2. 迁移旧 GROUP 产品: 旧模型只有 expiry_duration_days，无法精确推导课次/月份。
--    统一迁移为 PER_SESSION 占位（start_date=今天, total_sessions=10），
--    并置为 INACTIVE 强制管理员复核后再启用，避免错误定价。
-- ---------------------------------------------------------------------------
UPDATE `course_product`
SET `billing_type`   = 'PER_SESSION',
    `start_date`     = CURDATE(),
    `total_sessions` = 10,
    `status`         = 'INACTIVE'
WHERE `teaching_mode` = 'GROUP'
  AND `is_deleted` = 0;

-- ---------------------------------------------------------------------------
-- 3. course_product: 移除已废弃的 expiry_duration_days 列
-- ---------------------------------------------------------------------------
ALTER TABLE `course_product`
  DROP COLUMN `expiry_duration_days`;

-- ---------------------------------------------------------------------------
-- 4. class_enrollment: 新增报名快照列（计费类型 + 购买数量）
-- ---------------------------------------------------------------------------
ALTER TABLE `class_enrollment`
  ADD COLUMN `billing_type`       varchar(32) DEFAULT NULL COMMENT 'Snapshot of product billing type: PER_SESSION/MONTHLY' AFTER `expiry_date`,
  ADD COLUMN `purchased_quantity` int         DEFAULT NULL COMMENT 'Purchased quantity: session count (PER_SESSION) or month count (MONTHLY)' AFTER `billing_type`;

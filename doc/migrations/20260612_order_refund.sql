-- ============================================================================
-- Migration: 报名订单退款支持
-- Date: 2026-06-12
-- 适用表: payment_record, enrollment_order
--
-- 说明:
--   本项目无 Flyway/Liquibase，schema 变更需手工执行本脚本，并同步
--   doc/mysql_schema_from_db.sql。请在执行前备份数据库。
--
--   退款实现：退款记为一条 amount 为负、payment_type='REFUND' 的 payment_record。
--   部分退款更新订单 received_amount、balance_amount 和 payment_status；全额退款
--   置为 REFUNDED 并清零。不新增退款表，退款始终归属一个 enrollment_order。
--
-- Safe to rerun.
-- ============================================================================

SET @add_payment_type = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'payment_record'
        AND column_name = 'payment_type'
    ),
    'ALTER TABLE `payment_record` ADD COLUMN `payment_type` varchar(16) NOT NULL DEFAULT ''PAYMENT'' COMMENT ''Record type: PAYMENT/REFUND'' AFTER `payment_method`',
    'SELECT 1'
  )
);
PREPARE stmt FROM @add_payment_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- enrollment_order.payment_status 注释扩展 REFUNDED（varchar 长度无需调整，仅注释说明）
ALTER TABLE `enrollment_order`
  MODIFY COLUMN `payment_status` varchar(32) NOT NULL DEFAULT 'UNPAID'
    COMMENT 'Payment status: UNPAID/PARTIAL/PAID/REFUNDED';

SET @add_payment_order_type_index = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.statistics
      WHERE table_schema = DATABASE()
        AND table_name = 'payment_record'
        AND index_name = 'idx_payment_record_order_type'
    ),
    'CREATE INDEX `idx_payment_record_order_type` ON `payment_record` (`enrollment_order_id`, `payment_type`, `paid_at`)',
    'SELECT 1'
  )
);
PREPARE stmt FROM @add_payment_order_type_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

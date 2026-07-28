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
--   置为 REFUNDED 并清零。该规则不需要新增数据库字段。
-- ============================================================================

-- payment_record: 新增记录类型列（区分支付/退款），存量行默认 PAYMENT
ALTER TABLE `payment_record`
  ADD COLUMN `payment_type` varchar(16) NOT NULL DEFAULT 'PAYMENT'
    COMMENT 'Record type: PAYMENT/REFUND' AFTER `payment_method`;

-- enrollment_order.payment_status 注释扩展 REFUNDED（varchar 长度无需调整，仅注释说明）
ALTER TABLE `enrollment_order`
  MODIFY COLUMN `payment_status` varchar(32) NOT NULL DEFAULT 'UNPAID'
    COMMENT 'Payment status: UNPAID/PARTIAL/PAID/REFUNDED';

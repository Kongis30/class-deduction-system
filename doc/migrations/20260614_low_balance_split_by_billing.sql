-- ============================================================================
-- Migration: 低额度提醒按计费口径拆分（课时 / 课次 / 月份）
-- Date: 2026-06-14
-- 适用表: notification_trigger_rule
--
-- 说明:
--   本项目无 Flyway/Liquibase，schema 变更需手工执行本脚本，并同步
--   doc/mysql_schema_from_db.sql。请在执行前备份数据库。
--
--   原单一 LOW_BALANCE 触发类型无法区分三种计费口径的阈值单位，现拆分为：
--     - LOW_BALANCE_HOUR    : 1v1 按课时，阈值单位 = 0.5h units（remaining_units）
--     - LOW_BALANCE_SESSION : 班课 PER_SESSION，阈值单位 = 课次
--     - LOW_BALANCE_MONTH   : 班课 MONTHLY，阈值单位 = 月份
--   三者可独立开关。保守起见，新增的课次/月份规则默认停用（enabled=0）。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 旧 LOW_BALANCE 规则改名为 LOW_BALANCE_HOUR（保留原 template_id、阈值、
--    开关、收件人等配置不变）。
-- ---------------------------------------------------------------------------
UPDATE `notification_trigger_rule`
SET `trigger_type` = 'LOW_BALANCE_HOUR',
    `remark`       = 'Notify guardian when 1v1 remaining hour units drop below threshold (unit = 0.5h)'
WHERE `trigger_type` = 'LOW_BALANCE'
  AND `is_deleted` = 0;

-- ---------------------------------------------------------------------------
-- 2. 新增班课 PER_SESSION 低课次提醒规则（默认阈值 2 课次，默认停用）。
--    沿用 LOW_BALANCE_DEFAULT 模板（id=2），收件人为监护人邮箱。
-- ---------------------------------------------------------------------------
INSERT INTO `notification_trigger_rule`
  (`id`, `trigger_type`, `template_id`, `enabled`, `recipient_type`, `custom_recipients`, `threshold_value`, `remark`)
VALUES
  (3, 'LOW_BALANCE_SESSION', 2, 0, 'GUARDIAN_EMAIL', NULL, 2, 'Notify guardian when GROUP PER_SESSION remaining sessions drop below threshold (unit = session)')
ON DUPLICATE KEY UPDATE id=id;

-- ---------------------------------------------------------------------------
-- 3. 新增班课 MONTHLY 低月份提醒规则（默认阈值 1 月份，默认停用）。
--    沿用 LOW_BALANCE_DEFAULT 模板（id=2），收件人为监护人邮箱。
-- ---------------------------------------------------------------------------
INSERT INTO `notification_trigger_rule`
  (`id`, `trigger_type`, `template_id`, `enabled`, `recipient_type`, `custom_recipients`, `threshold_value`, `remark`)
VALUES
  (4, 'LOW_BALANCE_MONTH', 2, 0, 'GUARDIAN_EMAIL', NULL, 1, 'Notify guardian when GROUP MONTHLY remaining months drop below threshold (unit = month)')
ON DUPLICATE KEY UPDATE id=id;

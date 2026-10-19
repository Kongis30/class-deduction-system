-- 记录复用旧排课规则 ID 时已核实的当前分配周期起点。
-- 新周期仍由 supersedes_rule_id / 规则创建时间标识；本列只兼容旧规则的同周期重建。
DELIMITER $$

DROP PROCEDURE IF EXISTS add_explicit_one_on_one_period_start$$
CREATE PROCEDURE add_explicit_one_on_one_period_start()
BEGIN
    DECLARE column_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO column_exists
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'recurring_schedule_rule'
       AND column_name = 'allocation_period_started_at';

    IF column_exists = 0 THEN
        ALTER TABLE recurring_schedule_rule
            ADD COLUMN allocation_period_started_at datetime(3) DEFAULT NULL
                COMMENT 'Verified allocation period start when a legacy rule ID is reused'
                AFTER superseded_by_rule_id;
    END IF;
END$$

CALL add_explicit_one_on_one_period_start()$$
DROP PROCEDURE add_explicit_one_on_one_period_start$$

DELIMITER ;

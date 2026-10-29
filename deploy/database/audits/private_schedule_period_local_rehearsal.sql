-- 仅允许在本地隔离审计库演练；不是线上迁移脚本。
-- 这四条新规则的旧规则双向关联、起始余额快照和未重建课位均可核验。
-- 写入值与当前 COALESCE 读取结果相同；不能据此推断其他旧规则边界。
DELIMITER $$
DROP PROCEDURE IF EXISTS local_period_rehearsal_20261029$$
CREATE PROCEDURE local_period_rehearsal_20261029()
BEGIN
    DECLARE matched_count INT DEFAULT 0;
    DECLARE changed_count INT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    IF DATABASE() <> 'jin_period_audit_20261029_01' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Local rehearsal database required';
    END IF;

    CREATE TEMPORARY TABLE expected_periods (
        rule_id BIGINT PRIMARY KEY, package_id BIGINT NOT NULL,
        previous_rule_id BIGINT NOT NULL, period_start DATETIME(3) NOT NULL
    );
    INSERT INTO expected_periods VALUES
        (2111920968370274305, 2104961042045587458, 2105013687351250946, '2026-10-18 16:42:51.111'),
        (2111935751962279937, 2104209912395051010, 2111935384214093825, '2026-10-18 17:41:35.794'),
        (2113455381851725825, 2103237334335541250, 2104050839548612610, '2026-10-22 22:20:03.796'),
        (2115630796150251521, 2102533807099469825, 2104300139704856578, '2026-10-28 22:24:22.979');

    START TRANSACTION;
    SELECT COUNT(*) INTO matched_count
    FROM expected_periods e
    JOIN recurring_schedule_rule r ON r.id = e.rule_id
    JOIN recurring_schedule_rule previous ON previous.id = e.previous_rule_id
    WHERE r.package_id = e.package_id AND r.created_at = e.period_start
      AND r.updated_at = e.period_start AND r.version = 0
      AND r.is_deleted = 0 AND r.rule_status = 'ACTIVE'
      AND r.allocation_period_started_at IS NULL AND r.supersedes_rule_id = previous.id
      AND previous.is_deleted = 0 AND previous.rule_status = 'ENDED'
      AND previous.package_id = e.package_id AND previous.superseded_by_rule_id = r.id
      AND previous.created_at <= r.created_at
      AND EXISTS (SELECT 1 FROM recurring_schedule_starting_balance b
                  WHERE b.schedule_rule_id = r.id AND b.is_deleted = 0)
      AND EXISTS (SELECT 1 FROM recurring_schedule_slot s
                  WHERE s.rule_id = r.id AND s.is_deleted = 0 AND s.slot_status = 'ACTIVE')
      AND NOT EXISTS (SELECT 1 FROM recurring_schedule_slot s WHERE s.rule_id = r.id AND s.is_deleted = 1);
    IF matched_count <> 4 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Rehearsal evidence changed; nothing committed';
    END IF;

    UPDATE recurring_schedule_rule r JOIN expected_periods e ON e.rule_id = r.id
    SET r.allocation_period_started_at = e.period_start, r.updated_at = r.updated_at
    WHERE r.allocation_period_started_at IS NULL AND r.created_at = e.period_start;
    SET changed_count = ROW_COUNT();
    IF changed_count <> 4 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Rehearsal update count changed; nothing committed';
    END IF;
    COMMIT;
    DROP TEMPORARY TABLE expected_periods;
END$$
DELIMITER ;
CALL local_period_rehearsal_20261029();
DROP PROCEDURE local_period_rehearsal_20261029;

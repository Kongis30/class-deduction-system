-- 只回填可由旧、新课位证明没有改变老师、星期、时间和分配额的周期边界。
-- 必须先部署 recurring_schedule_rule.allocation_period_started_at 字段及读取它的后端版本。
-- 本修复不写课包、课位、课次、消课记录或课时流水；Erika Yu 的 16h 分配额不在修复范围。
-- 单次执行；任意一条线上记录与审计快照不符时整体回滚。

DELIMITER $$
CREATE PROCEDURE restore_one_on_one_period_boundaries_20261017()
BEGIN
    DECLARE changed_count INT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    CREATE TEMPORARY TABLE expected_period_repair (
        rule_id BIGINT PRIMARY KEY,
        package_id BIGINT NOT NULL,
        previous_slot_id BIGINT NOT NULL,
        current_slot_id BIGINT NOT NULL,
        period_start DATETIME(3) NOT NULL,
        allocated_units INT NOT NULL
    );
    INSERT INTO expected_period_repair VALUES
        -- Ethan: opening-hours adjustment rebuilt an unchanged slot after the first lesson.
        (2102839910555287553, 2102839632263217153,
         2102839910584647681, 2102938659461836801, '2026-09-23 15:17:58.218', 80),
        -- Beau: Oct 8 changed only classroom/delivery metadata; Sep 27 was the last actual schedule edit.
        (2104094525741408258, 2104093901264068609,
         2104382388127248386, 2108400392708136962, '2026-09-27 21:27:13.521', 56),
        -- Shared group: NULL and IN_PERSON are the same delivery mode; teacher/time/allocation stayed fixed.
        (2104089263521472513, 2104088998886055937,
         2104089263525666817, 2111147010062643202, '2026-09-27 02:02:27.171', 68);

    START TRANSACTION;
    UPDATE recurring_schedule_rule r
    JOIN expected_period_repair expected ON expected.rule_id = r.id
    JOIN recurring_schedule_slot previous_slot ON previous_slot.id = expected.previous_slot_id
        AND previous_slot.rule_id = r.id
    JOIN recurring_schedule_slot current_slot ON current_slot.id = expected.current_slot_id
        AND current_slot.rule_id = r.id
    SET r.allocation_period_started_at = expected.period_start
    WHERE r.package_id = expected.package_id
      AND r.rule_status = 'ACTIVE'
      AND r.is_deleted = 0
      AND r.supersedes_rule_id IS NULL
      AND r.allocation_period_started_at IS NULL
      AND previous_slot.is_deleted = 1
      AND previous_slot.created_at = expected.period_start
      AND current_slot.is_deleted = 0
      AND current_slot.slot_status = 'ACTIVE'
      AND current_slot.created_at > expected.period_start
      AND current_slot.allocated_units = expected.allocated_units
      AND previous_slot.allocated_units = expected.allocated_units
      AND previous_slot.teacher_id <=> current_slot.teacher_id
      AND previous_slot.weekday = current_slot.weekday
      AND previous_slot.start_time = current_slot.start_time
      AND previous_slot.end_time = current_slot.end_time
      AND previous_slot.planned_units = current_slot.planned_units
      AND previous_slot.subject_name <=> current_slot.subject_name
      AND COALESCE(previous_slot.delivery_mode, 'IN_PERSON')
          = COALESCE(current_slot.delivery_mode, 'IN_PERSON')
      AND NOT EXISTS (
          SELECT 1 FROM recurring_schedule_slot other_slot
          WHERE other_slot.rule_id = r.id
            AND other_slot.id <> current_slot.id
            AND other_slot.is_deleted = 0
            AND other_slot.slot_status = 'ACTIVE'
      );
    SET changed_count = ROW_COUNT();
    IF changed_count <> 3 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Period repair preconditions changed; no rows committed';
    END IF;
    COMMIT;
    DROP TEMPORARY TABLE expected_period_repair;
END$$
DELIMITER ;

CALL restore_one_on_one_period_boundaries_20261017();
DROP PROCEDURE restore_one_on_one_period_boundaries_20261017;

SELECT id, package_id, allocation_period_started_at
FROM recurring_schedule_rule
WHERE id IN (2102839910555287553, 2104094525741408258, 2104089263521472513)
ORDER BY id;

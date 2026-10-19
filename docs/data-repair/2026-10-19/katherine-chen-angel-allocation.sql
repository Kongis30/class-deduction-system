-- Restore Katherine Chen's canceled 2026-12-19 Angel lesson and its 2h allocation.
-- Guarded by the exact state observed on 2026-10-19; safe to abort if anything changed.
USE class_deduction_system_v2;

DROP PROCEDURE IF EXISTS repair_katherine_chen_angel_allocation_20261019;
DELIMITER //
CREATE PROCEDURE repair_katherine_chen_angel_allocation_20261019()
BEGIN
    DECLARE v_match_count INT DEFAULT 0;
    DECLARE v_changed_rows INT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    SELECT COUNT(*) INTO v_match_count
    FROM recurring_schedule_slot s
    JOIN recurring_schedule_rule r ON r.id = s.rule_id
    JOIN course_package p ON p.id = r.package_id
    JOIN lesson_occurrence o ON o.schedule_slot_id = s.id
    WHERE s.id = 2103121967609073667
      AND s.teacher_id = 2095298006443663362
      AND s.allocated_units = 48
      AND s.version = 0
      AND s.slot_status = 'ACTIVE'
      AND s.is_deleted = 0
      AND r.id = 2103121967604879361
      AND r.package_id = 2103120080113553410
      AND r.rule_status = 'ACTIVE'
      AND r.is_deleted = 0
      AND p.package_status = 'ACTIVE'
      AND p.estimated_end_date = '2026-12-17'
      AND p.version = 10
      AND p.remaining_units = 76
      AND p.consumed_units = 44
      AND p.is_deleted = 0
      AND o.id = 2106423030546169858
      AND o.package_id = p.id
      AND o.schedule_rule_id = r.id
      AND o.schedule_slot_id = s.id
      AND o.planned_teacher_id = s.teacher_id
      AND o.planning_status = 'CANCELLED'
      AND o.version = 1
      AND o.planned_start_at = '2026-12-19 15:00:00.000'
      AND o.planned_units = 4
      AND o.is_deleted = 0
      AND NOT EXISTS (
          SELECT 1 FROM lesson_record lr WHERE lr.occurrence_id = o.id
      );

    IF v_match_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: target data no longer matches the verified state.';
    END IF;

    UPDATE lesson_occurrence
    SET planning_status = 'SCHEDULED', version = version + 1
    WHERE id = 2106423030546169858
      AND planning_status = 'CANCELLED'
      AND version = 1
      AND is_deleted = 0;
    SET v_changed_rows = ROW_COUNT();
    IF v_changed_rows <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: expected exactly one occurrence update.';
    END IF;

    UPDATE recurring_schedule_slot
    SET allocated_units = 52, version = version + 1
    WHERE id = 2103121967609073667
      AND allocated_units = 48
      AND version = 0
      AND slot_status = 'ACTIVE'
      AND is_deleted = 0;
    SET v_changed_rows = ROW_COUNT();
    IF v_changed_rows <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: expected exactly one schedule allocation update.';
    END IF;

    UPDATE course_package
    SET estimated_end_date = '2026-12-19', version = version + 1
    WHERE id = 2103120080113553410
      AND estimated_end_date = '2026-12-17'
      AND version = 10
      AND remaining_units = 76
      AND consumed_units = 44
      AND package_status = 'ACTIVE'
      AND is_deleted = 0;
    SET v_changed_rows = ROW_COUNT();
    IF v_changed_rows <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: expected exactly one package end-date update.';
    END IF;

    COMMIT;
END//
DELIMITER ;

CALL repair_katherine_chen_angel_allocation_20261019();
DROP PROCEDURE repair_katherine_chen_angel_allocation_20261019;

-- Expected final state: Angel allocated 26h, deducted 6h, remaining 20h;
-- package remaining and consumed balances are intentionally unchanged.
SELECT s.id AS slot_id, s.allocated_units, s.version
FROM recurring_schedule_slot s
WHERE s.id = 2103121967609073667;

SELECT o.id AS occurrence_id, o.planning_status, o.version, o.planned_start_at, o.planned_units
FROM lesson_occurrence o
WHERE o.id = 2106423030546169858;

SELECT p.id AS package_id, p.estimated_end_date, p.version, p.remaining_units, p.consumed_units
FROM course_package p
WHERE p.id = 2103120080113553410;

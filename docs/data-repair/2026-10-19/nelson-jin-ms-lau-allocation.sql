-- Correct Nelson Jin's package starting balance and Ms. Lau allocation.
-- The 2026-11-01 class was canceled without an attendance record; restore it as an
-- unprocessed scheduled occurrence so lesson history and the 12h remaining agree.
USE class_deduction_system_v2;

DROP PROCEDURE IF EXISTS repair_nelson_jin_ms_lau_allocation_20261019;
DELIMITER //
CREATE PROCEDURE repair_nelson_jin_ms_lau_allocation_20261019()
BEGIN
    DECLARE v_match_count INT DEFAULT 0;
    DECLARE v_changed_rows INT DEFAULT 0;
    DECLARE v_ledger_id BIGINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    SELECT COUNT(*) INTO v_match_count
    FROM course_package p
    JOIN recurring_schedule_rule r ON r.package_id = p.id
    JOIN recurring_schedule_slot s ON s.rule_id = r.id
    JOIN lesson_occurrence o ON o.id = 2104449078118592513
    JOIN hour_ledger opening ON opening.package_id = p.id
    JOIN package_balance_snapshot snap ON snap.package_id = p.id
    WHERE p.id = 2104059699818680321
      AND p.student_id = 2104059699759960065
      AND p.total_units = 240
      AND p.opening_used_units = 184
      AND p.consumed_units = 220
      AND p.remaining_units = 20
      AND p.estimated_end_date = '2026-10-29'
      AND p.package_status = 'ACTIVE'
      AND p.version = 10
      AND p.is_deleted = 0
      AND r.id = 2104060493775261698
      AND r.rule_status = 'ACTIVE'
      AND r.is_deleted = 0
      AND s.id = 2104060493783650305
      AND s.teacher_id = 2086093048800407554
      AND s.allocated_units = 20
      AND s.version = 0
      AND s.slot_status = 'ACTIVE'
      AND s.is_deleted = 0
      AND o.package_id = p.id
      AND o.schedule_rule_id = r.id
      AND o.schedule_slot_id = s.id
      AND o.planned_teacher_id = s.teacher_id
      AND o.planned_start_at = '2026-11-01 11:00:00.000'
      AND o.planned_end_at = '2026-11-01 13:00:00.000'
      AND o.planned_units = 4
      AND o.planning_status = 'CANCELLED'
      AND o.version = 1
      AND o.is_deleted = 0
      AND opening.id = 2104059809818497026
      AND opening.ledger_type = 'MANUAL_ADJUST'
      AND opening.change_units = -184
      AND opening.balance_after_units = 56
      AND opening.reason_code = 'OPENING_USED_HOURS'
      AND opening.is_deleted = 0
      AND snap.total_units = 240
      AND snap.consumed_units = 220
      AND snap.remaining_units = 20
      AND snap.last_ledger_id = 2111917229924204545
      AND (SELECT COALESCE(SUM(active_slot.allocated_units), 0)
           FROM recurring_schedule_slot active_slot
           WHERE active_slot.rule_id = r.id
             AND active_slot.slot_status = 'ACTIVE'
             AND active_slot.is_deleted = 0) = 56
      AND (SELECT COALESCE(SUM(active_occurrence.planned_units), 0)
           FROM lesson_occurrence active_occurrence
           WHERE active_occurrence.schedule_rule_id = r.id
             AND active_occurrence.is_deleted = 0
             AND active_occurrence.planning_status <> 'CANCELLED') = 56
      AND NOT EXISTS (
          SELECT 1 FROM lesson_record lr
          WHERE lr.occurrence_id = o.id
      )
      AND NOT EXISTS (
          SELECT 1 FROM lesson_occurrence duplicate
          WHERE duplicate.schedule_slot_id = s.id
            AND duplicate.planned_start_at = o.planned_start_at
            AND duplicate.planning_status = 'SCHEDULED'
            AND duplicate.is_deleted = 0
      );

    IF v_match_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: Nelson Jin target rows no longer match the reviewed state.';
    END IF;

    -- Correct opening-used from 92h to 90h, retaining the original ledger row.
    SELECT COALESCE(MAX(id), 0) + 1 INTO v_ledger_id FROM hour_ledger;
    INSERT INTO hour_ledger (
        id, package_id, ledger_type, change_units, balance_after_units,
        business_date, reason_code, reason_desc, operated_by, operated_at,
        created_at, updated_at, is_deleted, version
    ) VALUES (
        v_ledger_id, 2104059699818680321, 'MANUAL_ADJUST', 4, 24,
        '2026-09-27', 'OPENING_USED_HOURS_CORRECTION',
        'Reversed the extra 2h opening-used adjustment; Starting Hours corrected from 28h to 30h per admin review on 2026-10-19.',
        1, NOW(3), NOW(3), NOW(3), 0, 0
    );

    UPDATE course_package
    SET opening_used_units = 180,
        consumed_units = 216,
        remaining_units = 24,
        estimated_end_date = '2026-11-01',
        version = version + 1
    WHERE id = 2104059699818680321
      AND opening_used_units = 184
      AND consumed_units = 220
      AND remaining_units = 20
      AND estimated_end_date = '2026-10-29'
      AND version = 10
      AND is_deleted = 0;
    SET v_changed_rows = ROW_COUNT();
    IF v_changed_rows <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: expected exactly one course package update.';
    END IF;

    UPDATE package_balance_snapshot
    SET consumed_units = 216,
        remaining_units = 24,
        last_ledger_id = v_ledger_id,
        snapshot_at = NOW(3)
    WHERE package_id = 2104059699818680321
      AND total_units = 240
      AND consumed_units = 220
      AND remaining_units = 20
      AND last_ledger_id = 2111917229924204545;
    SET v_changed_rows = ROW_COUNT();
    IF v_changed_rows <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: expected exactly one package balance snapshot update.';
    END IF;

    -- Add 2h to the Sunday slot to match its restored final lesson.
    UPDATE recurring_schedule_slot
    SET allocated_units = 24, version = version + 1, updated_by = 1
    WHERE id = 2104060493783650305
      AND teacher_id = 2086093048800407554
      AND allocated_units = 20
      AND version = 0
      AND slot_status = 'ACTIVE'
      AND is_deleted = 0;
    SET v_changed_rows = ROW_COUNT();
    IF v_changed_rows <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: expected exactly one Ms. Lau allocation update.';
    END IF;

    -- Restore the 11/01 lesson as scheduled; no attendance record is fabricated.
    UPDATE lesson_occurrence
    SET planning_status = 'SCHEDULED',
        notes = 'Restored as an unprocessed scheduled lesson during the 2026-10-19 allocation correction.',
        version = version + 1,
        updated_by = 1
    WHERE id = 2104449078118592513
      AND schedule_slot_id = 2104060493783650305
      AND planned_start_at = '2026-11-01 11:00:00.000'
      AND planned_end_at = '2026-11-01 13:00:00.000'
      AND planned_units = 4
      AND planning_status = 'CANCELLED'
      AND version = 1
      AND is_deleted = 0;
    SET v_changed_rows = ROW_COUNT();
    IF v_changed_rows <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: expected exactly one 11/01 lesson restoration.';
    END IF;

    COMMIT;
END//
DELIMITER ;

CALL repair_nelson_jin_ms_lau_allocation_20261019();
DROP PROCEDURE repair_nelson_jin_ms_lau_allocation_20261019;

-- Expected: Total 120h, Starting 30h, package Remaining 12h;
-- Ms. Lau Allocated 30h, Deducted 18h, Remaining 12h.
SELECT p.id AS package_id, p.total_units, p.opening_used_units, p.consumed_units,
       p.remaining_units, p.estimated_end_date
FROM course_package p WHERE p.id = 2104059699818680321;

SELECT s.id AS slot_id, s.allocated_units, s.version,
       SUM(CASE WHEN o.planning_status <> 'CANCELLED' AND o.is_deleted = 0
                THEN o.planned_units ELSE 0 END) AS aligned_occurrence_units
FROM recurring_schedule_slot s
LEFT JOIN lesson_occurrence o ON o.schedule_slot_id = s.id
WHERE s.rule_id = 2104060493775261698
  AND s.slot_status = 'ACTIVE' AND s.is_deleted = 0
GROUP BY s.id, s.allocated_units, s.version;

SELECT id, schedule_slot_id, planning_status, is_deleted, planned_start_at,
       planned_end_at, planned_units, version
FROM lesson_occurrence WHERE id = 2104449078118592513;

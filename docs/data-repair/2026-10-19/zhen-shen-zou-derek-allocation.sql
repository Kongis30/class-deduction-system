-- Correct Zhen Shen Zou's package starting hours and restore Derek's 2026-11-22 lesson.
-- Exact-state checks make the repair abort rather than overwrite any newer changes.
USE class_deduction_system_v2;

DROP PROCEDURE IF EXISTS repair_zhen_shen_zou_derek_allocation_20261019;
DELIMITER //
CREATE PROCEDURE repair_zhen_shen_zou_derek_allocation_20261019()
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
    JOIN lesson_occurrence o ON o.id = 2104050839682830338
    JOIN hour_ledger opening ON opening.package_id = p.id
    JOIN package_balance_snapshot snap ON snap.package_id = p.id
    WHERE p.id = 2103237334335541250
      AND p.total_units = 120
      AND p.opening_used_units = 60
      AND p.consumed_units = 92
      AND p.remaining_units = 28
      AND p.estimated_end_date = '2026-11-15'
      AND p.package_status = 'ACTIVE'
      AND p.version = 10
      AND p.is_deleted = 0
      AND r.id = 2104050839548612610
      AND r.rule_status = 'ACTIVE'
      AND r.is_deleted = 0
      AND s.id = 2104051305015693314
      AND s.teacher_id = 2086092345428209665
      AND s.allocated_units = 32
      AND s.version = 0
      AND s.slot_status = 'ACTIVE'
      AND s.is_deleted = 0
      AND o.id = 2104050839682830338
      AND o.schedule_slot_id = 2104050839565389826
      AND o.package_id = p.id
      AND o.planned_teacher_id = s.teacher_id
      AND o.planned_start_at = '2026-11-22 11:00:00.000'
      AND o.planned_end_at = '2026-11-22 13:00:00.000'
      AND o.planned_units = 4
      AND o.planning_status = 'SCHEDULED'
      AND o.version = 0
      AND o.is_deleted = 1
      AND EXISTS (
          SELECT 1 FROM recurring_schedule_slot old_slot
          WHERE old_slot.id = o.schedule_slot_id
            AND old_slot.rule_id = r.id
            AND old_slot.teacher_id = s.teacher_id
            AND old_slot.is_deleted = 1
      )
      AND opening.id = 2104049796152578049
      AND opening.ledger_type = 'MANUAL_ADJUST'
      AND opening.change_units = -4
      AND opening.reason_code = 'OPENING_USED_HOURS'
      AND opening.is_deleted = 0
      AND snap.total_units = 120
      AND snap.consumed_units = 92
      AND snap.remaining_units = 28
      AND snap.last_ledger_id = 2111917089905754113
      AND EXISTS (
          SELECT 1 FROM hour_ledger first_opening
          WHERE first_opening.id = 2103238105085038593
            AND first_opening.package_id = p.id
            AND first_opening.ledger_type = 'MANUAL_ADJUST'
            AND first_opening.change_units = -56
            AND first_opening.reason_code = 'OPENING_USED_HOURS'
            AND first_opening.is_deleted = 0
      )
      AND NOT EXISTS (
          SELECT 1 FROM lesson_record lr
          WHERE lr.occurrence_id = o.id AND lr.is_deleted = 0
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
            SET MESSAGE_TEXT = 'Repair stopped: Zhen Shen Zou target rows no longer match the reviewed state.';
    END IF;

    -- The second opening-used entry on 9/26 incorrectly lowered Starting from 32h to 30h.
    -- Preserve both historic entries and add a traceable +2h correction.
    SELECT COALESCE(MAX(id), 0) + 1 INTO v_ledger_id FROM hour_ledger;
    INSERT INTO hour_ledger (
        id, package_id, ledger_type, change_units, balance_after_units,
        business_date, reason_code, reason_desc, operated_by, operated_at,
        created_at, updated_at, is_deleted, version
    ) VALUES (
        v_ledger_id, 2103237334335541250, 'MANUAL_ADJUST', 4, 32,
        '2026-09-26', 'OPENING_USED_HOURS_CORRECTION',
        'Reversed the extra 2h opening-used adjustment; Starting Hours corrected from 30h to 32h per admin review on 2026-10-19.',
        1, NOW(3), NOW(3), NOW(3), 0, 0
    );

    UPDATE course_package
    SET opening_used_units = 56,
        consumed_units = 88,
        remaining_units = 32,
        estimated_end_date = '2026-11-22',
        version = version + 1
    WHERE id = 2103237334335541250
      AND opening_used_units = 60
      AND consumed_units = 92
      AND remaining_units = 28
      AND estimated_end_date = '2026-11-15'
      AND version = 10
      AND is_deleted = 0;
    SET v_changed_rows = ROW_COUNT();
    IF v_changed_rows <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: expected exactly one course package update.';
    END IF;

    UPDATE package_balance_snapshot
    SET consumed_units = 88,
        remaining_units = 32,
        last_ledger_id = v_ledger_id,
        snapshot_at = NOW(3)
    WHERE package_id = 2103237334335541250
      AND total_units = 120
      AND consumed_units = 92
      AND remaining_units = 28
      AND last_ledger_id = 2111917089905754113;
    SET v_changed_rows = ROW_COUNT();
    IF v_changed_rows <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: expected exactly one package balance snapshot update.';
    END IF;

    UPDATE recurring_schedule_slot
    SET allocated_units = 36, version = version + 1, updated_by = 1
    WHERE id = 2104051305015693314
      AND teacher_id = 2086092345428209665
      AND allocated_units = 32
      AND version = 0
      AND slot_status = 'ACTIVE'
      AND is_deleted = 0;
    SET v_changed_rows = ROW_COUNT();
    IF v_changed_rows <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: expected exactly one Mr. Derek allocation update.';
    END IF;

    -- Restore the matching, unprocessed 11/22 lesson from the superseded slot to the active slot.
    UPDATE lesson_occurrence
    SET schedule_slot_id = 2104051305015693314,
        is_deleted = 0,
        deleted_at = NULL,
        deleted_by = NULL,
        notes = 'Restored to the active Derek slot during the 2026-10-19 allocation correction.',
        version = version + 1,
        updated_by = 1
    WHERE id = 2104050839682830338
      AND schedule_slot_id = 2104050839565389826
      AND planned_start_at = '2026-11-22 11:00:00.000'
      AND planned_end_at = '2026-11-22 13:00:00.000'
      AND planned_units = 4
      AND planning_status = 'SCHEDULED'
      AND version = 0
      AND is_deleted = 1;
    SET v_changed_rows = ROW_COUNT();
    IF v_changed_rows <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: expected exactly one 11/22 future lesson restoration.';
    END IF;

    COMMIT;
END//
DELIMITER ;

CALL repair_zhen_shen_zou_derek_allocation_20261019();
DROP PROCEDURE repair_zhen_shen_zou_derek_allocation_20261019;

-- Expected: Total 60h, Starting 32h; Derek Allocated 18h, Deducted 8h,
-- Remaining with Derek 10h; the restored 11/22 lesson is part of those 10h.
SELECT p.id AS package_id, p.total_units, p.opening_used_units, p.consumed_units,
       p.remaining_units, p.estimated_end_date
FROM course_package p WHERE p.id = 2103237334335541250;

SELECT s.id AS slot_id, s.allocated_units, s.version,
       SUM(CASE WHEN o.planning_status = 'SCHEDULED' AND o.is_deleted = 0
                THEN o.planned_units ELSE 0 END) AS future_scheduled_units
FROM recurring_schedule_slot s
LEFT JOIN lesson_occurrence o ON o.schedule_slot_id = s.id
WHERE s.id = 2104051305015693314
GROUP BY s.id, s.allocated_units, s.version;

SELECT id, schedule_slot_id, planning_status, is_deleted, planned_start_at,
       planned_end_at, planned_units, version
FROM lesson_occurrence WHERE id = 2104050839682830338;

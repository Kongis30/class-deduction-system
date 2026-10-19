-- Restore Jinghe Hu / Ms. Claudia's current-period allocation to 18h and
-- add the missing 1h final Saturday occurrence without rewriting past records.
USE class_deduction_system_v2;

DROP PROCEDURE IF EXISTS repair_jinghe_hu_claudia_allocation_20261019;
DELIMITER //
CREATE PROCEDURE repair_jinghe_hu_claudia_allocation_20261019()
BEGIN
    DECLARE v_match_count INT DEFAULT 0;
    DECLARE v_changed_rows INT DEFAULT 0;
    DECLARE v_occurrence_id BIGINT;
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
    JOIN lesson_occurrence source_row ON source_row.id = 2104050533171482625
    WHERE p.id = 2103131259800178689
      AND p.student_id = 2103131259745652737
      AND p.total_units = 120
      AND p.opening_used_units = 48
      AND p.consumed_units = 78
      AND p.remaining_units = 42
      AND p.estimated_end_date = '2026-11-22'
      AND p.package_status = 'ACTIVE'
      AND p.version = 11
      AND p.is_deleted = 0
      AND r.id = 2103132358460362753
      AND r.rule_status = 'ACTIVE'
      AND r.is_deleted = 0
      AND s.id = 2104050533041459202
      AND s.teacher_id = 2095304710883418113
      AND s.allocated_units = 32
      AND s.version = 1
      AND s.slot_status = 'ACTIVE'
      AND s.is_deleted = 0
      AND source_row.package_id = p.id
      AND source_row.schedule_rule_id = r.id
      AND source_row.schedule_slot_id = s.id
      AND source_row.planned_teacher_id = s.teacher_id
      AND source_row.planned_start_at = '2026-11-21 11:00:00.000'
      AND source_row.planned_end_at = '2026-11-21 13:00:00.000'
      AND source_row.planned_units = 4
      AND source_row.planning_status = 'SCHEDULED'
      AND source_row.is_deleted = 0
      AND (SELECT COALESCE(SUM(active_occurrence.planned_units), 0)
           FROM lesson_occurrence active_occurrence
           WHERE active_occurrence.schedule_slot_id = s.id
             AND active_occurrence.is_deleted = 0
             AND active_occurrence.planning_status <> 'CANCELLED') = 34
      AND (SELECT COALESCE(SUM(future_occurrence.planned_units), 0)
           FROM lesson_occurrence future_occurrence
           WHERE future_occurrence.schedule_slot_id = s.id
             AND future_occurrence.planned_start_at >= '2026-10-19 00:00:00'
             AND future_occurrence.is_deleted = 0
             AND future_occurrence.planning_status = 'SCHEDULED') = 20
      AND (SELECT COALESCE(SUM(lr.deducted_units), 0)
           FROM lesson_record lr
           JOIN lesson_occurrence recorded ON recorded.id = lr.occurrence_id
           WHERE recorded.schedule_slot_id = s.id
             AND lr.planned_teacher_id = s.teacher_id
             AND lr.confirm_stage = 'CONFIRMED'
             AND lr.is_deleted = 0) = 14
      AND NOT EXISTS (
          SELECT 1 FROM lesson_occurrence target
          WHERE target.package_id = p.id
            AND target.planned_start_at = '2026-11-28 11:00:00.000'
      )
      AND NOT EXISTS (
          SELECT 1 FROM lesson_record target_record
          WHERE target_record.package_id = p.id
            AND target_record.planned_start_at = '2026-11-28 11:00:00.000'
      )
      AND NOT EXISTS (
          SELECT 1 FROM lesson_occurrence conflict
          WHERE conflict.is_deleted = 0
            AND conflict.planning_status <> 'CANCELLED'
            AND conflict.planned_start_at < '2026-11-28 12:00:00.000'
            AND conflict.planned_end_at > '2026-11-28 11:00:00.000'
            AND (conflict.student_id = p.student_id
                 OR conflict.planned_teacher_id = s.teacher_id
                 OR (s.classroom_id IS NOT NULL
                     AND conflict.planned_classroom_id = s.classroom_id))
      );

    IF v_match_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: Jinghe Hu / Ms. Claudia state or 11/28 availability changed.';
    END IF;

    UPDATE recurring_schedule_slot
    SET allocated_units = 36,
        version = version + 1,
        updated_by = 1
    WHERE id = 2104050533041459202
      AND teacher_id = 2095304710883418113
      AND allocated_units = 32
      AND version = 1
      AND slot_status = 'ACTIVE'
      AND is_deleted = 0;
    SET v_changed_rows = ROW_COUNT();
    IF v_changed_rows <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: expected exactly one Ms. Claudia allocation update.';
    END IF;

    SET v_occurrence_id = UUID_SHORT();
    INSERT INTO lesson_occurrence (
        id, lesson_no, package_id, private_lesson_group_id, class_group_id, student_id,
        schedule_rule_id, schedule_slot_id, occurrence_type, leave_carryover_units,
        planned_teacher_id, subject_name, delivery_mode, planned_classroom_id,
        classroom_overridden, planned_start_at, planned_end_at, planned_units,
        planning_status, conflict_checked, notes, created_by, updated_by,
        is_deleted, version
    )
    SELECT v_occurrence_id, CONCAT('LS', v_occurrence_id), source_row.package_id,
           source_row.private_lesson_group_id, source_row.class_group_id, source_row.student_id,
           source_row.schedule_rule_id, source_row.schedule_slot_id, 'REGULAR', 0,
           source_row.planned_teacher_id, source_row.subject_name, source_row.delivery_mode,
           source_row.planned_classroom_id, source_row.classroom_overridden,
           DATE_ADD(source_row.planned_start_at, INTERVAL 7 DAY),
           DATE_ADD(DATE_ADD(source_row.planned_start_at, INTERVAL 7 DAY), INTERVAL 1 HOUR), 2,
           'SCHEDULED', 1,
           'Restored final 1h occurrence to align Ms. Claudia allocation to 18h; 2026-10-19 repair.',
           1, 1, 0, 0
    FROM lesson_occurrence source_row
    WHERE source_row.id = 2104050533171482625
      AND source_row.package_id = 2103131259800178689
      AND source_row.schedule_slot_id = 2104050533041459202
      AND source_row.is_deleted = 0;
    SET v_changed_rows = ROW_COUNT();
    IF v_changed_rows <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: expected exactly one 11/28 final occurrence insert.';
    END IF;

    UPDATE course_package
    SET estimated_end_date = '2026-11-28',
        version = version + 1,
        updated_by = 1
    WHERE id = 2103131259800178689
      AND student_id = 2103131259745652737
      AND estimated_end_date = '2026-11-22'
      AND total_units = 120
      AND opening_used_units = 48
      AND consumed_units = 78
      AND remaining_units = 42
      AND version = 11
      AND is_deleted = 0;
    SET v_changed_rows = ROW_COUNT();
    IF v_changed_rows <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: expected exactly one package end-date update.';
    END IF;

    COMMIT;
END//
DELIMITER ;

CALL repair_jinghe_hu_claudia_allocation_20261019();
DROP PROCEDURE repair_jinghe_hu_claudia_allocation_20261019;

-- Expected: Ms. Claudia Allocated 18h, Deducted 7h, Remaining 11h;
-- future scheduled occurrences total 11h; package remaining stays 21h.
SELECT p.id AS package_id, p.total_units, p.opening_used_units, p.consumed_units,
       p.remaining_units, p.estimated_end_date
FROM course_package p WHERE p.id = 2103131259800178689;

SELECT s.id AS slot_id, s.allocated_units, s.version,
       SUM(CASE WHEN o.planning_status = 'SCHEDULED' AND o.is_deleted = 0
                THEN o.planned_units ELSE 0 END) AS future_scheduled_units
FROM recurring_schedule_slot s
LEFT JOIN lesson_occurrence o ON o.schedule_slot_id = s.id
WHERE s.id = 2104050533041459202
GROUP BY s.id, s.allocated_units, s.version;

SELECT id, lesson_no, schedule_slot_id, planned_start_at, planned_end_at,
       planned_units, planning_status, is_deleted, version
FROM lesson_occurrence
WHERE package_id = 2103131259800178689
  AND planned_start_at = '2026-11-28 11:00:00.000';

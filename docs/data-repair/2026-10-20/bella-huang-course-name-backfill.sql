-- Bella Huang: restore the four 1-on-1 lesson subject snapshots from their
-- original recurring slots, cross-checked against the current weekly schedule.
-- Run against class_deduction_system_v2 after reviewing the target IDs.
USE class_deduction_system_v2;

DROP PROCEDURE IF EXISTS repair_bella_huang_course_names_20261020;
DELIMITER //
CREATE PROCEDURE repair_bella_huang_course_names_20261020()
BEGIN
    DECLARE v_match_count INT DEFAULT 0;
    DECLARE v_updated_rows INT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    SELECT COUNT(*) INTO v_match_count
    FROM lesson_occurrence o
    JOIN course_package p ON p.id = o.package_id
    JOIN student st ON st.id = p.student_id
    JOIN recurring_schedule_rule r ON r.id = o.schedule_rule_id
    JOIN recurring_schedule_slot source_slot ON source_slot.id = o.schedule_slot_id
    WHERE o.id IN (2104095041208786945, 2104095041196204033,
                   2104095041212981250, 2104095041212981251)
      AND p.id = 2104081513282347009
      AND st.student_name = 'Bella Huang'
      AND r.id = 2104081817079980033
      AND r.rule_status = 'ACTIVE'
      AND r.is_deleted = 0
      AND o.is_deleted = 0
      AND o.subject_name IS NULL
      AND o.planning_status IN ('SCHEDULED', 'ADJUSTED')
      AND o.version = 0
      AND NOT EXISTS (
          SELECT 1 FROM lesson_record lr
          WHERE lr.occurrence_id = o.id AND lr.is_deleted = 0
      )
      AND (
          (o.id = 2104095041208786945
           AND o.schedule_slot_id = 2104095041183621121
           AND o.planned_start_at = '2026-10-01 17:00:00'
           AND source_slot.subject_name = 'G5 MATH')
          OR
          (o.id = 2104095041196204033
           AND o.schedule_slot_id = 2104095041179426818
           AND o.planned_start_at = '2026-10-02 17:00:00'
           AND source_slot.subject_name = 'G5 ELA')
          OR
          (o.id = 2104095041212981250
           AND o.schedule_slot_id = 2104095041179426818
           AND o.planned_start_at = '2026-10-06 17:00:00'
           AND source_slot.subject_name = 'G5 ELA')
          OR
          (o.id = 2104095041212981251
           AND o.schedule_slot_id = 2104095041183621121
           AND o.planned_start_at = '2026-10-08 17:00:00'
           AND source_slot.subject_name = 'G5 MATH')
      )
      AND EXISTS (
          SELECT 1 FROM recurring_schedule_slot active_slot
          WHERE active_slot.rule_id = r.id
            AND active_slot.slot_status = 'ACTIVE'
            AND active_slot.is_deleted = 0
            AND active_slot.teacher_id = source_slot.teacher_id
            AND active_slot.weekday = source_slot.weekday
            AND active_slot.start_time = source_slot.start_time
            AND active_slot.end_time = source_slot.end_time
            AND active_slot.subject_name = source_slot.subject_name
      );

    IF v_match_count <> 4 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: Bella Huang target rows or matching current slots changed.';
    END IF;

    UPDATE lesson_occurrence o
    JOIN recurring_schedule_slot source_slot ON source_slot.id = o.schedule_slot_id
    SET o.subject_name = source_slot.subject_name,
        o.version = o.version + 1,
        o.updated_at = NOW()
    WHERE o.id IN (2104095041208786945, 2104095041196204033,
                   2104095041212981250, 2104095041212981251)
      AND o.package_id = 2104081513282347009
      AND o.is_deleted = 0
      AND o.subject_name IS NULL
      AND o.version = 0;

    SET v_updated_rows = ROW_COUNT();
    IF v_updated_rows <> 4 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: expected to update exactly four lesson snapshots.';
    END IF;

    COMMIT;
END//
DELIMITER ;

CALL repair_bella_huang_course_names_20261020();
DROP PROCEDURE repair_bella_huang_course_names_20261020;

SELECT id, planned_start_at, subject_name, planning_status, version
FROM lesson_occurrence
WHERE id IN (2104095041208786945, 2104095041196204033,
             2104095041212981250, 2104095041212981251)
ORDER BY planned_start_at;

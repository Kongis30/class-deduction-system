-- Backfill only the seven unambiguous regular lesson snapshots approved for repair.
-- Brian Huang is intentionally excluded; Vincent Wang's ADHOC lesson is ambiguous.
USE class_deduction_system_v2;

DROP PROCEDURE IF EXISTS backfill_confirmed_course_names_20261020;
DELIMITER //
CREATE PROCEDURE backfill_confirmed_course_names_20261020()
BEGIN
    DECLARE v_eligible_rows INT DEFAULT 0;
    DECLARE v_updated_rows INT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    SELECT COUNT(*) INTO v_eligible_rows
    FROM (
        SELECT 2104382388139831298 AS occurrence_id, 'Beau Hayworth' AS student_name,
               'PK202609270029' AS package_no, '2026-10-03 15:00:00' AS starts_at,
               2104382388127248386 AS source_slot_id, 'ELA' AS subject_name
        UNION ALL SELECT 2104090983181918209, 'Darian Lin', 'PK202609270025', '2026-10-03 11:00:00', 2104090983173529602, 'G3 ELA&MATH'
        UNION ALL SELECT 2104090983186112513, 'Darian Lin', 'PK202609270025', '2026-10-10 11:00:00', 2104090983173529602, 'G3 ELA&MATH'
        UNION ALL SELECT 2103953603544338433, 'David Jin', 'PK202609260003', '2026-09-26 16:00:00', 2103953603510784002, 'ELA'
        UNION ALL SELECT 2104067397268295681, 'David Jin', 'PK202609260003', '2026-10-03 16:00:00', 2103953603510784002, 'ELA'
        UNION ALL SELECT 2104067397276684290, 'David Jin', 'PK202609260003', '2026-10-10 16:00:00', 2103953603510784002, 'ELA'
        UNION ALL SELECT 2103130135944810497, 'Terry Huang', 'PK202609240004', '2026-09-24 18:00:00', 2103130135936421891, 'MATH'
    ) target
    JOIN lesson_occurrence o ON o.id = target.occurrence_id
    JOIN course_package p ON p.id = o.package_id AND p.package_no = target.package_no
    JOIN student st ON st.id = p.student_id AND st.student_name = target.student_name
    JOIN recurring_schedule_slot source_slot
      ON source_slot.id = target.source_slot_id
     AND source_slot.subject_name = target.subject_name
    WHERE o.planned_start_at = target.starts_at
      AND o.schedule_slot_id = source_slot.id
      AND o.subject_name IS NULL
      AND o.planning_status = 'MERGED'
      AND o.version = 1
      AND o.is_deleted = 0
      AND EXISTS (
          SELECT 1 FROM lesson_record lr
          WHERE lr.occurrence_id = o.id AND lr.is_deleted = 0
      )
      AND (
          SELECT COUNT(*)
          FROM recurring_schedule_slot active_slot
          WHERE active_slot.rule_id = source_slot.rule_id
            AND active_slot.slot_status = 'ACTIVE'
            AND active_slot.is_deleted = 0
            AND active_slot.teacher_id = source_slot.teacher_id
            AND active_slot.weekday = source_slot.weekday
            AND active_slot.start_time = source_slot.start_time
            AND active_slot.end_time = source_slot.end_time
            AND active_slot.subject_name = source_slot.subject_name
      ) = 1;

    IF v_eligible_rows <> 7 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Backfill stopped: one or more target rows changed or no longer match the verified source.';
    END IF;

    UPDATE lesson_occurrence o
    JOIN (
        SELECT 2104382388139831298 AS occurrence_id, 'Beau Hayworth' AS student_name,
               'PK202609270029' AS package_no, '2026-10-03 15:00:00' AS starts_at,
               2104382388127248386 AS source_slot_id, 'ELA' AS subject_name
        UNION ALL SELECT 2104090983181918209, 'Darian Lin', 'PK202609270025', '2026-10-03 11:00:00', 2104090983173529602, 'G3 ELA&MATH'
        UNION ALL SELECT 2104090983186112513, 'Darian Lin', 'PK202609270025', '2026-10-10 11:00:00', 2104090983173529602, 'G3 ELA&MATH'
        UNION ALL SELECT 2103953603544338433, 'David Jin', 'PK202609260003', '2026-09-26 16:00:00', 2103953603510784002, 'ELA'
        UNION ALL SELECT 2104067397268295681, 'David Jin', 'PK202609260003', '2026-10-03 16:00:00', 2103953603510784002, 'ELA'
        UNION ALL SELECT 2104067397276684290, 'David Jin', 'PK202609260003', '2026-10-10 16:00:00', 2103953603510784002, 'ELA'
        UNION ALL SELECT 2103130135944810497, 'Terry Huang', 'PK202609240004', '2026-09-24 18:00:00', 2103130135936421891, 'MATH'
    ) target ON target.occurrence_id = o.id
    JOIN course_package p ON p.id = o.package_id AND p.package_no = target.package_no
    JOIN student st ON st.id = p.student_id AND st.student_name = target.student_name
    JOIN recurring_schedule_slot source_slot
      ON source_slot.id = target.source_slot_id
     AND source_slot.subject_name = target.subject_name
    SET o.subject_name = source_slot.subject_name,
        o.version = o.version + 1,
        o.updated_at = NOW()
    WHERE o.planned_start_at = target.starts_at
      AND o.schedule_slot_id = source_slot.id
      AND o.subject_name IS NULL
      AND o.planning_status = 'MERGED'
      AND o.version = 1
      AND o.is_deleted = 0
      AND EXISTS (
          SELECT 1 FROM lesson_record lr
          WHERE lr.occurrence_id = o.id AND lr.is_deleted = 0
      )
      AND (
          SELECT COUNT(*)
          FROM recurring_schedule_slot active_slot
          WHERE active_slot.rule_id = source_slot.rule_id
            AND active_slot.slot_status = 'ACTIVE'
            AND active_slot.is_deleted = 0
            AND active_slot.teacher_id = source_slot.teacher_id
            AND active_slot.weekday = source_slot.weekday
            AND active_slot.start_time = source_slot.start_time
            AND active_slot.end_time = source_slot.end_time
            AND active_slot.subject_name = source_slot.subject_name
      ) = 1;

    SET v_updated_rows = ROW_COUNT();
    IF v_updated_rows <> 7 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Backfill stopped: expected to update exactly seven course name snapshots.';
    END IF;

    COMMIT;
END//
DELIMITER ;

CALL backfill_confirmed_course_names_20261020();
DROP PROCEDURE backfill_confirmed_course_names_20261020;

SELECT o.id, st.student_name, p.package_no, o.planned_start_at,
       o.subject_name, o.planning_status, o.version
FROM lesson_occurrence o
JOIN course_package p ON p.id = o.package_id
JOIN student st ON st.id = p.student_id
WHERE o.id IN (2104382388139831298, 2104090983181918209, 2104090983186112513,
               2103953603544338433, 2104067397268295681, 2104067397276684290,
               2103130135944810497)
ORDER BY st.student_name, o.planned_start_at;

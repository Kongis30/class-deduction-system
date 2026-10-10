-- 10/7 formal leave 不消课，因此按原周课表将后续排课延长一周；只在余额、冲突及目标课次均核验通过时补入。
INSERT INTO lesson_occurrence
    (id, lesson_no, package_id, private_lesson_group_id, class_group_id, student_id,
     schedule_rule_id, schedule_slot_id, occurrence_type, planned_teacher_id, subject_name,
     delivery_mode, planned_classroom_id, planned_start_at, planned_end_at, planned_units,
     planning_status, conflict_checked, notes)
SELECT UUID_SHORT(), CONCAT('LS', UUID_SHORT()), source_row.package_id,
       source_row.private_lesson_group_id, NULL, source_row.student_id,
       source_row.schedule_rule_id, source_row.schedule_slot_id, 'REGULAR',
       source_row.planned_teacher_id, COALESCE(source_row.subject_name, slot.subject_name),
       source_row.delivery_mode, source_row.planned_classroom_id,
       DATE_ADD(source_row.planned_start_at, INTERVAL 7 DAY),
       DATE_ADD(source_row.planned_end_at, INTERVAL 7 DAY), source_row.planned_units,
       'SCHEDULED', 1, 'Restored after non-deducted formal leave; 2026-10-09 repair'
FROM lesson_occurrence source_row
JOIN course_package package ON package.id = source_row.package_id
JOIN recurring_schedule_rule rule ON rule.id = source_row.schedule_rule_id
JOIN recurring_schedule_slot slot ON slot.id = source_row.schedule_slot_id
WHERE source_row.id = 2108398832896811010
  AND source_row.package_id = 2103231174551326722
  AND package.student_id = 2096712568053260289
  AND package.package_status = 'ACTIVE'
  AND package.remaining_units >= source_row.planned_units + (
      SELECT COALESCE(SUM(future.planned_units), 0)
      FROM lesson_occurrence future
      WHERE future.package_id = source_row.package_id
        AND future.is_deleted = 0
        AND future.planning_status = 'SCHEDULED'
        AND future.planned_start_at > CURRENT_TIMESTAMP(3)
  )
  AND rule.rule_status = 'ACTIVE'
  AND slot.slot_status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM lesson_occurrence target
      WHERE target.package_id = source_row.package_id
        AND target.is_deleted = 0
        AND target.planned_start_at = DATE_ADD(source_row.planned_start_at, INTERVAL 7 DAY)
  )
  AND NOT EXISTS (
      SELECT 1 FROM lesson_occurrence conflict
      WHERE conflict.is_deleted = 0
        AND conflict.planning_status <> 'CANCELLED'
        AND conflict.planned_start_at < DATE_ADD(source_row.planned_end_at, INTERVAL 7 DAY)
        AND conflict.planned_end_at > DATE_ADD(source_row.planned_start_at, INTERVAL 7 DAY)
        AND (conflict.student_id = source_row.student_id
             OR conflict.planned_teacher_id = source_row.planned_teacher_id
             OR conflict.planned_classroom_id = source_row.planned_classroom_id)
  );

WITH scope AS (
 SELECT s.student_name,p.id package_id,p.remaining_units,gm.private_lesson_group_id group_id,r.id rule_id,
 COALESCE(r.allocation_period_started_at,
   CASE WHEN r.supersedes_rule_id IS NULL THEN GREATEST(r.created_at,
     COALESCE((SELECT MAX(sl.created_at) FROM recurring_schedule_slot sl
       WHERE sl.rule_id=r.id AND sl.slot_status='ACTIVE' AND sl.is_deleted=0),r.created_at))
   ELSE r.created_at END) period_start,
 ROW_NUMBER() OVER (PARTITION BY p.id ORDER BY r.created_at DESC,r.id DESC) rn
 FROM course_package p JOIN student s ON s.id=p.student_id
 LEFT JOIN private_lesson_group_member gm ON gm.package_id=p.id AND gm.member_status='ACTIVE' AND gm.is_deleted=0
 JOIN recurring_schedule_rule r ON (r.package_id=p.id OR r.private_lesson_group_id=gm.private_lesson_group_id)
 WHERE p.teaching_mode='ONE_ON_ONE' AND p.package_status='ACTIVE' AND p.is_deleted=0
   AND r.rule_status='ACTIVE' AND r.is_deleted=0
), a AS (SELECT * FROM scope WHERE rn=1), alloc AS (
 SELECT a.*,sl.teacher_id,SUM(sl.allocated_units) alloc_units
 FROM a JOIN recurring_schedule_slot sl ON sl.rule_id=a.rule_id AND sl.slot_status='ACTIVE' AND sl.is_deleted=0
 GROUP BY a.student_name,a.package_id,a.remaining_units,a.group_id,a.rule_id,a.period_start,sl.teacher_id
), teacher_totals AS (
 SELECT alloc.*,
 (SELECT COALESCE(SUM(lr.deducted_units),0) FROM lesson_record lr JOIN lesson_occurrence o ON o.id=lr.occurrence_id
  WHERE lr.package_id=alloc.package_id AND lr.confirm_stage='CONFIRMED' AND lr.should_deduct=1 AND lr.is_deleted=0
   AND o.schedule_rule_id=alloc.rule_id AND o.planned_teacher_id=alloc.teacher_id AND o.is_deleted=0
   AND o.planned_start_at>=alloc.period_start AND lr.confirmed_at>=alloc.period_start) deducted_units,
 (SELECT COALESCE(SUM(o.planned_units),0) FROM lesson_occurrence o
  WHERE o.schedule_rule_id=alloc.rule_id AND o.planned_teacher_id=alloc.teacher_id AND o.is_deleted=0
   AND o.planning_status NOT IN ('CANCELLED','MERGED') AND o.planned_start_at>=alloc.period_start
   AND ((alloc.group_id IS NULL AND o.package_id=alloc.package_id)
     OR (alloc.group_id IS NOT NULL AND o.private_lesson_group_id=alloc.group_id))) actionable_units
 FROM alloc)
SELECT student_name,package_id,teacher_id,rule_id,
 alloc_units/2 allocated_h,deducted_units/2 deducted_h,
 (alloc_units-deducted_units)/2 teacher_remaining_h,
 actionable_units/2 current_lesson_h,remaining_units/2 package_remaining_h
FROM teacher_totals
WHERE alloc_units-deducted_units<>actionable_units OR alloc_units-deducted_units<0
  OR alloc_units-deducted_units>remaining_units
ORDER BY student_name,package_id,teacher_id;

SELECT COUNT(*) all_active_one_on_one_packages
FROM course_package p
WHERE p.teaching_mode='ONE_ON_ONE' AND p.package_status='ACTIVE' AND p.is_deleted=0;

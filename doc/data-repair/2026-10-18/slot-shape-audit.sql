WITH scope AS (
 SELECT p.id package_id,gm.private_lesson_group_id group_id,r.id rule_id,
 COALESCE(r.allocation_period_started_at,
   CASE WHEN r.supersedes_rule_id IS NULL THEN GREATEST(r.created_at,
     COALESCE((SELECT MAX(sl.created_at) FROM recurring_schedule_slot sl
       WHERE sl.rule_id=r.id AND sl.slot_status='ACTIVE' AND sl.is_deleted=0),r.created_at))
   ELSE r.created_at END) period_start,
 ROW_NUMBER() OVER (PARTITION BY p.id ORDER BY r.created_at DESC,r.id DESC) rn
 FROM course_package p
 LEFT JOIN private_lesson_group_member gm ON gm.package_id=p.id AND gm.member_status='ACTIVE' AND gm.is_deleted=0
 JOIN recurring_schedule_rule r ON (r.package_id=p.id OR r.private_lesson_group_id=gm.private_lesson_group_id)
 WHERE p.teaching_mode='ONE_ON_ONE' AND p.package_status='ACTIVE' AND p.is_deleted=0
   AND r.rule_status='ACTIVE' AND r.is_deleted=0
), a AS (SELECT * FROM scope WHERE rn=1)
SELECT DISTINCT a.package_id,o.id occurrence_id,o.occurrence_type,o.planning_status,
 o.planned_start_at,o.planned_teacher_id,sl.teacher_id slot_teacher_id,
 o.schedule_slot_id,sl.weekday slot_weekday,sl.start_time slot_start_time,
 sl.is_deleted slot_deleted
FROM a JOIN lesson_occurrence o ON o.schedule_rule_id=a.rule_id AND o.is_deleted=0
  AND o.planning_status NOT IN ('CANCELLED','MERGED') AND o.planned_start_at>=a.period_start
  AND ((a.group_id IS NULL AND o.package_id=a.package_id)
    OR (a.group_id IS NOT NULL AND o.private_lesson_group_id=a.group_id))
LEFT JOIN recurring_schedule_slot sl ON sl.id=o.schedule_slot_id
WHERE o.occurrence_type IN ('REGULAR','MAKEUP')
 AND (sl.id IS NULL OR sl.is_deleted<>0 OR sl.slot_status<>'ACTIVE'
  OR o.planned_teacher_id<>sl.teacher_id
  OR WEEKDAY(o.planned_start_at)+1<>sl.weekday
  OR TIME(o.planned_start_at)<>sl.start_time)
ORDER BY a.package_id,o.planned_start_at;

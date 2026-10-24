-- Brian Zheng / PK202609270007
-- 只读核验脚本。此前把 Mr. Roland 分配额从 12 调到 8 个半小时单位的修正已按业务要求撤销。
-- 当前分配额恢复为 12；不改分配额、不恢复 11/13、不对 10/19 旧课作消课或取消判断。

SELECT id, package_no, package_status, remaining_units
FROM course_package
WHERE id = 2104063336141836290;

SELECT s.id, s.teacher_id, t.teacher_name, s.subject_name, s.allocated_units,
       s.slot_status, s.is_deleted,
       COALESCE(SUM(CASE WHEN o.is_deleted = 0 AND o.planning_status <> 'CANCELLED'
                         THEN o.planned_units ELSE 0 END), 0) AS scheduled_units
FROM recurring_schedule_slot s
LEFT JOIN teacher t ON t.id = s.teacher_id
LEFT JOIN lesson_occurrence o ON o.schedule_rule_id = s.rule_id AND o.schedule_slot_id = s.id
WHERE s.rule_id = 2113797489234145282
  AND s.id IN (2113797806428385281, 2113797806428385282)
GROUP BY s.id, s.teacher_id, t.teacher_name, s.subject_name, s.allocated_units,
         s.slot_status, s.is_deleted;

SELECT o.id, o.schedule_rule_id, o.schedule_slot_id, o.planned_teacher_id, t.teacher_name,
       o.planned_start_at, o.planned_units, o.planning_status, o.is_deleted,
       (SELECT COUNT(*) FROM lesson_record lr
        WHERE lr.occurrence_id = o.id AND lr.is_deleted = 0) AS active_lesson_records
FROM lesson_occurrence o
LEFT JOIN teacher t ON t.id = o.planned_teacher_id
WHERE o.id IN (2107571685714046977, 2113797489322225666);

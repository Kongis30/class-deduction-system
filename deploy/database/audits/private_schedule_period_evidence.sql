-- 只读列举当前有效规则的周期证据；NULL 不代表没有周期，也不自动证明创建时间就是周期起点。
-- 结果含学生信息，应保存到私有位置；不得将其当作自动回填清单。
START TRANSACTION READ ONLY;
SELECT r.id AS rule_id, r.package_id, st.student_name, p.package_no,
       r.rule_status, r.created_at AS rule_created_at, r.updated_at AS rule_updated_at,
       r.allocation_period_started_at, r.supersedes_rule_id, r.superseded_by_rule_id,
       prev.created_at AS previous_created_at, prev.rule_status AS previous_status,
       prev.superseded_by_rule_id AS previous_successor_id,
       (SELECT COUNT(*) FROM recurring_schedule_starting_balance b
        WHERE b.schedule_rule_id = r.id AND b.is_deleted = 0) AS starting_balance_count,
       (SELECT COUNT(*) FROM recurring_schedule_slot s
        WHERE s.rule_id = r.id AND s.is_deleted = 0 AND s.slot_status = 'ACTIVE') AS active_slot_count,
       (SELECT MIN(s.created_at) FROM recurring_schedule_slot s
        WHERE s.rule_id = r.id AND s.is_deleted = 0 AND s.slot_status = 'ACTIVE') AS first_active_slot_created,
       (SELECT COUNT(*) FROM recurring_schedule_slot s
        WHERE s.rule_id = r.id AND s.is_deleted = 1) AS deleted_slot_count,
       (SELECT MAX(s.deleted_at) FROM recurring_schedule_slot s
        WHERE s.rule_id = r.id AND s.is_deleted = 1) AS last_slot_deleted_at,
       (SELECT COUNT(*) FROM lesson_occurrence o
        WHERE o.schedule_rule_id = r.id AND o.is_deleted = 0) AS active_occurrence_count,
       (SELECT MIN(o.planned_start_at) FROM lesson_occurrence o
        WHERE o.schedule_rule_id = r.id AND o.is_deleted = 0) AS first_lesson_at,
       (SELECT COUNT(*) FROM lesson_occurrence o
        JOIN lesson_record lr ON lr.occurrence_id = o.id AND lr.is_deleted = 0
        WHERE o.schedule_rule_id = r.id AND o.is_deleted = 0 AND lr.confirm_stage = 'CONFIRMED') AS confirmed_record_count,
       (SELECT COUNT(*) FROM lesson_occurrence o
        JOIN lesson_adjustment_log l ON l.occurrence_id = o.id
        WHERE o.schedule_rule_id = r.id AND l.adjustment_type = 'SCHEDULE_DETAILS_UPDATE') AS detail_change_log_count
FROM recurring_schedule_rule r
JOIN course_package p ON p.id = r.package_id
LEFT JOIN student st ON st.id = p.student_id
LEFT JOIN recurring_schedule_rule prev ON prev.id = r.supersedes_rule_id
WHERE r.is_deleted = 0 AND r.rule_status = 'ACTIVE'
ORDER BY r.allocation_period_started_at IS NOT NULL, st.student_name, r.id;
COMMIT;

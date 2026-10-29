-- 本地隔离快照只读比对；执行前确认三个库均由对应日期备份恢复。
-- 固定周时段相同不等于期间没有变化再改回，差异也不提供精确操作时间。
START TRANSACTION READ ONLY;
WITH current_slots AS (
    SELECT rule_id, GROUP_CONCAT(CONCAT_WS('/', weekday, start_time, end_time, planned_units)
        ORDER BY weekday, start_time, end_time, planned_units SEPARATOR '|') AS times
    FROM jin_period_audit_20261029_01.recurring_schedule_slot
    WHERE is_deleted = 0 AND slot_status = 'ACTIVE' GROUP BY rule_id
), oct19_slots AS (
    SELECT rule_id, GROUP_CONCAT(CONCAT_WS('/', weekday, start_time, end_time, planned_units)
        ORDER BY weekday, start_time, end_time, planned_units SEPARATOR '|') AS times
    FROM jin_period_history_20261019.recurring_schedule_slot
    WHERE is_deleted = 0 AND slot_status = 'ACTIVE' GROUP BY rule_id
), oct08_slots AS (
    SELECT rule_id, GROUP_CONCAT(CONCAT_WS('/', weekday, start_time, end_time, planned_units)
        ORDER BY weekday, start_time, end_time, planned_units SEPARATOR '|') AS times
    FROM jin_period_history_20261008.recurring_schedule_slot
    WHERE is_deleted = 0 AND slot_status = 'ACTIVE' GROUP BY rule_id
)
SELECT r.id AS rule_id, st.student_name, p.package_no, r.created_at, r.start_date,
       r.supersedes_rule_id, r.allocation_period_started_at,
       h19.id AS oct19_rule_id, h19.rule_status AS oct19_status,
       h19.start_date AS oct19_start_date, h19.allocation_period_started_at AS oct19_period_start,
       h08.id AS oct08_rule_id, h08.rule_status AS oct08_status, h08.start_date AS oct08_start_date,
       cs.times AS current_times, s19.times AS oct19_times, s08.times AS oct08_times
FROM jin_period_audit_20261029_01.recurring_schedule_rule r
JOIN jin_period_audit_20261029_01.course_package p ON p.id = r.package_id
LEFT JOIN jin_period_audit_20261029_01.student st ON st.id = p.student_id
LEFT JOIN jin_period_history_20261019.recurring_schedule_rule h19 ON h19.id = r.id
LEFT JOIN jin_period_history_20261008.recurring_schedule_rule h08 ON h08.id = r.id
LEFT JOIN current_slots cs ON cs.rule_id = r.id
LEFT JOIN oct19_slots s19 ON s19.rule_id = r.id
LEFT JOIN oct08_slots s08 ON s08.rule_id = r.id
WHERE r.is_deleted = 0 AND r.rule_status = 'ACTIVE' AND r.allocation_period_started_at IS NULL
ORDER BY st.student_name, r.id;
COMMIT;

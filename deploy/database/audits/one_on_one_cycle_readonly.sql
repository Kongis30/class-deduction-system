-- 一对一当前周期课时只读审计。仅覆盖非共享课包；共享组需要按成员分别核对。
-- 单位为 0.5 小时。此文件不修改课包、排课、课次或消课记录。
-- 需先部署 allocation_period_started_at 列；已固定的旧周期优先读取该列。
START TRANSACTION READ ONLY;
WITH
active_packages AS (
    SELECT p.*
    FROM course_package p
    WHERE p.teaching_mode = 'ONE_ON_ONE'
      AND p.package_status = 'ACTIVE' AND p.is_deleted = 0
      AND NOT EXISTS (
          SELECT 1 FROM private_lesson_group_member gm
          WHERE gm.package_id = p.id AND gm.member_status = 'ACTIVE' AND gm.is_deleted = 0
      )
),
ranked_rules AS (
    SELECT r.*, ROW_NUMBER() OVER (
        PARTITION BY r.package_id ORDER BY r.created_at DESC, r.id DESC
    ) AS position_in_package
    FROM recurring_schedule_rule r
    JOIN active_packages p ON p.id = r.package_id
    WHERE r.rule_status = 'ACTIVE' AND r.is_deleted = 0
),
current_rules AS (
    SELECT * FROM ranked_rules WHERE position_in_package = 1
),
active_slots AS (
    SELECT s.*, r.package_id
    FROM recurring_schedule_slot s
    JOIN current_rules r ON r.id = s.rule_id
    WHERE s.slot_status = 'ACTIVE' AND s.is_deleted = 0
),
periods AS (
    SELECT r.package_id, r.id AS rule_id, r.supersedes_rule_id,
           CASE WHEN r.allocation_period_started_at IS NOT NULL
                THEN r.allocation_period_started_at
                WHEN r.supersedes_rule_id IS NULL
                THEN GREATEST(r.created_at, COALESCE(MAX(s.created_at), r.created_at))
                ELSE r.created_at END AS period_start,
           COUNT(s.id) AS slot_count,
           COALESCE(SUM(GREATEST(COALESCE(s.allocated_units, 0), 0)), 0) AS slot_allocated_units
    FROM current_rules r
    LEFT JOIN active_slots s ON s.rule_id = r.id
    GROUP BY r.package_id, r.id, r.supersedes_rule_id, r.created_at
),
latest_records AS (
    SELECT ranked.* FROM (
        SELECT lr.*, ROW_NUMBER() OVER (
            PARTITION BY lr.package_id, lr.occurrence_id
            ORDER BY lr.updated_at DESC, lr.id DESC
        ) AS record_rank
        FROM lesson_record lr
        JOIN active_packages p ON p.id = lr.package_id
        WHERE lr.is_deleted = 0
    ) ranked WHERE ranked.record_rank = 1
),
occurrence_facts AS (
    SELECT o.package_id, o.id AS occurrence_id, o.planned_units,
           o.planned_start_at, o.planned_teacher_id, o.occurrence_type,
           o.planning_status, o.schedule_slot_id, o.schedule_rule_id,
           lr.confirm_stage, lr.should_deduct, lr.deducted_units,
           CASE WHEN o.planned_start_at >= period.period_start
                    AND (lr.confirmed_at IS NULL OR lr.confirmed_at >= period.period_start)
                    AND (o.schedule_rule_id = period.rule_id
                         OR (o.schedule_rule_id IS NULL AND o.occurrence_type = 'ADHOC'
                             AND o.created_at >= period.period_start)
                         OR period.supersedes_rule_id IS NOT NULL)
                THEN 1 ELSE 0 END AS in_current_period,
           CASE WHEN active_slot.id IS NULL AND NOT EXISTS (
                    SELECT 1
                    FROM recurring_schedule_slot historical_slot
                    JOIN active_slots equivalent_slot ON equivalent_slot.rule_id = period.rule_id
                    WHERE historical_slot.id = o.schedule_slot_id
                      AND historical_slot.rule_id = period.rule_id
                      AND historical_slot.teacher_id <=> equivalent_slot.teacher_id
                      AND historical_slot.weekday = equivalent_slot.weekday
                      AND historical_slot.start_time = equivalent_slot.start_time
                      AND historical_slot.end_time = equivalent_slot.end_time
                      AND historical_slot.planned_units = equivalent_slot.planned_units
                      AND historical_slot.allocated_units <=> equivalent_slot.allocated_units - COALESCE((
                          SELECT SUM(makeup.leave_carryover_units)
                          FROM lesson_occurrence makeup
                          JOIN recurring_schedule_slot makeup_slot ON makeup_slot.id = makeup.schedule_slot_id
                          WHERE makeup.package_id = o.package_id
                            AND makeup.schedule_rule_id = period.rule_id
                            AND makeup.occurrence_type = 'MAKEUP'
                            AND makeup.leave_carryover_units > 0
                            AND makeup_slot.teacher_id <=> equivalent_slot.teacher_id
                            AND makeup_slot.weekday = equivalent_slot.weekday
                            AND makeup_slot.start_time = equivalent_slot.start_time
                            AND makeup_slot.end_time = equivalent_slot.end_time
                            AND makeup_slot.planned_units = equivalent_slot.planned_units
                      ), 0)
                ) THEN 1 ELSE 0 END AS outside_active_slot
    FROM lesson_occurrence o
    JOIN periods period ON period.package_id = o.package_id
    LEFT JOIN latest_records lr ON lr.package_id = o.package_id AND lr.occurrence_id = o.id
    LEFT JOIN active_slots active_slot ON active_slot.id = o.schedule_slot_id
    WHERE o.is_deleted = 0
),
occurrence_totals AS (
    SELECT package_id,
           COALESCE(SUM(CASE WHEN in_current_period = 1
                                  AND planned_teacher_id IS NOT NULL AND outside_active_slot = 1
                                  AND occurrence_type <> 'MAKEUP' AND planning_status <> 'CANCELLED'
                             THEN GREATEST(planned_units, 0) ELSE 0 END), 0) AS exception_allocated_units,
           COALESCE(SUM(CASE WHEN in_current_period = 1 AND confirm_stage = 'CONFIRMED'
                                  AND should_deduct = 1 AND planned_teacher_id IS NOT NULL
                             THEN GREATEST(deducted_units, 0) ELSE 0 END), 0) AS current_deducted_units,
           COALESCE(SUM(CASE WHEN in_current_period = 1 AND planned_start_at >= NOW()
                                  AND planning_status NOT IN ('CANCELLED', 'MERGED')
                                  AND (confirm_stage IS NULL OR confirm_stage NOT IN ('CONFIRMED', 'PENDING_ADMIN'))
                             THEN GREATEST(planned_units, 0) ELSE 0 END), 0) AS current_future_open_units,
           COALESCE(SUM(CASE WHEN in_current_period = 0 AND planned_start_at < NOW()
                                  AND planning_status NOT IN ('CANCELLED', 'MERGED')
                                  AND (confirm_stage IS NULL OR confirm_stage NOT IN ('CONFIRMED', 'PENDING_ADMIN'))
                             THEN GREATEST(planned_units, 0) ELSE 0 END), 0) AS older_unresolved_units
    FROM occurrence_facts GROUP BY package_id
)
SELECT p.id AS package_id, p.student_id, st.student_name, p.package_no,
       p.total_units, p.opening_used_units, p.consumed_units, p.remaining_units,
       period.rule_id, period.supersedes_rule_id, period.period_start, period.slot_count,
       period.slot_allocated_units,
       COALESCE(ot.exception_allocated_units, 0) AS exception_allocated_units,
       period.slot_allocated_units + COALESCE(ot.exception_allocated_units, 0) AS current_allocated_units,
       COALESCE(ot.current_deducted_units, 0) AS current_deducted_units,
       period.slot_allocated_units + COALESCE(ot.exception_allocated_units, 0)
           - COALESCE(ot.current_deducted_units, 0) AS current_remaining_with_teachers_units,
       COALESCE(ot.current_future_open_units, 0) AS current_future_open_units,
       COALESCE(ot.older_unresolved_units, 0) AS older_unresolved_units,
       CASE
           WHEN p.total_units - p.consumed_units <> p.remaining_units THEN 'PACKAGE_LEDGER_MISMATCH'
           WHEN period.slot_allocated_units + COALESCE(ot.exception_allocated_units, 0)
                    - COALESCE(ot.current_deducted_units, 0) < 0 THEN 'NEGATIVE_TEACHER_BALANCE'
           WHEN period.slot_allocated_units + COALESCE(ot.exception_allocated_units, 0)
                    - COALESCE(ot.current_deducted_units, 0)
                    + COALESCE(ot.older_unresolved_units, 0) > p.remaining_units
               THEN 'OVER_ALLOCATED_REVIEW_REQUIRED'
           ELSE 'NO_OVER_ALLOCATION_DETECTED'
       END AS audit_result
FROM active_packages p
JOIN student st ON st.id = p.student_id
JOIN periods period ON period.package_id = p.id
LEFT JOIN occurrence_totals ot ON ot.package_id = p.id
ORDER BY audit_result DESC, st.student_name, p.id;
COMMIT;

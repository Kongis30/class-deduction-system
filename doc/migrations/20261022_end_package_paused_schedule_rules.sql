-- 旧版课包停课将排课规则标记为 PAUSED，未来课次虽已取消，旧规则仍占用课包分配额度。
-- 只结束已停课课包的旧规则，或课次明确记录为课包停课取消的规则；保留独立手动暂停的规则。
-- 执行前核对候选规则；存在未取消的未来课次时，不自动修改该规则。
SELECT r.id AS rule_id, r.package_id, p.package_status
FROM recurring_schedule_rule r
JOIN course_package p ON p.id = r.package_id
WHERE r.rule_status = 'PAUSED'
  AND (p.package_status = 'PAUSED' OR EXISTS (
      SELECT 1 FROM lesson_occurrence o
      WHERE o.schedule_rule_id = r.id
        AND o.is_deleted = 0
        AND o.notes = 'Cancelled when package was paused.'
  ))
  AND NOT EXISTS (
      SELECT 1 FROM lesson_occurrence o
      WHERE o.schedule_rule_id = r.id
        AND o.is_deleted = 0
        AND o.planned_start_at >= NOW()
        AND o.planning_status <> 'CANCELLED'
  )
ORDER BY r.id;

START TRANSACTION;
UPDATE recurring_schedule_rule r
JOIN course_package p ON p.id = r.package_id
SET r.rule_status = 'ENDED'
WHERE r.rule_status = 'PAUSED'
  AND (p.package_status = 'PAUSED' OR EXISTS (
      SELECT 1 FROM lesson_occurrence o
      WHERE o.schedule_rule_id = r.id
        AND o.is_deleted = 0
        AND o.notes = 'Cancelled when package was paused.'
  ))
  AND NOT EXISTS (
      SELECT 1 FROM lesson_occurrence o
      WHERE o.schedule_rule_id = r.id
        AND o.is_deleted = 0
        AND o.planned_start_at >= NOW()
        AND o.planning_status <> 'CANCELLED'
  );
SELECT ROW_COUNT() AS ended_rule_count;
COMMIT;
